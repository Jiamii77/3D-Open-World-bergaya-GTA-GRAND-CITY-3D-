package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.ApkExportManager
import com.example.game.CharacterPalettes
import com.example.game.GraphicsQuality
import com.example.game.PlayerProfileState
import kotlinx.coroutines.launch
import java.util.Locale
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

@Composable
fun MainMenuScreen(
  profile: PlayerProfileState,
  signedInEmail: String?,
  releaseUrl: String,
  onPlaySingleplayer: () -> Unit,
  onOpenOnlineLobby: () -> Unit,
  onOpenLeaderboard: () -> Unit,
  onSaveCustomization: (nickname: String, skinIdx: Int, outfitIdx: Int) -> Unit,
  onSaveSettings: (quality: GraphicsQuality, sensitivity: Float, volume: Float) -> Unit,
  onSaveReleaseUrl: (url: String) -> Unit,
) {
  var showCustomize by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var showSystemApkDialog by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ViceMidnight)
  ) {
    // Full-bleed twilight metropolis hero backdrop
    Image(
      painter = painterResource(id = R.drawable.img_menu_hero_1795016619297),
      contentDescription = "Vice Roam twilight city skyline",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    // Atmospheric gradient scrim for high contrast readability
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              Color(0xEE0A0E17),
              Color(0xCC0A0E17),
              Color(0x660A0E17),
            )
          )
        )
    )

    Row(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left Column: Brand Header + Player Card + Customization/Settings buttons
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Surface(
            color = NeonCyan.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
          ) {
            Text(
              text = stringResource(R.string.tagline_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = NeonCyan,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }

          Spacer(Modifier.height(8.dp))

          Text(
            text = "VICE ROAM",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
          )

          Text(
            text = "Kota 3D Open-World • Siklus Siang-Malam • 5-Star Wanted AI",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }

        Spacer(Modifier.height(12.dp))

        // Player Identity Summary Card
        Card(
          colors = CardDefaults.cardColors(containerColor = ViceSurface.copy(alpha = 0.90f)),
          border = BorderStroke(1.dp, ViceCardBorder),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // Character color avatar preview
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CharacterPalettes.outfitColors[profile.outfitColorIndex.coerceIn(0, 7)])
                .border(3.dp, CharacterPalettes.skinColors[profile.skinColorIndex.coerceIn(0, 5)], CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Avatar karakter",
                tint = ViceMidnight,
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = profile.nickname,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = if (signedInEmail != null) "Online: $signedInEmail" else "Profil Lokal • Offline Siap",
                style = MaterialTheme.typography.bodyMedium,
                color = if (signedInEmail != null) ViceEmerald else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "$${profile.cash}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.labelLarge.fontFamily),
                color = ViceEmerald,
              )
              Text(
                text = profile.graphicsQuality.name,
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { showCustomize = true },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("customize_character_button"),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
          ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.btn_customize), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }

          OutlinedButton(
            onClick = onOpenLeaderboard,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("open_leaderboard_button"),
            border = BorderStroke(1.dp, ViceAmber.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ViceAmber),
          ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.btn_leaderboard), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }

          OutlinedButton(
            onClick = { showSettings = true },
            modifier = Modifier
              .weight(0.9f)
              .height(48.dp)
              .testTag("settings_button"),
            border = BorderStroke(1.dp, ViceCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
          ) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.btn_settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      // Right Column: Game Mode Cards + System Update & APK Export
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
      ) {
        ModeLaunchCard(
          title = stringResource(R.string.mode_singleplayer),
          description = stringResource(R.string.mode_singleplayer_desc),
          badge = "60 FPS • OFFLINE",
          accent = NeonCyan,
          icon = Icons.Filled.SportsEsports,
          testTag = "play_singleplayer_button",
          onClick = onPlaySingleplayer,
        )

        Spacer(Modifier.height(12.dp))

        ModeLaunchCard(
          title = stringResource(R.string.mode_multiplayer),
          description = stringResource(R.string.mode_multiplayer_desc),
          badge = "16-32 PEMAIN • LOBBY & PVP",
          accent = SunsetCoral,
          icon = Icons.Filled.Public,
          testTag = "play_online_button",
          onClick = onOpenOnlineLobby,
        )

        Spacer(Modifier.height(12.dp))

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("open_system_apk_button")
            .clickable { showSystemApkDialog = true },
          colors = CardDefaults.cardColors(containerColor = ViceSurface.copy(alpha = 0.88f)),
          border = BorderStroke(1.dp, ViceEmerald.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(14.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = ViceEmerald,
                modifier = Modifier.size(22.dp),
              )
              Column {
                Text(
                  text = stringResource(R.string.btn_system_apk),
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                )
                Text(
                  text = "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Ekspor APK & Pembaruan",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Surface(
              color = ViceEmerald.copy(alpha = 0.16f),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, ViceEmerald.copy(alpha = 0.5f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = ViceEmerald, modifier = Modifier.size(16.dp))
                Text("APK", style = MaterialTheme.typography.labelLarge, color = ViceEmerald)
              }
            }
          }
        }
      }
    }
  }

  if (showCustomize) {
    CustomizeCharacterDialog(
      profile = profile,
      onDismiss = { showCustomize = false },
      onSave = { nick, skin, outfit ->
        onSaveCustomization(nick, skin, outfit)
        showCustomize = false
      },
    )
  }

  if (showSettings) {
    SettingsDialog(
      profile = profile,
      onDismiss = { showSettings = false },
      onSave = { q, sens, vol ->
        onSaveSettings(q, sens, vol)
        showSettings = false
      },
    )
  }

  if (showSystemApkDialog) {
    SystemAndApkDialog(
      savedReleaseUrl = releaseUrl,
      onSaveReleaseUrl = onSaveReleaseUrl,
      onDismiss = { showSystemApkDialog = false },
    )
  }
}

