package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalPreferencesRepository
import com.example.game.ChatFeedItem
import com.example.game.GameEngine
import com.example.game.GraphicsQuality
import com.example.game.PlayerProfileState
import com.example.game.VehicleType
import com.example.game.WeaponCategory
import com.example.network.FirestoreMultiplayerRepository
import com.example.network.MultiplayerRoomDoc
import com.example.network.RemotePlayerRenderState
import com.example.network.RoomPlayerDoc
import com.example.network.WebSocketMultiplayerClient
import com.example.network.toPlayerProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppRoute {
  MAIN_MENU,
  ONLINE_LOBBY,
  IN_GAME,
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class MainGameViewModel(application: Application) : AndroidViewModel(application) {
  private val localPrefs = LocalPreferencesRepository(application)
  val gameEngine = GameEngine()

  private val _route = MutableStateFlow(AppRoute.MAIN_MENU)
  val route: StateFlow<AppRoute> = _route.asStateFlow()

  private val _profile = MutableStateFlow(PlayerProfileState())
  val profile: StateFlow<PlayerProfileState> = _profile.asStateFlow()

  init {
    viewModelScope.launch {
      localPrefs.profileFlow.collect { saved ->
        _profile.value = saved
        gameEngine.updateProfileSettings(saved)
      }
    }
    gameEngine.onSaveProfileProgress = { newCash, unlocked ->
      val updated = _profile.value.copy(cash = newCash, unlockedWeapons = unlocked)
      updateProfile(updated)
    }
  }

  fun updateProfile(updated: PlayerProfileState) {
    _profile.value = updated
    gameEngine.updateProfileSettings(updated)
    viewModelScope.launch {
      localPrefs.saveProfile(updated)
    }
  }

  fun updateCustomization(nickname: String, skinIdx: Int, outfitIdx: Int) {
    val cleanNick = nickname.trim().take(20).ifBlank { "StreetAce" }
    updateProfile(_profile.value.copy(nickname = cleanNick, skinColorIndex = skinIdx, outfitColorIndex = outfitIdx))
  }

  fun updateSettings(quality: GraphicsQuality, sensitivity: Float, volume: Float) {
    updateProfile(_profile.value.copy(graphicsQuality = quality, cameraSensitivity = sensitivity, audioVolume = volume))
  }

  fun navigateTo(target: AppRoute) {
    _route.value = target
  }

  fun launchSingleplayer() {
    gameEngine.onSyncLocalPlayer = null
    gameEngine.onSendPvpHit = null
    gameEngine.startSession(
      profileState = _profile.value,
      online = false,
    )
    _route.value = AppRoute.IN_GAME
  }

  fun exitGameToMenu() {
    gameEngine.onSyncLocalPlayer = null
    gameEngine.onSendPvpHit = null
    _route.value = AppRoute.MAIN_MENU
  }
}

class OnlineSessionViewModel(
  private val repo: FirestoreMultiplayerRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val wsClient = WebSocketMultiplayerClient()

  val publicRoomsState: StateFlow<UiState<List<MultiplayerRoomDoc>>> =
    repo.observePublicRooms()
      .map<List<MultiplayerRoomDoc>, UiState<List<MultiplayerRoomDoc>>> { UiState.Success(it) }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        Log.w("OnlineVM", "Error observing public rooms", root)
        emit(UiState.Error(root.localizedMessage ?: "Gagal memuat server publik"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val myRoomsState: StateFlow<UiState<List<MultiplayerRoomDoc>>> =
    repo.observeMyRooms(currentUserId)
      .map<List<MultiplayerRoomDoc>, UiState<List<MultiplayerRoomDoc>>> { UiState.Success(it) }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        Log.w("OnlineVM", "Error observing private rooms", root)
        emit(UiState.Error(root.localizedMessage ?: "Gagal memuat room pribadi"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _cloudProfile = MutableStateFlow<PlayerProfileState?>(null)
  val cloudProfile: StateFlow<PlayerProfileState?> = _cloudProfile.asStateFlow()

  private val _lobbyBusy = MutableStateFlow(false)
  val lobbyBusy: StateFlow<Boolean> = _lobbyBusy.asStateFlow()

  private val _lobbyError = MutableStateFlow<String?>(null)
  val lobbyError: StateFlow<String?> = _lobbyError.asStateFlow()

  private var activeRoomId: String? = null
  private var useWebSocketOnly = false
  private var roomPlayersJob: Job? = null
  private var roomEventsJob: Job? = null
  private var profileJob: Job? = null
  private val seenEventIds = HashSet<String>()

  init {
    profileJob = viewModelScope.launch {
      repo.observeUserProfile(currentUserId)
        .catch { err ->
          if (err is CancellationException && err.cause == null) throw err
          Log.w("OnlineVM", "Profile sync warning: ${err.message}")
        }
        .collect { doc ->
          if (doc != null) {
            _cloudProfile.value = doc.toPlayerProfileState()
          }
        }
    }
  }

  fun syncCloudProfile(profile: PlayerProfileState) {
    viewModelScope.launch {
      repo.upsertUserProfile(profile)
    }
  }

  fun clearError() {
    _lobbyError.value = null
  }

  fun createAndJoinRoom(
    roomName: String,
    visibility: String,
    maxPlayers: Int,
    pvpEnabled: Boolean,
    profile: PlayerProfileState,
    engine: GameEngine,
    onJoined: () -> Unit,
  ) {
    viewModelScope.launch {
      _lobbyBusy.value = true
      _lobbyError.value = null
      repo.upsertUserProfile(profile)
      val created = repo.createRoom(roomName, visibility, maxPlayers, pvpEnabled)
      created.fold(
        onSuccess = { room ->
          joinFirestoreRoom(room, profile, engine, onJoined)
        },
        onFailure = { err ->
          _lobbyBusy.value = false
          _lobbyError.value = err.localizedMessage ?: "Gagal membuat room"
        }
      )
    }
  }

  fun joinRoomById(
    roomId: String,
    profile: PlayerProfileState,
    engine: GameEngine,
    onJoined: () -> Unit,
  ) {
    val cleanId = roomId.trim()
    if (cleanId.isBlank()) {
      _lobbyError.value = "Masukkan ID Room yang valid"
      return
    }
    viewModelScope.launch {
      _lobbyBusy.value = true
      _lobbyError.value = null
      val res = repo.getRoom(cleanId)
      res.fold(
        onSuccess = { room ->
          if (room == null) {
            _lobbyBusy.value = false
            _lobbyError.value = "Room '$cleanId' tidak ditemukan"
          } else {
            joinFirestoreRoom(room, profile, engine, onJoined)
          }
        },
        onFailure = { err ->
          _lobbyBusy.value = false
          _lobbyError.value = err.localizedMessage ?: "Gagal mencari room"
        }
      )
    }
  }

  fun joinFirestoreRoom(
    room: MultiplayerRoomDoc,
    profile: PlayerProfileState,
    engine: GameEngine,
    onJoined: () -> Unit,
  ) {
    viewModelScope.launch {
      _lobbyBusy.value = true
      _lobbyError.value = null
      stopRoomStreams()
      useWebSocketOnly = false

      repo.upsertUserProfile(profile)
      val joinRes = repo.joinRoomAsPlayer(room.id, profile)
      if (joinRes.isFailure) {
        _lobbyBusy.value = false
        _lobbyError.value = joinRes.exceptionOrNull()?.localizedMessage ?: "Gagal bergabung ke room"
        return@launch
      }

      activeRoomId = room.id
      seenEventIds.clear()

      engine.startSession(
        profileState = profile,
        online = true,
        roomId = room.id,
        roomName = room.name,
        pvp = room.pvpEnabled,
        uid = currentUserId,
      )

      engine.onSyncLocalPlayer = { doc ->
        viewModelScope.launch {
          repo.syncPlayerState(room.id, doc)
        }
      }
      engine.onSendPvpHit = { targetUid, damage ->
        viewModelScope.launch {
          repo.sendRoomEvent(
            roomId = room.id,
            senderName = engine.profile.nickname,
            eventType = "HIT",
            message = "Hit",
            targetUid = targetUid,
            damage = damage,
          )
        }
      }

      roomPlayersJob = viewModelScope.launch {
        repo.observeRoomPlayers(room.id)
          .catch { err ->
            if (err is CancellationException && err.cause == null) throw err
            Log.w("OnlineVM", "Player listener warning: ${err.message}")
          }
          .collect { players ->
            val nowMs = System.currentTimeMillis()
            val activeIds = HashSet<String>()
            for (p in players) {
              if (p.userId == currentUserId || p.userId.isBlank()) continue
              activeIds.add(p.userId)
              engine.interpolator.pushSnapshot(
                RemotePlayerRenderState(
                  userId = p.userId,
                  nickname = p.nickname,
                  skinColorIndex = p.skinColorIndex.toInt(),
                  outfitColorIndex = p.outfitColorIndex.toInt(),
                  x = p.x.toFloat(),
                  y = p.y.toFloat(),
                  z = p.z.toFloat(),
                  yawDeg = p.yaw.toFloat(),
                  speed = p.speed.toFloat(),
                  animState = p.animState,
                  vehicleType = VehicleType.safeFrom(p.vehicleType),
                  vehicleHp = p.vehicleHp.toInt(),
                  hp = p.hp.toInt(),
                  armor = p.armor.toInt(),
                  wantedStars = p.wantedStars.toInt(),
                  weaponIndex = p.weaponIndex.toInt(),
                  inSafeZone = p.inSafeZone,
                  shotSeq = p.shotSeq,
                  emoteText = p.emoteText,
                ),
                nowMs,
              )
            }
            engine.interpolator.pruneMissing(activeIds)
            if (room.hostUid == currentUserId) {
              repo.updateRoomPlayerCount(room.id, players.size)
            }
          }
      }

      roomEventsJob = viewModelScope.launch {
        repo.observeRoomEvents(room.id)
          .catch { err ->
            if (err is CancellationException && err.cause == null) throw err
            Log.w("OnlineVM", "Event listener warning: ${err.message}")
          }
          .collect { events ->
            for (evt in events) {
              if (!seenEventIds.add(evt.id)) continue
              when (evt.eventType) {
                "CHAT", "EMOTE", "RACE" -> {
                  engine.pushChatItem(
                    ChatFeedItem(
                      id = evt.id,
                      senderName = evt.senderName,
                      message = evt.message,
                      isEmote = evt.eventType == "EMOTE",
                    )
                  )
                }
                "HIT" -> {
                  if (evt.targetUid == currentUserId && evt.senderId != currentUserId) {
                    engine.applyIncomingPvpDamage((evt.damage ?: 0L).toInt(), evt.senderName)
                  }
                }
              }
            }
          }
      }

      _lobbyBusy.value = false
      onJoined()
    }
  }

  fun joinCustomWebSocketServer(
    wsUrl: String,
    roomCode: String,
    profile: PlayerProfileState,
    engine: GameEngine,
    onJoined: () -> Unit,
  ) {
    stopRoomStreams()
    useWebSocketOnly = true
    val cleanRoom = roomCode.trim().ifBlank { "ws_city_01" }
    activeRoomId = cleanRoom

    engine.startSession(
      profileState = profile,
      online = true,
      roomId = cleanRoom,
      roomName = "WS: $cleanRoom",
      pvp = true,
      uid = currentUserId,
    )

    wsClient.connect(
      wsUrl = wsUrl.trim(),
      roomId = cleanRoom,
      localPlayerId = currentUserId,
      onRemoteState = { remote ->
        engine.interpolator.pushSnapshot(remote)
      },
      onChatEvent = { sender, msg, isEmote ->
        engine.pushChatItem(ChatFeedItem(System.nanoTime().toString(), sender, msg, isEmote))
      },
      onIncomingHit = { targetUid, damage, attacker ->
        if (targetUid == currentUserId) {
          engine.applyIncomingPvpDamage(damage, attacker)
        }
      },
    )

    engine.onSyncLocalPlayer = { doc -> wsClient.sendState(cleanRoom, doc) }
    engine.onSendPvpHit = { targetUid, damage ->
      wsClient.sendEvent(cleanRoom, currentUserId, profile.nickname, "HIT", "Hit", targetUid, damage)
    }

    onJoined()
  }

  fun sendChatOrEmote(senderName: String, message: String, isEmote: Boolean) {
    val rId = activeRoomId ?: return
    val clean = message.trim().take(120)
    if (clean.isBlank()) return
    if (useWebSocketOnly) {
      wsClient.sendEvent(rId, currentUserId, senderName, if (isEmote) "EMOTE" else "CHAT", clean)
    } else {
      viewModelScope.launch {
        repo.sendRoomEvent(
          roomId = rId,
          senderName = senderName,
          eventType = if (isEmote) "EMOTE" else "CHAT",
          message = clean,
        )
      }
    }
  }

  suspend fun leaveCurrentRoom() {
    val rId = activeRoomId
    stopRoomStreams()
    if (rId != null && !useWebSocketOnly) {
      repo.leaveRoom(rId)
    }
    activeRoomId = null
  }

  fun cleanupListenersOnSignOut() {
    profileJob?.cancel()
    profileJob = null
    stopRoomStreams()
  }

  private fun stopRoomStreams() {
    roomPlayersJob?.cancel()
    roomPlayersJob = null
    roomEventsJob?.cancel()
    roomEventsJob = null
    wsClient.disconnect()
  }

  override fun onCleared() {
    super.onCleared()
    cleanupListenersOnSignOut()
  }
}
