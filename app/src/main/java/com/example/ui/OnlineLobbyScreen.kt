package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.game.GameEngine
import com.example.game.PlayerProfileState
import com.example.network.AuthManager
import com.example.network.FirestoreMultiplayerRepository
import com.example.network.MultiplayerRoomDoc
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceAmber
import com.example.ui.theme.ViceCardBorder
import com.example.ui.theme.ViceCrimson
import com.example.ui.theme.ViceEmerald
import com.example.ui.theme.ViceMidnight
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceSurfaceElevated
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun OnlineLobbyRoute(
  currentUser: FirebaseUser?,
  profile: PlayerProfileState,
  gameEngine: GameEngine,
  onSyncCloudProfileToLocal: (PlayerProfileState) -> Unit,
  onEnterOnlineGame: (OnlineSessionViewModel) -> Unit,
  onBackToMenu: () -> Unit,
) {
  BackHandler { onBackToMenu() }

  if (currentUser == null) {
    OnlineAuthScreen(
      onBackToMenu = onBackToMenu,
      onAuthSuccess = {},
    )
  } else {
    val uid = currentUser.uid
    key(uid) {
      val sessionVm: OnlineSessionViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            OnlineSessionViewModel(FirestoreMultiplayerRepository(db), uid)
          }
        },
      )
      AuthenticatedOnlineLobby(
        user = currentUser,
        sessionVm = sessionVm,
        profile = profile,
        gameEngine = gameEngine,
        onSyncCloudProfileToLocal = onSyncCloudProfileToLocal,
        onEnterOnlineGame = { onEnterOnlineGame(sessionVm) },
        onBackToMenu = onBackToMenu,
      )
    }
  }
}

