package com.example.game

import com.example.engine.MathUtil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class BulletTracer(
  val startX: Float,
  val startY: Float,
  val startZ: Float,
  val endX: Float,
  val endY: Float,
  val endZ: Float,
  var lifeSec: Float,
)

data class LootDrop(
  val id: Int,
  val x: Float,
  val y: Float,
  val z: Float,
  val isCash: Boolean,
  val amount: Int,
  var lifeSec: Float = 24f,
)

data class AutoAimTarget(
  val x: Float,
  val y: Float,
  val z: Float,
  val kind: Kind,
  val idString: String,
  val idInt: Int,
) {
  enum class Kind { NPC, POLICE, VEHICLE, REMOTE_PLAYER }
}

class WeaponSystem {
  var unlockedWeapons: MutableSet<WeaponCategory> =
    mutableSetOf(WeaponCategory.MELEE, WeaponCategory.PISTOL)

  var activeWeapon: WeaponCategory = WeaponCategory.PISTOL
    private set

  private val clipAmmo = IntArray(WeaponCategory.entries.size) { idx ->
    WeaponCategory.byIndex(idx).magSize
  }
  private val reserveAmmo = IntArray(WeaponCategory.entries.size) { idx ->
    when (WeaponCategory.byIndex(idx)) {
      WeaponCategory.MELEE -> 1
      WeaponCategory.PISTOL -> 48
      WeaponCategory.SMG -> 90
      WeaponCategory.SHOTGUN -> 24
      WeaponCategory.ASSAULT_RIFLE -> 90
    }
  }

  private var lastShotMs = 0L
  var isReloading = false
    private set
  private var reloadEndMs = 0L

  var muzzleFlashSec = 0f
  var shotSequence: Long = 0L
  val tracers = ArrayList<BulletTracer>()
  val lootDrops = ArrayList<LootDrop>()
  private var nextLootId = 1

  fun currentClip(): Int = clipAmmo[activeWeapon.index]
  fun currentReserve(): Int = reserveAmmo[activeWeapon.index]

  fun syncUnlocked(unlocked: Set<WeaponCategory>) {
    unlockedWeapons.clear()
    unlockedWeapons.add(WeaponCategory.MELEE)
    unlockedWeapons.addAll(unlocked)
    if (activeWeapon !in unlockedWeapons) {
      activeWeapon = WeaponCategory.PISTOL.takeIf { it in unlockedWeapons } ?: WeaponCategory.MELEE
    }
  }

  fun selectWeapon(category: WeaponCategory) {
    if (category !in unlockedWeapons) return
    if (activeWeapon != category) {
      activeWeapon = category
      isReloading = false
    }
  }

  fun cycleNextWeapon(): WeaponCategory {
    val ordered = WeaponCategory.entries.filter { it in unlockedWeapons }
    val curIdx = ordered.indexOf(activeWeapon)
    val next = ordered[(curIdx + 1) % ordered.size]
    selectWeapon(next)
    return next
  }

  fun addAmmo(category: WeaponCategory, amount: Int) {
    val idx = category.index
    reserveAmmo[idx] = (reserveAmmo[idx] + amount).coerceAtMost(category.maxReserve)
  }

  fun refillAllAmmo() {
    for (w in unlockedWeapons) {
      clipAmmo[w.index] = w.magSize
      reserveAmmo[w.index] = w.maxReserve
    }
    isReloading = false
  }

  fun startReload(nowMs: Long = System.currentTimeMillis()): Boolean {
    if (activeWeapon == WeaponCategory.MELEE || isReloading) return false
    val idx = activeWeapon.index
    if (clipAmmo[idx] >= activeWeapon.magSize || reserveAmmo[idx] <= 0) return false
    isReloading = true
    reloadEndMs = nowMs + activeWeapon.reloadMs
    return true
  }

