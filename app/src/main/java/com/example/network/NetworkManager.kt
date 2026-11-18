package com.example.network

import android.content.Context
import android.util.Log
import com.example.R
import com.example.game.GraphicsQuality
import com.example.game.PlayerProfileState
import com.example.game.VehicleType
import com.example.game.WeaponCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs

data class RemotePlayerRenderState(
  val userId: String,
  val nickname: String,
  val skinColorIndex: Int,
  val outfitColorIndex: Int,
  val x: Float,
  val y: Float,
  val z: Float,
  val yawDeg: Float,
  val speed: Float,
  val animState: String,
  val vehicleType: VehicleType,
  val vehicleHp: Int,
  val hp: Int,
  val armor: Int,
  val wantedStars: Int,
  val weaponIndex: Int,
  val inSafeZone: Boolean,
  val shotSeq: Long,
  val emoteText: String?,
)

private data class InterpSample(
  val startX: Float,
  val startY: Float,
  val startZ: Float,
  val startYaw: Float,
  val targetX: Float,
  val targetY: Float,
  val targetZ: Float,
  val targetYaw: Float,
  val startTimeMs: Long,
  val durationMs: Long,
  val base: RemotePlayerRenderState,
)

class RemotePlayerInterpolator {
  private val samples = ConcurrentHashMap<String, InterpSample>()

  fun pushSnapshot(remote: RemotePlayerRenderState, nowMs: Long = System.currentTimeMillis()) {
    val existing = samples[remote.userId]
    if (existing == null) {
      samples[remote.userId] = InterpSample(
        startX = remote.x,
        startY = remote.y,
        startZ = remote.z,
        startYaw = remote.yawDeg,
        targetX = remote.x,
        targetY = remote.y,
        targetZ = remote.z,
        targetYaw = remote.yawDeg,
        startTimeMs = nowMs,
        durationMs = 140L,
        base = remote,
      )
    } else {
      val current = sampleOne(existing, nowMs)
      val distSq = (remote.x - current.x) * (remote.x - current.x) +
        (remote.z - current.z) * (remote.z - current.z)
      // Snap on respawn/teleport > 25m; otherwise smooth interpolate
      val snap = distSq > 625f
      samples[remote.userId] = InterpSample(
        startX = if (snap) remote.x else current.x,
        startY = if (snap) remote.y else current.y,
        startZ = if (snap) remote.z else current.z,
        startYaw = if (snap) remote.yawDeg else current.yawDeg,
        targetX = remote.x,
        targetY = remote.y,
        targetZ = remote.z,
        targetYaw = remote.yawDeg,
        startTimeMs = nowMs,
        durationMs = 160L,
        base = remote,
      )
    }
  }

  fun pruneMissing(activeIds: Set<String>) {
    val keys = samples.keys.toList()
    for (k in keys) {
      if (k !in activeIds) samples.remove(k)
    }
  }

  fun clear() = samples.clear()

  fun getInterpolatedPlayers(nowMs: Long = System.currentTimeMillis()): List<RemotePlayerRenderState> {
    if (samples.isEmpty()) return emptyList()
    return samples.values.map { sampleOne(it, nowMs) }
  }

