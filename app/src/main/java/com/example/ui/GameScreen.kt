package com.example.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.CityRenderer
import com.example.game.GameEngine
import com.example.game.PlayerProfileState
import com.example.game.VehicleType
import com.example.game.WeaponCategory
import com.example.game.WorldMarkerType
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
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
  engine: GameEngine,
  profile: PlayerProfileState,
  onlineSessionVm: OnlineSessionViewModel?,
  onExitToMenu: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val hud by engine.hudState.collectAsStateWithLifecycle()

  var showPause by remember { mutableStateOf(false) }
  var showWeaponWheel by remember { mutableStateOf(false) }
  var activeMarkerModal by remember { mutableStateOf<WorldMarkerType?>(null) }
  var showChatModal by remember { mutableStateOf(false) }

  BackHandler {
    when {
      activeMarkerModal != null -> activeMarkerModal = null
      showWeaponWheel -> showWeaponWheel = false
      showChatModal -> showChatModal = false
      else -> showPause = !showPause
    }
  }

  val glView = remember {
    GLSurfaceView(context).apply {
      setEGLContextClientVersion(2)
      preserveEGLContextOnPause = true
      setRenderer(CityRenderer(engine))
      renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
  }

  DisposableEffect(lifecycleOwner, glView) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) glView.onResume()
      if (event == Lifecycle.Event.ON_PAUSE) glView.onPause()
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      glView.onResume()
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(obs)
      glView.onPause()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      factory = { glView },
      modifier = Modifier.fillMaxSize(),
    )

    GameHudOverlay(
      hud = hud,
      engine = engine,
      cameraSensitivity = profile.cameraSensitivity,
      onOpenPause = { showPause = true },
      onOpenWeaponWheel = { showWeaponWheel = true },
      onOpenMarkerMenu = { marker -> activeMarkerModal = marker },
      onOpenChatModal = { showChatModal = true },
    )
  }

  // 1. Weapon Wheel (5 Categories)
  if (showWeaponWheel) {
    AlertDialog(
      onDismissRequest = { showWeaponWheel = false },
      containerColor = ViceSurface,
      title = { Text("Weapon Wheel (5 Kategori)", color = Color.White) },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          WeaponCategory.entries.forEach { cat ->
            val unlocked = cat in hud.unlockedWeapons
            val selected = cat == hud.activeWeapon
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (selected) NeonCyan.copy(alpha = 0.22f) else ViceSurfaceElevated
              ),
              border = BorderStroke(1.dp, if (selected) NeonCyan else ViceCardBorder),
              modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = unlocked) {
                  engine.onSelectWeapon(cat)
                  showWeaponWheel = false
                },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column {
                  Text(cat.displayName, color = if (unlocked) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
                  Text("DMG ${cat.damage} • Range ${cat.range.toInt()}m", color = TextSecondary)
                }
                Text(
                  text = if (!unlocked) "Terkunci (Toko)" else if (selected) "AKTIF" else "PILIH",
                  color = if (!unlocked) ViceCrimson else if (selected) NeonCyan else ViceEmerald,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showWeaponWheel = false }) { Text("Tutup") }
      },
    )
  }

  // 2. World Marker Modals (Weapon Shop, Garage, Safe House)
  activeMarkerModal?.let { marker ->
    when (marker) {
      WorldMarkerType.WEAPON_SHOP -> {
        AlertDialog(
          onDismissRequest = { activeMarkerModal = null },
          containerColor = ViceSurface,
          title = { Text("Ammu-Nation • Toko Senjata ($${hud.cash})", color = SunsetCoral) },
          text = {
            Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              WeaponCategory.entries.filter { it != WeaponCategory.MELEE }.forEach { cat ->
                val unlocked = cat in hud.unlockedWeapons
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(cat.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Damage ${cat.damage} • Mag ${cat.magSize}", color = TextSecondary)
                  }
                  Button(
                    onClick = { engine.buyOrRefillWeapon(cat) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (unlocked) NeonCyan else SunsetCoral,
                      contentColor = ViceMidnight,
                    ),
                  ) {
                    Text(if (unlocked) "Isi Peluru $${cat.ammoPrice}" else "Beli $${cat.unlockPrice}")
                  }
                }
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { activeMarkerModal = null }) { Text("Selesai") }
          },
        )
      }

      WorldMarkerType.GARAGE -> {
        AlertDialog(
          onDismissRequest = { activeMarkerModal = null },
          containerColor = ViceSurface,
          title = { Text("Garasi Kendaraan Vice Roam", color = NeonCyan) },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              listOf(VehicleType.SEDAN, VehicleType.SUV, VehicleType.MOTO).forEach { vType ->
                Button(
                  onClick = {
                    engine.spawnVehicleFromGarage(vType)
                    activeMarkerModal = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.buttonColors(containerColor = ViceSurfaceElevated, contentColor = Color.White),
                  border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                ) {
                  Text("Keluarkan ${vType.displayName} (HP ${vType.maxHp})")
                }
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { activeMarkerModal = null }) { Text("Tutup") }
          },
        )
      }

      WorldMarkerType.SAFE_HOUSE -> {
        AlertDialog(
          onDismissRequest = { activeMarkerModal = null },
          containerColor = ViceSurface,
          title = { Text("Safe House • Zona Aman", color = ViceEmerald) },
          text = {
            Text(
              "Pulihkan Health & Armor ke 100%, hapus Wanted Level Polisi, dan simpan progres Anda.",
              color = TextSecondary,
            )
          },
          confirmButton = {
            Button(
              onClick = {
                engine.useSafeHouse()
                activeMarkerModal = null
              },
              colors = ButtonDefaults.buttonColors(containerColor = ViceEmerald, contentColor = ViceMidnight),
            ) {
              Text("Pulihkan & Simpan")
            }
          },
          dismissButton = {
            TextButton(onClick = { activeMarkerModal = null }) { Text("Batal") }
          },
        )
      }
    }
  }

  // 3. Online Quick Chat & Emotes
  if (showChatModal && onlineSessionVm != null) {
    var customMsg by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showChatModal = false },
      containerColor = ViceSurface,
      title = { Text("Quick Chat & Emote Online", color = NeonCyan) },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val quickEmotes = listOf(
            "Melambaikan tangan 👋",
            "Ayo balapan liar di jembatan!",
            "Butuh tumpangan mobil!",
            "Ketemu di Safe House",
          )
          quickEmotes.forEach { text ->
            OutlinedButton(
              onClick = {
                onlineSessionVm.sendChatOrEmote(profile.nickname, text, isEmote = true)
                showChatModal = false
              },
              modifier = Modifier.fillMaxWidth(),
              border = BorderStroke(1.dp, ViceCardBorder),
            ) {
              Text(text, color = Color.White)
            }
          }
          OutlinedTextField(
            value = customMsg,
            onValueChange = { if (it.length <= 100) customMsg = it },
            label = { Text("Pesan Chat") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (customMsg.isNotBlank()) {
              onlineSessionVm.sendChatOrEmote(profile.nickname, customMsg, isEmote = false)
            }
            showChatModal = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
        ) {
          Text("Kirim")
        }
      },
      dismissButton = {
        TextButton(onClick = { showChatModal = false }) { Text("Tutup") }
      },
    )
  }

  // 4. Pause Menu
  if (showPause) {
    AlertDialog(
      onDismissRequest = { showPause = false },
      containerColor = ViceSurface,
      title = { Text("Game Dijeda", color = Color.White, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Mode: ${if (hud.isOnline) "Online Free Roam (${hud.onlineRoomName})" else "Singleplayer (Offline)"}", color = TextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { engine.timeOfDayHours = 12.0f; showPause = false }) {
              Text("Siang", color = ViceAmber)
            }
            OutlinedButton(onClick = { engine.timeOfDayHours = 21.5f; showPause = false }) {
              Text("Malam Neon", color = NeonCyan)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showPause = false },
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
        ) {
          Text("Lanjutkan")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            showPause = false
            scope.launch {
              onlineSessionVm?.leaveCurrentRoom()
              onExitToMenu()
            }
          },
          modifier = Modifier.testTag("exit_game_button"),
          border = BorderStroke(1.dp, ViceCrimson),
        ) {
          Text("Keluar ke Menu", color = ViceCrimson)
        }
      },
    )
  }
}