@Composable
private fun ModeLaunchCard(
  title: String,
  description: String,
  badge: String,
  accent: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  testTag: String,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated.copy(alpha = 0.92f)),
    border = BorderStroke(1.5.dp, accent.copy(alpha = 0.75f)),
    shape = RoundedCornerShape(18.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier
          .size(54.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(accent.copy(alpha = 0.18f))
          .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(28.dp))
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(text = badge, style = MaterialTheme.typography.labelSmall, color = accent)
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
    }
  }
}

@Composable
private fun CustomizeCharacterDialog(
  profile: PlayerProfileState,
  onDismiss: () -> Unit,
  onSave: (String, Int, Int) -> Unit,
) {
  var nickname by remember { mutableStateOf(profile.nickname) }
  var skinIdx by remember { mutableIntStateOf(profile.skinColorIndex) }
  var outfitIdx by remember { mutableIntStateOf(profile.outfitColorIndex) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ViceSurface,
    title = { Text("Kustomisasi Karakter", style = MaterialTheme.typography.headlineMedium, color = Color.White) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = nickname,
          onValueChange = { if (it.length <= 20) nickname = it },
          label = { Text("Nickname Pemain") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("nickname_input"),
        )

        Text("Warna Kulit (Skin Tone)", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CharacterPalettes.skinColors.forEachIndexed { idx, col ->
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(col)
                .border(if (skinIdx == idx) 3.dp else 1.dp, if (skinIdx == idx) NeonCyan else Color.White.copy(alpha = 0.3f), CircleShape)
                .clickable { skinIdx = idx },
            )
          }
        }

        Text("Warna Pakaian (Outfit)", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CharacterPalettes.outfitColors.forEachIndexed { idx, col ->
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(col)
                .border(if (outfitIdx == idx) 3.dp else 1.dp, if (outfitIdx == idx) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { outfitIdx = idx },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(nickname, skinIdx, outfitIdx) },
        modifier = Modifier.testTag("save_customization_button"),
        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
      ) {
        Text("Simpan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@Composable
private fun SettingsDialog(
  profile: PlayerProfileState,
  onDismiss: () -> Unit,
  onSave: (GraphicsQuality, Float, Float) -> Unit,
) {
  var quality by remember { mutableStateOf(profile.graphicsQuality) }
  var sensitivity by remember { mutableFloatStateOf(profile.cameraSensitivity) }
  var volume by remember { mutableFloatStateOf(profile.audioVolume) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ViceSurface,
    title = { Text("Pengaturan Game", style = MaterialTheme.typography.headlineMedium, color = Color.White) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Kualitas Grafik 3D", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GraphicsQuality.entries.forEach { q ->
            FilterChip(
              selected = quality == q,
              onClick = { quality = q },
              label = { Text(q.name) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                selectedLabelColor = NeonCyan,
              ),
            )
          }
        }

        Text("Sensitivitas Kamera: ${"%.1f".format(sensitivity)}x", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Slider(
          value = sensitivity,
          onValueChange = { sensitivity = it },
          valueRange = 0.4f..2.2f,
          colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = SunsetCoral, modifier = Modifier.size(18.dp))
          Text("Volume Audio: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Slider(
          value = volume,
          onValueChange = { volume = it },
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(thumbColor = SunsetCoral, activeTrackColor = SunsetCoral),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(quality, sensitivity, volume) },
        modifier = Modifier.testTag("save_settings_button"),
        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
      ) {
        Text("Terapkan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    },
  )
}

@Composable
private fun SystemAndApkDialog(
  savedReleaseUrl: String,
  onSaveReleaseUrl: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val apkInfo = remember(context) { ApkExportManager.getInstalledApkInfo(context) }

  var isExporting by remember { mutableStateOf(false) }
  var exportStatus by remember { mutableStateOf<String?>(null) }
  var exportError by remember { mutableStateOf(false) }

  var releaseUrlInput by remember(savedReleaseUrl) { mutableStateOf(savedReleaseUrl) }
  var updateLinkError by remember { mutableStateOf<String?>(null) }

  val saveApkLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
  ) { destinationUri ->
    if (destinationUri == null) return@rememberLauncherForActivityResult
    isExporting = true
    exportStatus = null
    scope.launch {
      val result = ApkExportManager.exportInstalledApkToUri(context, destinationUri)
      isExporting = false
      result.fold(
        onSuccess = { bytes ->
          exportError = false
          val mb = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
          exportStatus = "File APK berhasil disimpan ($mb)."
        },
        onFailure = { err ->
          exportError = true
          exportStatus = err.localizedMessage ?: "Gagal mengekspor file APK."
        },
      )
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = ViceSurface,
    title = {
      Text(
        text = stringResource(R.string.system_apk_title),
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Section 1: Save Installed Game as .APK via Storage Access Framework
        Card(
          colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
          border = BorderStroke(1.dp, ViceEmerald.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Ekspor / Unduh Paket Game (.APK)",
              style = MaterialTheme.typography.titleMedium,
              color = ViceEmerald,
            )
            Text(
              text = "Versi: v${apkInfo.versionName} (Build ${apkInfo.versionCode}) • ${apkInfo.formattedSizeMb}\nPaket: ${apkInfo.applicationId}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )

            if (exportStatus != null) {
              Text(
                text = exportStatus!!,
                style = MaterialTheme.typography.bodyMedium,
                color = if (exportError) ViceCrimson else ViceEmerald,
              )
            }

            Button(
              onClick = {
                saveApkLauncher.launch("ViceRoam-v${apkInfo.versionName}.apk")
              },
              enabled = apkInfo.isSourceAvailable && !isExporting,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_apk_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = ViceEmerald,
                contentColor = ViceMidnight,
              ),
            ) {
              if (isExporting) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = ViceMidnight,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_save_apk), fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        HorizontalDivider(color = ViceCardBorder)

        // Section 2: Compliant External Game System Update Link (ACTION_VIEW)
        Text(
          text = "Pembaruan Sistem Game (Rilis Eksternal / GitHub)",
          style = MaterialTheme.typography.titleMedium,
          color = NeonCyan,
        )
        Text(
          text = "Catatan sistem saat ini: v${apkInfo.versionName} (${apkInfo.buildType}) mencakup Kota 3D OpenGL ES, Online Free Roam, dan Papan Skor Top 10.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )

        OutlinedTextField(
          value = releaseUrlInput,
          onValueChange = {
            releaseUrlInput = it
            updateLinkError = null
          },
          label = { Text("URL Rilis Resmi (contoh: https://github.com/.../releases)") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("release_url_input"),
        )

        if (updateLinkError != null) {
          Text(updateLinkError!!, style = MaterialTheme.typography.bodyMedium, color = ViceCrimson)
        }

        val trimmedUrl = releaseUrlInput.trim()
        val isValidHttpUrl = trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")

        OutlinedButton(
          onClick = {
            if (!isValidHttpUrl) {
              updateLinkError = "Masukkan URL valid yang diawali https:// atau http://"
              return@OutlinedButton
            }
            onSaveReleaseUrl(trimmedUrl)
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl))
              context.startActivity(intent)
            } catch (e: Exception) {
              updateLinkError = e.localizedMessage ?: "Tidak dapat membuka browser."
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("open_update_url_button"),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        ) {
          Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.btn_open_update_link))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Tutup") }
    },
  )
}