  private fun sampleOne(s: InterpSample, nowMs: Long): RemotePlayerRenderState {
    val t = ((nowMs - s.startTimeMs).toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    val x = s.startX + (s.targetX - s.startX) * smooth
    val y = s.startY + (s.targetY - s.startY) * smooth
    val z = s.startZ + (s.targetZ - s.startZ) * smooth
    val yaw = lerpAngleDeg(s.startYaw, s.targetYaw, smooth)
    return s.base.copy(x = x, y = y, z = z, yawDeg = yaw)
  }

  private fun lerpAngleDeg(a: Float, b: Float, t: Float): Float {
    var diff = ((b - a + 180f) % 360f) - 180f
    if (diff < -180f) diff += 360f
    if (abs(diff) > 360f) diff %= 360f
    return a + diff * t
  }
}

class FirestoreMultiplayerRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeUserProfile(userId: String): Flow<UserProfileDoc?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users").document(userId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == userId) {
            handleFirestoreError(fsErr ?: error, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun upsertUserProfile(profile: PlayerProfileState): Result<Unit> {
    val uid = auth.currentUser?.uid
    val path = "users/${uid ?: "unauthenticated"}"
    return try {
      val verifiedUid = requireUserId()
      val docRef = db.collection("users").document(verifiedUid)
      val existing = docRef.get().await()
      val cleanNickname = profile.nickname.trim().take(20).let {
        if (it.length >= 2) it else "StreetAce"
      }
      if (!existing.exists()) {
        val doc = UserProfileDoc(
          userId = verifiedUid,
          nickname = cleanNickname,
          skinColorIndex = profile.skinColorIndex.toLong(),
          outfitColorIndex = profile.outfitColorIndex.toLong(),
          cash = profile.cash.toLong(),
          unlockedWeapons = profile.unlockedWeapons.map { it.name }.take(5),
          graphicsQuality = profile.graphicsQuality.name,
          cameraSensitivity = profile.cameraSensitivity.toDouble(),
          audioVolume = profile.audioVolume.toDouble(),
        )
        docRef.set(doc.toCreateMap()).await()
      } else {
        docRef.update(
          mapOf(
            "nickname" to cleanNickname,
            "skinColorIndex" to profile.skinColorIndex.toLong().coerceIn(0L, 5L),
            "outfitColorIndex" to profile.outfitColorIndex.toLong().coerceIn(0L, 7L),
            "cash" to profile.cash.toLong().coerceIn(0L, 999_999_999L),
            "unlockedWeapons" to profile.unlockedWeapons.map { it.name }.take(5).ifEmpty { listOf("MELEE", "PISTOL") },
            "graphicsQuality" to profile.graphicsQuality.name,
            "cameraSensitivity" to profile.cameraSensitivity.toDouble().coerceIn(0.2, 2.5),
            "audioVolume" to profile.audioVolume.toDouble().coerceIn(0.0, 1.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  fun observePublicRooms(): Flow<List<MultiplayerRoomDoc>> = flow {
    val uid = requireUserId()
    val path = "rooms"
    emitAll(
      db.collection("rooms")
        .whereEqualTo("visibility", "PUBLIC")
        .snapshots()
        .map { snap ->
          snap.toObjects(MultiplayerRoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.playerCount }
        }
        .catch { error ->
          val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeMyRooms(userId: String): Flow<List<MultiplayerRoomDoc>> = flow {
    val path = "rooms"
    emitAll(
      db.collection("rooms")
        .whereEqualTo("hostUid", userId)
        .snapshots()
        .map { snap ->
          snap.toObjects(MultiplayerRoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == userId) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun createRoom(
    roomName: String,
    visibility: String,
    maxPlayers: Int,
    pvpEnabled: Boolean,
  ): Result<MultiplayerRoomDoc> {
    val roomId = "room_" + UUID.randomUUID().toString().replace("-", "").take(10)
    val path = "rooms/$roomId"
    return try {
      val uid = requireUserId()
      val sanitizedName = roomName.trim().take(32).let {
        if (it.length >= 3) it else "Vice City Room"
      }
      val doc = MultiplayerRoomDoc(
        id = roomId,
        hostUid = uid,
        name = sanitizedName,
        visibility = if (visibility == "PRIVATE") "PRIVATE" else "PUBLIC",
        maxPlayers = maxPlayers.coerceIn(16, 32).toLong(),
        pvpEnabled = pvpEnabled,
        playerCount = 1L,
      )
      db.collection("rooms").document(roomId).set(doc.toCreateMap()).await()
      Result.success(doc)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun getRoom(roomId: String): Result<MultiplayerRoomDoc?> {
    val path = "rooms/$roomId"
    return try {
      requireUserId()
      require(roomId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Invalid room ID format" }
      val snap = db.collection("rooms").document(roomId).get().await()
      val room = if (snap.exists()) {
        snap.toObject(MultiplayerRoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null
      Result.success(room)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun joinRoomAsPlayer(roomId: String, profile: PlayerProfileState): Result<Unit> {
    val uid = auth.currentUser?.uid
    val path = "rooms/$roomId/players/${uid ?: "unauth"}"
    return try {
      val verifiedUid = requireUserId()
      val playerDoc = RoomPlayerDoc(
        userId = verifiedUid,
        roomId = roomId,
        nickname = profile.nickname.trim().take(20).let { if (it.length >= 2) it else "Player" },
        skinColorIndex = profile.skinColorIndex.toLong(),
        outfitColorIndex = profile.outfitColorIndex.toLong(),
        x = -36.0,
        y = 0.0,
        z = 36.0,
        yaw = 0.0,
        speed = 0.0,
        animState = "IDLE",
        vehicleType = "NONE",
        vehicleHp = 100L,
        hp = 100L,
        armor = 50L,
        wantedStars = 0L,
        weaponIndex = 1L,
        inSafeZone = true,
        shotSeq = 0L,
      )
      db.collection("rooms").document(roomId)
        .collection("players").document(verifiedUid)
        .set(playerDoc.toCreateMap())
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun syncPlayerState(roomId: String, player: RoomPlayerDoc): Result<Unit> {
    val uid = auth.currentUser?.uid
    val path = "rooms/$roomId/players/${uid ?: "unauth"}"
    return try {
      val verifiedUid = requireUserId()
      db.collection("rooms").document(roomId)
        .collection("players").document(verifiedUid)
        .update(player.copy(userId = verifiedUid, roomId = roomId).toSyncUpdateMap())
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateRoomPlayerCount(roomId: String, count: Int): Result<Unit> {
    val path = "rooms/$roomId"
    return try {
      requireUserId()
      db.collection("rooms").document(roomId)
        .update(
          mapOf(
            "playerCount" to count.coerceIn(0, 32).toLong(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun leaveRoom(roomId: String): Result<Unit> {
    val uid = auth.currentUser?.uid
    val path = "rooms/$roomId/players/${uid ?: "unauth"}"
    return try {
      val verifiedUid = requireUserId()
      db.collection("rooms").document(roomId)
        .collection("players").document(verifiedUid)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  fun observeRoomPlayers(roomId: String): Flow<List<RoomPlayerDoc>> = flow {
    val uid = requireUserId()
    val path = "rooms/$roomId/players"
    emitAll(
      db.collection("rooms").document(roomId).collection("players")
        .whereEqualTo("roomId", roomId)
        .snapshots()
        .map { snap ->
          snap.toObjects(RoomPlayerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun sendRoomEvent(
    roomId: String,
    senderName: String,
    eventType: String,
    message: String,
    targetUid: String? = null,
    damage: Int? = null,
  ): Result<Unit> {
    val eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").take(12)
    val path = "rooms/$roomId/events/$eventId"
    return try {
      val uid = requireUserId()
      val doc = RoomEventDoc(
        roomId = roomId,
        senderId = uid,
        senderName = senderName.take(20).ifBlank { "Player" },
        eventType = eventType,
        message = message.take(120).ifBlank { "..." },
        targetUid = targetUid,
        damage = damage?.toLong(),
      )
      db.collection("rooms").document(roomId)
        .collection("events").document(eventId)
        .set(doc.toCreateMap())
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  fun observeRoomEvents(roomId: String): Flow<List<RoomEventDoc>> = flow {
    val uid = requireUserId()
    val path = "rooms/$roomId/events"
    emitAll(
      db.collection("rooms").document(roomId).collection("events")
        .whereEqualTo("roomId", roomId)
        .snapshots()
        .map { snap ->
          snap.toObjects(RoomEventDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            .takeLast(25)
        }
        .catch { error ->
          val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }
}

/**
 * Optional OkHttp WebSocket client transport for dedicated custom game servers (ws:// / wss://).
 */
class WebSocketMultiplayerClient {
  private val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .pingInterval(15, TimeUnit.SECONDS)
    .build()

  private var webSocket: WebSocket? = null
  private val _connected = MutableStateFlow(false)
  val connected: StateFlow<Boolean> = _connected.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun connect(
    wsUrl: String,
    roomId: String,
    localPlayerId: String,
    onRemoteState: (RemotePlayerRenderState) -> Unit,
    onChatEvent: (sender: String, msg: String, isEmote: Boolean) -> Unit,
    onIncomingHit: (targetUid: String, damage: Int, attacker: String) -> Unit,
  ) {
    disconnect()
    val request = try {
      Request.Builder().url(wsUrl).build()
    } catch (e: Exception) {
      _lastError.value = "URL WebSocket tidak valid: ${e.message}"
      return
    }
    webSocket = client.newWebSocket(
      request,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          _connected.value = true
          _lastError.value = null
          val joinJson = JSONObject().apply {
            put("type", "JOIN")
            put("roomId", roomId)
            put("userId", localPlayerId)
          }
          webSocket.send(joinJson.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          try {
            val json = JSONObject(text)
            when (json.optString("type")) {
              "STATE" -> {
                val uid = json.optString("userId")
                if (uid.isNotBlank() && uid != localPlayerId) {
                  onRemoteState(
                    RemotePlayerRenderState(
                      userId = uid,
                      nickname = json.optString("nickname", "Player"),
                      skinColorIndex = json.optInt("skinColorIndex", 1),
                      outfitColorIndex = json.optInt("outfitColorIndex", 0),
                      x = json.optDouble("x", 0.0).toFloat(),
                      y = json.optDouble("y", 0.0).toFloat(),
                      z = json.optDouble("z", 0.0).toFloat(),
                      yawDeg = json.optDouble("yaw", 0.0).toFloat(),
                      speed = json.optDouble("speed", 0.0).toFloat(),
                      animState = json.optString("animState", "IDLE"),
                      vehicleType = VehicleType.safeFrom(json.optString("vehicleType")),
                      vehicleHp = json.optInt("vehicleHp", 100),
                      hp = json.optInt("hp", 100),
                      armor = json.optInt("armor", 0),
                      wantedStars = json.optInt("wantedStars", 0),
                      weaponIndex = json.optInt("weaponIndex", 1),
                      inSafeZone = json.optBoolean("inSafeZone", false),
                      shotSeq = json.optLong("shotSeq", 0L),
                      emoteText = json.optString("emoteText").takeIf { it.isNotBlank() },
                    )
                  )
                }
              }
              "CHAT", "EMOTE" -> {
                onChatEvent(
                  json.optString("senderName", "Player"),
                  json.optString("message", ""),
                  json.optString("type") == "EMOTE",
                )
              }
              "HIT" -> {
                onIncomingHit(
                  json.optString("targetUid"),
                  json.optInt("damage", 0),
                  json.optString("senderName", "Rival"),
                )
              }
            }
          } catch (e: Exception) {
            Log.w("WSClient", "Malformed WS packet: ${e.message}")
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          _connected.value = false
          _lastError.value = t.localizedMessage ?: "Koneksi WebSocket terputus"
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          _connected.value = false
        }
      }
    )
  }

  fun sendState(roomId: String, player: RoomPlayerDoc) {
    val ws = webSocket ?: return
    val json = JSONObject().apply {
      put("type", "STATE")
      put("roomId", roomId)
      put("userId", player.userId)
      put("nickname", player.nickname)
      put("skinColorIndex", player.skinColorIndex)
      put("outfitColorIndex", player.outfitColorIndex)
      put("x", player.x)
      put("y", player.y)
      put("z", player.z)
      put("yaw", player.yaw)
      put("speed", player.speed)
      put("animState", player.animState)
      put("vehicleType", player.vehicleType)
      put("vehicleHp", player.vehicleHp)
      put("hp", player.hp)
      put("armor", player.armor)
      put("wantedStars", player.wantedStars)
      put("weaponIndex", player.weaponIndex)
      put("inSafeZone", player.inSafeZone)
      put("shotSeq", player.shotSeq)
      if (!player.emoteText.isNullOrBlank()) put("emoteText", player.emoteText)
    }
    ws.send(json.toString())
  }

  fun sendEvent(roomId: String, senderId: String, senderName: String, type: String, message: String, targetUid: String? = null, damage: Int? = null) {
    val ws = webSocket ?: return
    val json = JSONObject().apply {
      put("type", type)
      put("roomId", roomId)
      put("senderId", senderId)
      put("senderName", senderName)
      put("message", message)
      if (targetUid != null) put("targetUid", targetUid)
      if (damage != null) put("damage", damage)
    }
    ws.send(json.toString())
  }

  fun disconnect() {
    try {
      webSocket?.close(1000, "Leaving room")
    } catch (_: Exception) {
    }
    webSocket = null
    _connected.value = false
  }
}

fun UserProfileDoc.toPlayerProfileState(): PlayerProfileState {
  val weapons = unlockedWeapons
    .mapNotNull { WeaponCategory.safeFromName(it) }
    .toSet()
    .ifEmpty { setOf(WeaponCategory.MELEE, WeaponCategory.PISTOL) }
  return PlayerProfileState(
    nickname = nickname.ifBlank { "StreetAce" },
    skinColorIndex = skinColorIndex.toInt().coerceIn(0, 5),
    outfitColorIndex = outfitColorIndex.toInt().coerceIn(0, 7),
    cash = cash.toInt().coerceAtLeast(0),
    unlockedWeapons = weapons,
    graphicsQuality = GraphicsQuality.safeFrom(graphicsQuality),
    cameraSensitivity = cameraSensitivity.toFloat().coerceIn(0.2f, 2.5f),
    audioVolume = audioVolume.toFloat().coerceIn(0f, 1f),
  )
}
