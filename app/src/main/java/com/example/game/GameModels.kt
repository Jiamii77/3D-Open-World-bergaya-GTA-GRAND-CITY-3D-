package com.example.game

import androidx.compose.ui.graphics.Color

enum class GraphicsQuality(val label: String, val drawDistance: Float, val maxNpcs: Int, val maxTraffic: Int, val renderScale: Float) {
  LOW("Low (60 FPS Hemat)", 110f, 10, 6, 0.72f),
  MEDIUM("Medium (Seimbang)", 155f, 18, 10, 0.88f),
  HIGH("High (Detail Penuh)", 210f, 26, 14, 1.0f);

  companion object {
    fun safeFrom(raw: String?): GraphicsQuality =
      entries.firstOrNull { it.name == raw } ?: MEDIUM
  }
}

enum class WeaponCategory(
  val index: Int,
  val displayName: String,
  val shortTag: String,
  val damage: Int,
  val range: Float,
  val fireCooldownMs: Long,
  val magSize: Int,
  val maxReserve: Int,
  val reloadMs: Long,
  val pellets: Int,
  val spreadDeg: Float,
  val unlockPrice: Int,
  val ammoPrice: Int,
  val recoilKick: Float,
) {
  MELEE(0, "Tangan Kosong / Bat", "MELEE", 34, 3.4f, 420L, 1, 1, 0L, 1, 0f, 0, 0, 0.6f),
  PISTOL(1, "Pistol 9mm", "PISTOL", 30, 42f, 340L, 12, 96, 1100L, 1, 1.5f, 0, 60, 1.4f),
  SMG(2, "SMG Micro", "SMG", 16, 36f, 105L, 30, 180, 1350L, 1, 4.2f, 450, 120, 0.9f),
  SHOTGUN(3, "Pump Shotgun", "SHOTGUN", 18, 22f, 760L, 8, 48, 1650L, 5, 11f, 800, 140, 3.2f),
  ASSAULT_RIFLE(4, "Assault Rifle", "RIFLE", 28, 62f, 130L, 30, 210, 1500L, 1, 2.2f, 1400, 180, 1.6f);

  companion object {
    fun byIndex(idx: Int): WeaponCategory =
      entries.firstOrNull { it.index == idx } ?: MELEE

    fun safeFromName(raw: String?): WeaponCategory? =
      entries.firstOrNull { it.name == raw }
  }
}

enum class VehicleType(
  val displayName: String,
  val maxSpeed: Float,
  val reverseSpeed: Float,
  val acceleration: Float,
  val turnRateDeg: Float,
  val maxHp: Int,
  val halfWidth: Float,
  val halfLength: Float,
  val height: Float,
) {
  NONE("Jalan Kaki", 0f, 0f, 0f, 0f, 100, 0.4f, 0.4f, 1.8f),
  SEDAN("Sports Sedan", 31f, 11f, 20f, 115f, 220, 1.05f, 2.25f, 1.25f),
  SUV("Urban SUV", 24f, 9f, 14f, 92f, 360, 1.18f, 2.45f, 1.65f),
  MOTO("Street Moto", 34f, 7f, 25f, 145f, 140, 0.52f, 1.25f, 1.15f),
  POLICE("Police Cruiser", 32f, 11f, 21f, 118f, 280, 1.08f, 2.30f, 1.32f);

  companion object {
    fun safeFrom(raw: String?): VehicleType =
      entries.firstOrNull { it.name == raw } ?: NONE
  }
}

object CharacterPalettes {
  val skinColors = listOf(
    Color(0xFFF6D2B8), // Light Peach
    Color(0xFFE5B48E), // Warm Sand
    Color(0xFFC98B5E), // Golden Tan
    Color(0xFFA2653E), // Caramel Bronze
    Color(0xFF744229), // Deep Espresso
    Color(0xFF4A2A1A), // Rich Cocoa
  )

  val skinRgb = listOf(
    floatArrayOf(0.96f, 0.82f, 0.72f),
    floatArrayOf(0.90f, 0.71f, 0.56f),
    floatArrayOf(0.79f, 0.55f, 0.37f),
    floatArrayOf(0.64f, 0.40f, 0.24f),
    floatArrayOf(0.45f, 0.26f, 0.16f),
    floatArrayOf(0.29f, 0.16f, 0.10f),
  )

