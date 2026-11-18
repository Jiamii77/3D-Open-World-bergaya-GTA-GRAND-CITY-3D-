package com.example.network

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val nickname: String = "StreetAce",
  val skinColorIndex: Long = 1L,
  val outfitColorIndex: Long = 0L,
  val cash: Long = 1250L,
  val unlockedWeapons: List<String> = listOf("MELEE", "PISTOL"),
  val graphicsQuality: String = "MEDIUM",
  val cameraSensitivity: Double = 1.0,
  val audioVolume: Double = 0.75,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(): Map<String, Any> =
    mapOf(
      "userId" to userId,
      "nickname" to nickname.take(20),
      "skinColorIndex" to skinColorIndex.coerceIn(0L, 5L),
      "outfitColorIndex" to outfitColorIndex.coerceIn(0L, 7L),
      "cash" to cash.coerceIn(0L, 999_999_999L),
      "unlockedWeapons" to unlockedWeapons.take(5).ifEmpty { listOf("MELEE", "PISTOL") },
      "graphicsQuality" to graphicsQuality,
      "cameraSensitivity" to cameraSensitivity.coerceIn(0.2, 2.5),
      "audioVolume" to audioVolume.coerceIn(0.0, 1.0),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class MultiplayerRoomDoc(
  @DocumentId val id: String = "",
  val hostUid: String = "",
  val name: String = "Neon Harbor City",
  val visibility: String = "PUBLIC",
  val maxPlayers: Long = 24L,
  val pvpEnabled: Boolean = true,
  val playerCount: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(): Map<String, Any> =
    mapOf(
      "hostUid" to hostUid,
      "name" to name.trim().take(32),
      "visibility" to if (visibility == "PRIVATE") "PRIVATE" else "PUBLIC",
      "maxPlayers" to maxPlayers.coerceIn(2L, 32L),
      "pvpEnabled" to pvpEnabled,
      "playerCount" to playerCount.coerceIn(0L, 32L),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class RoomPlayerDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val roomId: String = "",
  val nickname: String = "StreetAce",
  val skinColorIndex: Long = 1L,
  val outfitColorIndex: Long = 0L,
  val x: Double = 0.0,
  val y: Double = 0.0,
  val z: Double = 0.0,
  val yaw: Double = 0.0,
  val speed: Double = 0.0,
  val animState: String = "IDLE",
  val vehicleType: String = "NONE",
  val vehicleHp: Long = 100L,
  val hp: Long = 100L,
  val armor: Long = 50L,
  val wantedStars: Long = 0L,
  val weaponIndex: Long = 1L,
  val inSafeZone: Boolean = true,
  val shotSeq: Long = 0L,
  val emoteText: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(): Map<String, Any> {
    val raw = mutableMapOf<String, Any?>(
      "userId" to userId,
      "roomId" to roomId,
      "nickname" to nickname.take(20),
      "skinColorIndex" to skinColorIndex.coerceIn(0L, 5L),
      "outfitColorIndex" to outfitColorIndex.coerceIn(0L, 7L),
      "x" to x.coerceIn(-1000.0, 1000.0),
      "y" to y.coerceIn(-50.0, 200.0),
      "z" to z.coerceIn(-1000.0, 1000.0),
      "yaw" to yaw.coerceIn(-3600.0, 3600.0),
      "speed" to speed.coerceIn(-50.0, 120.0),
      "animState" to animState.take(20).ifBlank { "IDLE" },
      "vehicleType" to vehicleType,
      "vehicleHp" to vehicleHp.coerceIn(0L, 500L),
      "hp" to hp.coerceIn(0L, 100L),
      "armor" to armor.coerceIn(0L, 100L),
      "wantedStars" to wantedStars.coerceIn(0L, 5L),
      "weaponIndex" to weaponIndex.coerceIn(0L, 4L),
      "inSafeZone" to inSafeZone,
      "shotSeq" to shotSeq.coerceAtLeast(0L),
      "emoteText" to emoteText?.take(40),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  fun toSyncUpdateMap(): Map<String, Any> {
    val raw = mutableMapOf<String, Any?>(
      "nickname" to nickname.take(20),
      "skinColorIndex" to skinColorIndex.coerceIn(0L, 5L),
      "outfitColorIndex" to outfitColorIndex.coerceIn(0L, 7L),
      "x" to x.coerceIn(-1000.0, 1000.0),
      "y" to y.coerceIn(-50.0, 200.0),
      "z" to z.coerceIn(-1000.0, 1000.0),
      "yaw" to yaw.coerceIn(-3600.0, 3600.0),
      "speed" to speed.coerceIn(-50.0, 120.0),
      "animState" to animState.take(20).ifBlank { "IDLE" },
      "vehicleType" to vehicleType,
      "vehicleHp" to vehicleHp.coerceIn(0L, 500L),
      "hp" to hp.coerceIn(0L, 100L),
      "armor" to armor.coerceIn(0L, 100L),
      "wantedStars" to wantedStars.coerceIn(0L, 5L),
      "weaponIndex" to weaponIndex.coerceIn(0L, 4L),
      "inSafeZone" to inSafeZone,
      "shotSeq" to shotSeq.coerceAtLeast(0L),
      "emoteText" to emoteText?.take(40),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }
}

@Keep
@IgnoreExtraProperties
data class RoomEventDoc(
  @DocumentId val id: String = "",
  val roomId: String = "",
  val senderId: String = "",
  val senderName: String = "Player",
  val eventType: String = "CHAT",
  val message: String = "",
  val targetUid: String? = null,
  val damage: Long? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  fun toCreateMap(): Map<String, Any> {
    val raw = mutableMapOf<String, Any?>(
      "roomId" to roomId,
      "senderId" to senderId,
      "senderName" to senderName.take(20).ifBlank { "Player" },
      "eventType" to eventType,
      "message" to message.take(120).ifBlank { "..." },
      "targetUid" to targetUid?.take(128),
      "damage" to damage?.coerceIn(0L, 100L),
      "createdAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }
}
