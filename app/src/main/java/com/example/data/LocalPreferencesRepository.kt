package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.GraphicsQuality
import com.example.game.PlayerProfileState
import com.example.game.WeaponCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.viceRoamDataStore: DataStore<Preferences> by preferencesDataStore(name = "vice_roam_prefs")

class LocalPreferencesRepository(context: Context) {
  private val dataStore = context.applicationContext.viceRoamDataStore

  private object Keys {
    val NICKNAME = stringPreferencesKey("nickname")
    val SKIN_INDEX = intPreferencesKey("skin_index")
    val OUTFIT_INDEX = intPreferencesKey("outfit_index")
    val CASH = intPreferencesKey("cash")
    val WEAPONS = stringPreferencesKey("unlocked_weapons")
    val GRAPHICS = stringPreferencesKey("graphics_quality")
    val SENSITIVITY = floatPreferencesKey("camera_sensitivity")
    val VOLUME = floatPreferencesKey("audio_volume")
  }

  val profileFlow: Flow<PlayerProfileState> = dataStore.data.map { prefs ->
    val weaponsRaw = prefs[Keys.WEAPONS] ?: "MELEE,PISTOL"
    val weapons = weaponsRaw.split(",")
      .mapNotNull { WeaponCategory.safeFromName(it.trim()) }
      .toSet()
      .ifEmpty { setOf(WeaponCategory.MELEE, WeaponCategory.PISTOL) }
    PlayerProfileState(
      nickname = (prefs[Keys.NICKNAME] ?: "StreetAce").take(20).ifBlank { "StreetAce" },
      skinColorIndex = (prefs[Keys.SKIN_INDEX] ?: 1).coerceIn(0, 5),
      outfitColorIndex = (prefs[Keys.OUTFIT_INDEX] ?: 0).coerceIn(0, 7),
      cash = (prefs[Keys.CASH] ?: 1250).coerceAtLeast(0),
      unlockedWeapons = weapons,
      graphicsQuality = GraphicsQuality.safeFrom(prefs[Keys.GRAPHICS]),
      cameraSensitivity = (prefs[Keys.SENSITIVITY] ?: 1.0f).coerceIn(0.2f, 2.5f),
      audioVolume = (prefs[Keys.VOLUME] ?: 0.75f).coerceIn(0.0f, 1.0f),
    )
  }

  suspend fun saveProfile(state: PlayerProfileState) {
    dataStore.edit { prefs ->
      prefs[Keys.NICKNAME] = state.nickname.trim().take(20).ifBlank { "StreetAce" }
      prefs[Keys.SKIN_INDEX] = state.skinColorIndex.coerceIn(0, 5)
      prefs[Keys.OUTFIT_INDEX] = state.outfitColorIndex.coerceIn(0, 7)
      prefs[Keys.CASH] = state.cash.coerceAtLeast(0)
      prefs[Keys.WEAPONS] = state.unlockedWeapons.joinToString(",") { it.name }
      prefs[Keys.GRAPHICS] = state.graphicsQuality.name
      prefs[Keys.SENSITIVITY] = state.cameraSensitivity.coerceIn(0.2f, 2.5f)
      prefs[Keys.VOLUME] = state.audioVolume.coerceIn(0.0f, 1.0f)
    }
  }
}
