package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.WeaponCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural zero-asset sound synthesizer using short PCM AudioTrack bursts.
 * Generates weapon shots, car horn, explosion, pickup chimes, and police siren pulses.
 */
class SoundEngine {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Volatile
  var volume: Float = 0.75f

  private var lastSirenMs = 0L
  private var lastEngineMs = 0L

  private val meleeClip by lazy { synthNoiseBurst(120, 260f, decay = 9f, toneMix = 0.4f) }
  private val pistolClip by lazy { synthNoiseBurst(110, 420f, decay = 18f, toneMix = 0.25f) }
  private val smgClip by lazy { synthNoiseBurst(70, 540f, decay = 24f, toneMix = 0.3f) }
  private val shotgunClip by lazy { synthNoiseBurst(230, 140f, decay = 9f, toneMix = 0.2f) }
  private val rifleClip by lazy { synthNoiseBurst(130, 340f, decay = 15f, toneMix = 0.3f) }
  private val hornClip by lazy { synthDualTone(260, 392f, 494f, 0.7f) }
  private val explosionClip by lazy { synthNoiseBurst(420, 75f, decay = 5f, toneMix = 0.18f) }
  private val pickupClip by lazy { synthDualTone(150, 880f, 1318f, 0.5f) }
  private val sirenHiClip by lazy { synthDualTone(220, 760f, 770f, 0.35f) }
  private val sirenLoClip by lazy { synthDualTone(220, 560f, 570f, 0.35f) }
  private var sirenPhase = false

  fun playShot(weapon: WeaponCategory) {
    if (volume <= 0.02f) return
    val pcm = when (weapon) {
      WeaponCategory.MELEE -> meleeClip
      WeaponCategory.PISTOL -> pistolClip
      WeaponCategory.SMG -> smgClip
      WeaponCategory.SHOTGUN -> shotgunClip
      WeaponCategory.ASSAULT_RIFLE -> rifleClip
    }
    playPcm(pcm, volume * 0.75f)
  }

  fun playHorn() {
    if (volume <= 0.02f) return
    playPcm(hornClip, volume * 0.65f)
  }

  fun playExplosion() {
    if (volume <= 0.02f) return
    playPcm(explosionClip, volume * 0.9f)
  }

  fun playPickup() {
    if (volume <= 0.02f) return
    playPcm(pickupClip, volume * 0.55f)
  }

  fun tickSiren(active: Boolean, nowMs: Long) {
    if (!active || volume <= 0.02f) return
    if (nowMs - lastSirenMs > 380L) {
      lastSirenMs = nowMs
      sirenPhase = !sirenPhase
      playPcm(if (sirenPhase) sirenHiClip else sirenLoClip, volume * 0.30f)
    }
  }

  fun tickEngine(inVehicle: Boolean, speedRatio: Float, nowMs: Long) {
    if (!inVehicle || volume <= 0.02f) return
    val interval = (170L - (speedRatio * 75f).toLong()).coerceIn(85L, 170L)
    if (nowMs - lastEngineMs > interval) {
      lastEngineMs = nowMs
      val baseFreq = 58f + speedRatio * 95f
      val pulse = synthDualTone(110, baseFreq, baseFreq * 1.5f, 0.16f)
      playPcm(pulse, volume * 0.25f)
    }
  }

  private fun playPcm(samples: ShortArray, gain: Float) {
    val g = gain.coerceIn(0f, 1f)
    if (g <= 0.01f) return
    scope.launch {
      var track: AudioTrack? = null
      try {
        val byteLen = samples.size * 2
        track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setSampleRate(SAMPLE_RATE)
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(byteLen)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
        track.write(samples, 0, samples.size)
        track.setVolume(g)
        track.play()
        kotlinx.coroutines.delay((samples.size * 1000L) / SAMPLE_RATE + 40L)
      } catch (_: Throwable) {
        // Ignore gracefully on environments without audio sink
      } finally {
        try {
          track?.stop()
          track?.release()
        } catch (_: Throwable) {
        }
      }
    }
  }

  private fun synthNoiseBurst(
    durationMs: Int,
    toneHz: Float,
    decay: Float,
    toneMix: Float,
  ): ShortArray {
    val count = (SAMPLE_RATE * durationMs) / 1000
    val out = ShortArray(count)
    val rng = Random(durationMs * 31 + toneHz.toInt())
    for (i in 0 until count) {
      val t = i.toFloat() / count.toFloat()
      val env = exp(-decay * t)
      val noise = rng.nextFloat() * 2f - 1f
      val tone = sin(2.0 * PI * toneHz * (i.toDouble() / SAMPLE_RATE) * (1.0 - 0.35 * t)).toFloat()
      val sample = ((noise * (1f - toneMix) + tone * toneMix) * env).coerceIn(-1f, 1f)
      out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
    }
    return out
  }

  private fun synthDualTone(
    durationMs: Int,
    freqA: Float,
    freqB: Float,
    amp: Float,
  ): ShortArray {
    val count = (SAMPLE_RATE * durationMs) / 1000
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i.toFloat() / count.toFloat()
      val attack = (t / 0.08f).coerceAtMost(1f)
      val release = ((1f - t) / 0.15f).coerceAtMost(1f)
      val env = attack * release
      val sA = sin(2.0 * PI * freqA * (i.toDouble() / SAMPLE_RATE)).toFloat()
      val sB = sin(2.0 * PI * freqB * (i.toDouble() / SAMPLE_RATE)).toFloat()
      val sample = ((sA + sB) * 0.5f * env * amp).coerceIn(-1f, 1f)
      out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
    }
    return out
  }

  companion object {
    private const val SAMPLE_RATE = 22050
  }
}