  val outfitColors = listOf(
    Color(0xFF00E5FF), // Neon Cyan Jacket
    Color(0xFFFF5E3A), // Sunset Coral Streetwear
    Color(0xFF2EE59D), // Emerald Tracksuit
    Color(0xFFFFC107), // Amber Gold Varsity
    Color(0xFF9D4EDD), // Violet Cyber Hoodie
    Color(0xFFE63946), // Crimson Leather
    Color(0xFFF1F5F9), // Arctic White Suit
    Color(0xFF334155), // Stealth Slate Tactical
  )

  val outfitRgb = listOf(
    floatArrayOf(0.00f, 0.90f, 1.00f),
    floatArrayOf(1.00f, 0.37f, 0.23f),
    floatArrayOf(0.18f, 0.90f, 0.62f),
    floatArrayOf(1.00f, 0.76f, 0.03f),
    floatArrayOf(0.62f, 0.31f, 0.87f),
    floatArrayOf(0.90f, 0.22f, 0.27f),
    floatArrayOf(0.95f, 0.96f, 0.98f),
    floatArrayOf(0.20f, 0.25f, 0.33f),
  )
}

data class PlayerProfileState(
  val nickname: String = "StreetAce",
  val skinColorIndex: Int = 1,
  val outfitColorIndex: Int = 0,
  val cash: Int = 1250,
  val unlockedWeapons: Set<WeaponCategory> = setOf(WeaponCategory.MELEE, WeaponCategory.PISTOL),
  val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
  val cameraSensitivity: Float = 1.0f,
  val audioVolume: Float = 0.75f,
)

enum class WorldMarkerType(val label: String) {
  WEAPON_SHOP("Toko Senjata"),
  GARAGE("Garasi Kendaraan"),
  SAFE_HOUSE("Safe House"),
}

data class WorldMarker(
  val type: WorldMarkerType,
  val x: Float,
  val z: Float,
  val radius: Float,
)

data class MinimapBlip(
  val x: Float,
  val z: Float,
  val kind: BlipKind,
  val label: String = "",
)

enum class BlipKind {
  REMOTE_PLAYER,
  POLICE,
  NPC_HOSTILE,
  VEHICLE,
  SHOP,
  GARAGE,
  SAFE_HOUSE,
}

data class ChatFeedItem(
  val id: String,
  val senderName: String,
  val message: String,
  val isEmote: Boolean = false,
)

data class HudState(
  val health: Int = 100,
  val armor: Int = 50,
  val cash: Int = 1250,
  val wantedStars: Int = 0,
  val wantedCooldownRatio: Float = 0f,
  val activeWeapon: WeaponCategory = WeaponCategory.PISTOL,
  val clipAmmo: Int = 12,
  val reserveAmmo: Int = 48,
  val isReloading: Boolean = false,
  val unlockedWeapons: Set<WeaponCategory> = setOf(WeaponCategory.MELEE, WeaponCategory.PISTOL),
  val inVehicle: Boolean = false,
  val vehicleType: VehicleType = VehicleType.NONE,
  val vehicleHp: Int = 100,
  val vehicleMaxHp: Int = 100,
  val vehicleSpeedKmh: Int = 0,
  val nearVehicleType: VehicleType? = null,
  val nearMarker: WorldMarkerType? = null,
  val inSafeZone: Boolean = true,
  val hasAutoAimTarget: Boolean = false,
  val playerX: Float = 0f,
  val playerZ: Float = 0f,
  val playerYawDeg: Float = 0f,
  val timeOfDayHours: Float = 17.5f,
  val isNight: Boolean = false,
  val isOnline: Boolean = false,
  val onlineRoomName: String = "",
  val onlinePlayerCount: Int = 1,
  val pvpEnabled: Boolean = true,
  val minimapBlips: List<MinimapBlip> = emptyList(),
  val chatFeed: List<ChatFeedItem> = emptyList(),
  val statusBanner: String? = null,
  val isWasted: Boolean = false,
  val currentFps: Int = 60,
)