  fun update(dt: Float, nowMs: Long) {
    if (muzzleFlashSec > 0f) {
      muzzleFlashSec = (muzzleFlashSec - dt).coerceAtLeast(0f)
    }
    val it = tracers.iterator()
    while (it.hasNext()) {
      val tr = it.next()
      tr.lifeSec -= dt
      if (tr.lifeSec <= 0f) it.remove()
    }
    val lit = lootDrops.iterator()
    while (lit.hasNext()) {
      val l = lit.next()
      l.lifeSec -= dt
      if (l.lifeSec <= 0f) lit.remove()
    }
    if (isReloading && nowMs >= reloadEndMs) {
      val idx = activeWeapon.index
      val needed = activeWeapon.magSize - clipAmmo[idx]
      val load = needed.coerceAtMost(reserveAmmo[idx])
      clipAmmo[idx] += load
      reserveAmmo[idx] -= load
      isReloading = false
    }
  }

  fun spawnLoot(x: Float, y: Float, z: Float, isCash: Boolean, amount: Int) {
    lootDrops.add(LootDrop(nextLootId++, x, y, z, isCash, amount))
  }

  /**
   * Soft Auto-Aim / Aim-Assist for mobile: finds the closest visible target inside a 32° forward cone.
   */
  fun findAutoAimTarget(
    originX: Float,
    originY: Float,
    originZ: Float,
    aimYawDeg: Float,
    maxRange: Float,
    candidates: List<AutoAimTarget>,
  ): AutoAimTarget? {
    var best: AutoAimTarget? = null
    var bestScore = Float.MAX_VALUE
    val rangeSq = maxRange * maxRange

    for (c in candidates) {
      val dx = c.x - originX
      val dz = c.z - originZ
      val distSq = dx * dx + dz * dz
      if (distSq > rangeSq || distSq < 0.25f) continue
      val targetYaw = MathUtil.yawFromDirDeg(dx, dz)
      val angDiff = MathUtil.angleDiffDeg(aimYawDeg, targetYaw)
      if (angDiff <= 32f) {
        val score = distSq * 0.5f + angDiff * angDiff * 1.4f
        if (score < bestScore) {
          bestScore = score
          best = c
        }
      }
    }
    return best
  }

  /**
   * Fires active weapon if cooldown and ammo allow. Returns list of ray directions (dx, dy, dz) for pellets.
   */
  fun tryFire(
    nowMs: Long,
    baseYawDeg: Float,
    autoAimTarget: AutoAimTarget?,
    originX: Float,
    originY: Float,
    originZ: Float,
  ): List<Triple<Float, Float, Float>>? {
    if (isReloading) return null
    if (nowMs - lastShotMs < activeWeapon.fireCooldownMs) return null

    val idx = activeWeapon.index
    if (activeWeapon != WeaponCategory.MELEE) {
      if (clipAmmo[idx] <= 0) {
        startReload(nowMs)
        return null
      }
      clipAmmo[idx]--
    }

    lastShotMs = nowMs
    shotSequence++
    if (activeWeapon != WeaponCategory.MELEE) {
      muzzleFlashSec = 0.085f
    }

    val targetYaw = if (autoAimTarget != null) {
      MathUtil.yawFromDirDeg(autoAimTarget.x - originX, autoAimTarget.z - originZ)
    } else {
      baseYawDeg
    }
    val targetDy = if (autoAimTarget != null) {
      val d = MathUtil.dist2D(originX, originZ, autoAimTarget.x, autoAimTarget.z).coerceAtLeast(1f)
      ((autoAimTarget.y - originY) / d).coerceIn(-0.45f, 0.45f)
    } else {
      0f
    }

    val rays = ArrayList<Triple<Float, Float, Float>>(activeWeapon.pellets)
    for (p in 0 until activeWeapon.pellets) {
      val jitter = (Random.nextFloat() * 2f - 1f) * activeWeapon.spreadDeg
      val yawRad = (targetYaw + jitter) * MathUtil.DEG_TO_RAD
      val dx = sin(yawRad)
      val dz = cos(yawRad)
      rays.add(Triple(dx, targetDy, dz))
    }

    if (clipAmmo[idx] == 0 && activeWeapon != WeaponCategory.MELEE && reserveAmmo[idx] > 0) {
      startReload(nowMs)
    }
    return rays
  }
}