@Composable
private fun OnlineAuthScreen(
  onBackToMenu: () -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    AuthManager.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      scope = scope,
      onAuthSuccess = onAuthSuccess,
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ViceMidnight)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .navigationBarsPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    IconButton(
      onClick = onBackToMenu,
      modifier = Modifier
        .align(Alignment.TopStart)
        .size(48.dp)
        .testTag("auth_back_button"),
    ) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
    }

    Card(
      modifier = Modifier.width(460.dp),
      colors = CardDefaults.cardColors(containerColor = ViceSurface),
      border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
      shape = RoundedCornerShape(20.dp),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(40.dp))
        Text("Masuk ke Online Free Roam", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
          "Login menggunakan akun Google untuk bergabung ke server kota (16-32 pemain), menyimpan uang & senjata, serta PvP / balapan liar.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )

        if (errorMsg != null) {
          Text(errorMsg!!, style = MaterialTheme.typography.bodyMedium, color = ViceCrimson)
        }

        Button(
          onClick = {
            isLoading = true
            errorMsg = null
            AuthManager.onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              scope = scope,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMsg = msg
              },
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("google_sign_in_button"),
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
        ) {
          if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ViceMidnight, strokeWidth = 2.dp)
          } else {
            Text(stringResource(R.string.btn_sign_in_google), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun AuthenticatedOnlineLobby(
  user: FirebaseUser,
  sessionVm: OnlineSessionViewModel,
  profile: PlayerProfileState,
  gameEngine: GameEngine,
  onSyncCloudProfileToLocal: (PlayerProfileState) -> Unit,
  onEnterOnlineGame: () -> Unit,
  onBackToMenu: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val publicRooms by sessionVm.publicRoomsState.collectAsStateWithLifecycle()
  val myRooms by sessionVm.myRoomsState.collectAsStateWithLifecycle()
  val cloudProfile by sessionVm.cloudProfile.collectAsStateWithLifecycle()
  val busy by sessionVm.lobbyBusy.collectAsStateWithLifecycle()
  val errorMsg by sessionVm.lobbyError.collectAsStateWithLifecycle()

  var showCreateDialog by remember { mutableStateOf(false) }
  var joinCodeInput by remember { mutableStateOf("") }
  var showWsPanel by remember { mutableStateOf(false) }
  var wsUrlInput by remember { mutableStateOf("wss://echo.websocket.events") }
  var wsRoomInput by remember { mutableStateOf("vice_ws_01") }

  LaunchedEffect(cloudProfile) {
    cloudProfile?.let { cp ->
      if (cp.cash > profile.cash || cp.unlockedWeapons.size > profile.unlockedWeapons.size) {
        onSyncCloudProfileToLocal(cp)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ViceMidnight)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 12.dp),
  ) {
    // Top Lobby Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(
          onClick = onBackToMenu,
          modifier = Modifier
            .size(48.dp)
            .testTag("lobby_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
        Column {
          Text("ONLINE FREE ROAM LOBBY", style = MaterialTheme.typography.headlineMedium, color = Color.White)
          Text(
            "Akun: ${user.email ?: profile.nickname} • Nickname: ${profile.nickname}",
            style = MaterialTheme.typography.bodyMedium,
            color = ViceEmerald,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
          selected = showWsPanel,
          onClick = { showWsPanel = !showWsPanel },
          label = { Text(if (showWsPanel) "Server Publik" else "Custom WebSocket") },
          leadingIcon = { Icon(Icons.Filled.Lan, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )

        Button(
          onClick = { showCreateDialog = true },
          modifier = Modifier
            .height(48.dp)
            .testTag("create_room_button"),
          colors = ButtonDefaults.buttonColors(containerColor = SunsetCoral, contentColor = Color.White),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Buat Room")
        }

        OutlinedButton(
          onClick = {
            AuthManager.signOut(
              credentialManager = credentialManager,
              scope = scope,
              onBeforeSignOut = {
                sessionVm.leaveCurrentRoom()
                sessionVm.cleanupListenersOnSignOut()
              },
            )
          },
          modifier = Modifier
            .height(48.dp)
            .testTag("sign_out_button"),
          border = BorderStroke(1.dp, ViceCardBorder),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
      }
    }

    if (errorMsg != null) {
      Surface(
        color = ViceCrimson.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, ViceCrimson),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(errorMsg!!, color = Color.White, style = MaterialTheme.typography.bodyMedium)
          TextButton(onClick = { sessionVm.clearError() }) { Text("Tutup") }
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    if (showWsPanel) {
      // Custom WebSocket / Socket Relay Connection
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ViceSurface),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Koneksi Custom WebSocket Real-Time Server", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
          Text(
            "Hubungkan langsung ke server WebSocket (ws:// atau wss://) untuk sinkronisasi posisi, kendaraan, tembakan & PvP custom.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
              value = wsUrlInput,
              onValueChange = { wsUrlInput = it },
              label = { Text("WebSocket URL") },
              singleLine = true,
              modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
              value = wsRoomInput,
              onValueChange = { wsRoomInput = it },
              label = { Text("Room Channel") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
          Button(
            onClick = {
              sessionVm.joinCustomWebSocketServer(wsUrlInput, wsRoomInput, profile, gameEngine, onEnterOnlineGame)
            },
            modifier = Modifier
              .height(48.dp)
              .testTag("connect_ws_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
          ) {
            Text("Gabung via WebSocket", fontWeight = FontWeight.Bold)
          }
        }
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Left: Public & Private Server Browser
        Card(
          modifier = Modifier
            .weight(1.7f)
            .fillMaxHeight(),
          colors = CardDefaults.cardColors(containerColor = ViceSurface),
          border = BorderStroke(1.dp, ViceCardBorder),
          shape = RoundedCornerShape(16.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("Server Kota Publik (16–32 Pemain)", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))

            when (val state = publicRooms) {
              is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = NeonCyan)
                }
              }
              is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Text(state.message, color = ViceCrimson)
                }
              }
              is UiState.Success -> {
                val combined = (state.data + ((myRooms as? UiState.Success)?.data ?: emptyList()))
                  .distinctBy { it.id }
                if (combined.isEmpty()) {
                  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(Icons.Filled.Public, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                      Spacer(Modifier.height(6.dp))
                      Text("Belum ada room aktif. Buat room kota pertama!", color = TextSecondary)
                    }
                  }
                } else {
                  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(combined, key = { it.id }) { room ->
                      RoomListRow(
                        room = room,
                        busy = busy,
                        onJoin = {
                          sessionVm.joinFirestoreRoom(room, profile, gameEngine, onEnterOnlineGame)
                        },
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Right: Join by Code + Rules Summary
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = ViceSurface),
            border = BorderStroke(1.dp, ViceCardBorder),
            shape = RoundedCornerShape(16.dp),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Gabung Kode Room", style = MaterialTheme.typography.titleLarge, color = Color.White)
              OutlinedTextField(
                value = joinCodeInput,
                onValueChange = { joinCodeInput = it },
                label = { Text("ID Room (contoh: room_ab12)") },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("join_code_input"),
              )
              Button(
                onClick = { sessionVm.joinRoomById(joinCodeInput, profile, gameEngine, onEnterOnlineGame) },
                enabled = !busy,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("join_by_code_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
              ) {
                Text("Gabung Server", fontWeight = FontWeight.Bold)
              }
            }
          }

          Card(
            colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
            border = BorderStroke(1.dp, ViceCardBorder),
            shape = RoundedCornerShape(16.dp),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = ViceEmerald, modifier = Modifier.size(18.dp))
                Text("Safe Zone Spawn", style = MaterialTheme.typography.titleMedium, color = ViceEmerald)
              }
              Text(
                "Area Safe House terlindungi dari PvP. Keluar zona hijau untuk bertarung bebas, kejaran polisi & balapan liar!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateRoomDialog(
      defaultName = "${profile.nickname}'s City",
      busy = busy,
      onDismiss = { showCreateDialog = false },
      onCreate = { name, vis, maxP, pvp ->
        showCreateDialog = false
        sessionVm.createAndJoinRoom(name, vis, maxP, pvp, profile, gameEngine, onEnterOnlineGame)
      },
    )
  }
}

@Composable
private fun RoomListRow(
  room: MultiplayerRoomDoc,
  busy: Boolean,
  onJoin: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
    border = BorderStroke(1.dp, ViceCardBorder),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = !busy, onClick = onJoin),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (room.visibility == "PRIVATE") {
            Icon(Icons.Filled.Lock, contentDescription = "Private", tint = ViceAmber, modifier = Modifier.size(15.dp))
          }
          Text(room.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Text(
          "ID: ${room.id} • ${if (room.pvpEnabled) "PvP Aktif (Safe Zone Spawn)" else "Mode Damai"}",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(Icons.Filled.Group, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
          Text("${room.playerCount}/${room.maxPlayers}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
        }
        Button(
          onClick = onJoin,
          enabled = !busy,
          modifier = Modifier.height(40.dp),
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
        ) {
          Text("Masuk")
        }
      }
    }
  }
}

@Composable
private fun CreateRoomDialog(
  defaultName: String,
  busy: Boolean,
  onDismiss: () -> Unit,
  onCreate: (name: String, visibility: String, maxPlayers: Int, pvpEnabled: Boolean) -> Unit,
) {
  var name by remember { mutableStateOf(defaultName.take(28)) }
  var isPublic by remember { mutableStateOf(true) }
  var maxPlayers by remember { mutableFloatStateOf(24f) }
  var pvpEnabled by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ViceSurface,
    title = { Text("Buat Server Kota Baru", style = MaterialTheme.typography.headlineMedium, color = Color.White) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 32) name = it },
          label = { Text("Nama Server") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Room Publik (Tampil di Daftar)", color = Color.White)
          Switch(checked = isPublic, onCheckedChange = { isPublic = it })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Aktifkan PvP (Safe Zone Terlindung)", color = Color.White)
          Switch(checked = pvpEnabled, onCheckedChange = { pvpEnabled = it })
        }

        Text("Kapasitas Pemain: ${maxPlayers.toInt()} Pemain", color = NeonCyan)
        Slider(
          value = maxPlayers,
          onValueChange = { maxPlayers = it },
          valueRange = 16f..32f,
          steps = 15,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(name, if (isPublic) "PUBLIC" else "PRIVATE", maxPlayers.toInt(), pvpEnabled) },
        enabled = !busy,
        modifier = Modifier.testTag("confirm_create_room_button"),
        colors = ButtonDefaults.buttonColors(containerColor = SunsetCoral, contentColor = Color.White),
      ) {
        Text("Buat & Masuk")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}
