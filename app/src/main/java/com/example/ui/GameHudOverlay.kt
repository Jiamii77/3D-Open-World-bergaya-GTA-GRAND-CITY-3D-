package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.BlipKind
import com.example.game.GameEngine
import com.example.game.HudState
import com.example.game.WeaponCategory
import com.example.game.WorldMarkerType
import com.example.ui.theme.HudGlass
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PoliceBlue
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceAmber
import com.example.ui.theme.ViceCardBorder
import com.example.ui.theme.ViceCrimson
import com.example.ui.theme.ViceEmerald
import com.example.ui.theme.ViceMidnight
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GameHudOverlay(
  hud: HudState,
  engine: GameEngine,
  cameraSensitivity: Float,
  onOpenPause: () -> Unit,
  onOpenWeaponWheel: () -> Unit,
  onOpenMarkerMenu: (WorldMarkerType) -> Unit,
  onOpenChatModal: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    // 1. Right-side 360° Touch-Drag Camera Zone (behind buttons)
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(0.58f)
        .align(Alignment.CenterEnd)
        .pointerInput(cameraSensitivity) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            engine.player.rotateCamera(dragAmount.x, dragAmount.y, cameraSensitivity)
          }
        }
    )

    // 2. Main Safe-Area HUD Container
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      // --- TOP-LEFT: Circular Radar / Minimap + Online Chat Feed ---
      Column(
        modifier = Modifier.align(Alignment.TopStart),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularMinimap(hud = hud)

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hud.inSafeZone) {
              Surface(
                color = ViceEmerald.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, ViceEmerald),
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Filled.Shield, contentDescription = null, tint = ViceEmerald, modifier = Modifier.size(13.dp))
                  Text("SAFE ZONE", style = MaterialTheme.typography.labelSmall, color = ViceEmerald)
                }
              }
            }
            if (hud.isOnline) {
              Surface(
                color = HudGlass,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "${hud.onlineRoomName} • ${hud.onlinePlayerCount} Online",
                  style = MaterialTheme.typography.labelSmall,
                  color = NeonCyan,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }
        }

        // Compact Online Chat Feed
        if (hud.isOnline && hud.chatFeed.isNotEmpty()) {
          Surface(
            color = HudGlass,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.width(230.dp),
          ) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              hud.chatFeed.takeLast(3).forEach { msg ->
                Text(
                  text = if (msg.isEmote) "* ${msg.senderName} ${msg.message}" else "${msg.senderName}: ${msg.message}",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (msg.isEmote) ViceAmber else Color.White,
                )
              }
            }
          }
        }
      }

      // --- TOP-CENTER: Status Banner / Contextual Marker Prompt ---
      Column(
        modifier = Modifier.align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (hud.statusBanner != null) {
          Surface(
            color = HudGlass,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = hud.statusBanner,
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            )
          }
        }

        if (hud.nearMarker != null) {
          Surface(
            color = SunsetCoral,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .clickable { onOpenMarkerMenu(hud.nearMarker) }
              .testTag("marker_interact_button"),
          ) {
            Text(
              text = "Buka ${hud.nearMarker.label}",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
          }
        }
      }

      // --- TOP-RIGHT: Health, Armor, Cash, Wanted Stars, Weapon, Pause ---
      Row(
        modifier = Modifier.align(Alignment.TopEnd),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          color = HudGlass,
          border = BorderStroke(1.dp, ViceCardBorder),
          shape = RoundedCornerShape(14.dp),
        ) {
          Column(
            modifier = Modifier
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .width(190.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            // Cash & Clock + FPS
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "$${hud.cash}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.labelLarge.fontFamily),
                color = ViceEmerald,
              )
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                  imageVector = if (hud.isNight) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                  contentDescription = null,
                  tint = if (hud.isNight) NeonCyan else ViceAmber,
                  modifier = Modifier.size(14.dp),
                )
                val hr = hud.timeOfDayHours.toInt() % 24
                val mn = ((hud.timeOfDayHours - hr) * 60).toInt().coerceIn(0, 59)
                Text(
                  text = "%02d:%02d • %d FPS".format(hr, mn, hud.currentFps),
                  style = MaterialTheme.typography.labelSmall,
                  color = TextSecondary,
                )
              }
            }

            // Health Bar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Favorite, contentDescription = "Health", tint = ViceCrimson, modifier = Modifier.size(14.dp))
              LinearProgressIndicator(
                progress = { (hud.health / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                  .weight(1f)
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp)),
                color = ViceEmerald,
                trackColor = Color.White.copy(alpha = 0.16f),
              )
              Text("${hud.health}", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }

            // Armor Bar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Security, contentDescription = "Armor", tint = NeonCyan, modifier = Modifier.size(14.dp))
              LinearProgressIndicator(
                progress = { (hud.armor / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                  .weight(1f)
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp)),
                color = NeonCyan,
                trackColor = Color.White.copy(alpha = 0.16f),
              )
              Text("${hud.armor}", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }

            // Wanted Level Stars (1..5)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (star in 1..5) {
                  val lit = star <= hud.wantedStars
                  Icon(
                    Icons.Filled.Star,
                    contentDescription = "Wanted Star $star",
                    tint = if (lit) ViceAmber else Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              // Active Weapon & Ammo badge
              val ammoText = if (hud.activeWeapon == WeaponCategory.MELEE) {
                "MELEE"
              } else if (hud.isReloading) {
                "RELOAD..."
              } else {
                "${hud.activeWeapon.shortTag} ${hud.clipAmmo}/${hud.reserveAmmo}"
              }
              Text(ammoText, style = MaterialTheme.typography.labelSmall, color = SunsetCoral)
            }

            if (hud.wantedStars > 0 && hud.wantedCooldownRatio > 0f) {
              LinearProgressIndicator(
                progress = { hud.wantedCooldownRatio },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(3.dp)
                  .clip(RoundedCornerShape(2.dp)),
                color = PoliceBlue,
                trackColor = Color.Transparent,
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(
            color = HudGlass,
            border = BorderStroke(1.dp, ViceCardBorder),
            shape = CircleShape,
          ) {
            IconButton(
              onClick = onOpenPause,
              modifier = Modifier
                .size(48.dp)
                .testTag("pause_button"),
            ) {
              Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
            }
          }

          if (hud.isOnline) {
            Surface(
              color = HudGlass,
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
              shape = CircleShape,
            ) {
              IconButton(
                onClick = onOpenChatModal,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("quick_chat_button"),
              ) {
                Icon(Icons.Filled.ChatBubble, contentDescription = "Quick Chat & Emote", tint = NeonCyan)
              }
            }
          }
        }
      }

      // --- BOTTOM-LEFT: Virtual Joystick OR Driving Steering Controls ---
      Box(modifier = Modifier.align(Alignment.BottomStart)) {
        if (hud.inVehicle) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            HoldActionButton(
              label = "KIRI",
              icon = Icons.AutoMirrored.Filled.ArrowBack,
              accent = NeonCyan,
              sizeDp = 68,
              testTag = "steer_left_button",
              onHeldChange = { held ->
                engine.driveSteer = if (held) -1f else if (engine.driveSteer < 0f) 0f else engine.driveSteer
              },
            )
            HoldActionButton(
              label = "KANAN",
              icon = Icons.AutoMirrored.Filled.ArrowForward,
              accent = NeonCyan,
              sizeDp = 68,
              testTag = "steer_right_button",
              onHeldChange = { held ->
                engine.driveSteer = if (held) 1f else if (engine.driveSteer > 0f) 0f else engine.driveSteer
              },
            )
          }
        } else {
          VirtualJoystick(
            onMove = { x, y ->
              engine.joystickX = x
              engine.joystickY = y
            },
          )
        }
      }

      // --- BOTTOM-CENTER: Vehicle Speedometer + HP bar when driving ---
      if (hud.inVehicle) {
        Surface(
          modifier = Modifier.align(Alignment.BottomCenter),
          color = HudGlass,
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text("${hud.vehicleSpeedKmh} KM/H", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("• ${hud.vehicleType.displayName}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            LinearProgressIndicator(
              progress = { (hud.vehicleHp.toFloat() / hud.vehicleMaxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
              modifier = Modifier
                .width(64.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = if (hud.vehicleHp < hud.vehicleMaxHp * 0.35f) ViceCrimson else ViceEmerald,
              trackColor = Color.White.copy(alpha = 0.2f),
            )
          }
        }
      }

      // --- BOTTOM-RIGHT: Contextual Action Buttons ---
      Column(
        modifier = Modifier.align(Alignment.BottomEnd),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Contextual Enter/Exit Vehicle button (appears automatically near vehicle or while driving)
        if (hud.inVehicle || hud.nearVehicleType != null) {
          Surface(
            color = if (hud.inVehicle) SunsetCoral else ViceEmerald,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .clickable { engine.onToggleVehicle() }
              .testTag("enter_exit_vehicle_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                if (hud.inVehicle) Icons.AutoMirrored.Filled.ExitToApp else Icons.Filled.DirectionsCar,
                contentDescription = "Masuk atau keluar kendaraan",
                tint = ViceMidnight,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = if (hud.inVehicle) "Keluar" else "Naik ${hud.nearVehicleType?.displayName ?: "Mobil"}",
                style = MaterialTheme.typography.labelLarge,
                color = ViceMidnight,
              )
            }
          }
        }

        if (hud.inVehicle) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            ActionCircleButton(
              icon = Icons.Filled.Campaign,
              label = "KLAKSON",
              accent = ViceAmber,
              sizeDp = 52,
              testTag = "horn_button",
              onClick = { engine.onJumpOrHorn() },
            )
            HoldActionButton(
              label = "REM",
              icon = null,
              accent = ViceCrimson,
              sizeDp = 64,
              testTag = "brake_button",
              onHeldChange = { engine.driveBrake = it },
            )
            HoldActionButton(
              label = "GAS",
              icon = Icons.Filled.ArrowUpward,
              accent = ViceEmerald,
              sizeDp = 76,
              testTag = "gas_button",
              onHeldChange = { engine.driveThrottle = it },
            )
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            ActionCircleButton(
              icon = Icons.Filled.SwapHoriz,
              label = "WHEEL",
              accent = ViceAmber,
              sizeDp = 50,
              testTag = "weapon_wheel_button",
              onClick = onOpenWeaponWheel,
            )
            ActionCircleButton(
              icon = Icons.Filled.Refresh,
              label = "RELOAD",
              accent = NeonCyan,
              sizeDp = 50,
              testTag = "reload_button",
              onClick = { engine.onReload() },
            )
            ActionCircleButton(
              icon = Icons.Filled.ArrowUpward,
              label = "LOMPAT",
              accent = Color.White,
              sizeDp = 52,
              testTag = "jump_button",
              onClick = { engine.onJumpOrHorn() },
            )
            HoldActionButton(
              label = if (hud.hasAutoAimTarget) "LOCK!" else "TEMBAK",
              icon = Icons.Filled.GpsFixed,
              accent = if (hud.hasAutoAimTarget) ViceAmber else SunsetCoral,
              sizeDp = 78,
              testTag = "fire_button",
              onHeldChange = { engine.fireHeld = it },
            )
          }
        }
      }

      // Wasted Overlay Banner
      if (hud.isWasted) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "WASTED • RESPAWNING...",
            style = MaterialTheme.typography.displayLarge,
            color = ViceCrimson,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun CircularMinimap(hud: HudState) {
  Surface(
    shape = CircleShape,
    color = HudGlass,
    border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.7f)),
    modifier = Modifier.size(106.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
      val cx = size.width * 0.5f
      val cy = size.height * 0.5f
      val mapRadiusWorld = 90f
      val scale = (size.minDimension * 0.5f) / mapRadiusWorld

      // Safe zone circle
      val szOffsetX = (-24f - hud.playerX) * scale
      val szOffsetY = (24f - hud.playerZ) * scale
      drawCircle(
        color = ViceEmerald.copy(alpha = 0.22f),
        radius = 22f * scale,
        center = Offset(cx + szOffsetX, cy + szOffsetY),
      )

      // City Road Grid lines
      val roadCoords = floatArrayOf(-96f, -48f, 0f, 48f, 96f)
      for (rc in roadCoords) {
        val rx = cx + (rc - hud.playerX) * scale
        drawLine(Color.White.copy(alpha = 0.22f), Offset(rx, 0f), Offset(rx, size.height), strokeWidth = 3f)
        val rz = cy + (rc - hud.playerZ) * scale
        drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, rz), Offset(size.width, rz), strokeWidth = 3f)
      }

      // Blips (clamp inside radar circle)
      val maxR = size.minDimension * 0.45f
      for (blip in hud.minimapBlips) {
        var dx = (blip.x - hud.playerX) * scale
        var dy = (blip.z - hud.playerZ) * scale
        val d = sqrt(dx * dx + dy * dy)
        if (d > maxR && d > 0.01f) {
          dx = (dx / d) * maxR
          dy = (dy / d) * maxR
        }
        val pos = Offset(cx + dx, cy + dy)
        when (blip.kind) {
          BlipKind.REMOTE_PLAYER -> drawCircle(NeonCyan, 4.5f, pos)
          BlipKind.POLICE -> drawCircle(if ((System.currentTimeMillis() / 220L) % 2L == 0L) ViceCrimson else PoliceBlue, 4.2f, pos)
          BlipKind.SHOP -> drawRect(SunsetCoral, Offset(pos.x - 4f, pos.y - 4f), Size(8f, 8f))
          BlipKind.GARAGE -> drawRect(NeonCyan, Offset(pos.x - 4f, pos.y - 4f), Size(8f, 8f))
          BlipKind.SAFE_HOUSE -> drawRect(ViceEmerald, Offset(pos.x - 4f, pos.y - 4f), Size(8f, 8f))
          BlipKind.VEHICLE -> drawCircle(Color.White.copy(alpha = 0.6f), 2.5f, pos)
          BlipKind.NPC_HOSTILE -> drawCircle(ViceAmber, 3f, pos)
        }
      }

      // Player heading arrow at center
      rotate(degrees = hud.playerYawDeg, pivot = Offset(cx, cy)) {
        val arrow = Path().apply {
          moveTo(cx, cy + 7f)
          lineTo(cx - 5f, cy - 5f)
          lineTo(cx, cy - 2f)
          lineTo(cx + 5f, cy - 5f)
          close()
        }
        drawPath(arrow, Color.White)
      }
      drawCircle(Color.White.copy(alpha = 0.35f), size.minDimension * 0.48f, Offset(cx, cy), style = Stroke(1.5f))
    }
  }
}

@Composable
private fun VirtualJoystick(
  onMove: (Float, Float) -> Unit,
) {
  var knobX by remember { mutableFloatStateOf(0f) }
  var knobY by remember { mutableFloatStateOf(0f) }
  val maxRadiusPx = 96f

  Box(
    modifier = Modifier
      .size(134.dp)
      .clip(CircleShape)
      .background(HudGlass)
      .border(1.5.dp, NeonCyan.copy(alpha = 0.45f), CircleShape)
      .testTag("virtual_joystick")
      .pointerInput(Unit) {
        detectDragGestures(
          onDragEnd = {
            knobX = 0f; knobY = 0f
            onMove(0f, 0f)
          },
          onDragCancel = {
            knobX = 0f; knobY = 0f
            onMove(0f, 0f)
          },
          onDrag = { change, dragAmount ->
            change.consume()
            var nx = knobX + dragAmount.x
            var ny = knobY + dragAmount.y
            val dist = sqrt(nx * nx + ny * ny)
            if (dist > maxRadiusPx) {
              nx = (nx / dist) * maxRadiusPx
              ny = (ny / dist) * maxRadiusPx
            }
            knobX = nx
            knobY = ny
            // Invert Y so dragging UP produces positive forward movement
            onMove(nx / maxRadiusPx, -ny / maxRadiusPx)
          },
        )
      },
    contentAlignment = Alignment.Center,
  ) {
    // Inner sprint threshold guide ring
    Box(
      modifier = Modifier
        .size(94.dp)
        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
    )
    // Draggable thumb
    Box(
      modifier = Modifier
        .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
        .size(50.dp)
        .clip(CircleShape)
        .background(NeonCyan.copy(alpha = 0.85f))
        .border(2.dp, Color.White, CircleShape),
    )
  }
}

@Composable
private fun ActionCircleButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accent: Color,
  sizeDp: Int,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    color = HudGlass,
    border = BorderStroke(1.5.dp, accent.copy(alpha = 0.8f)),
    shape = CircleShape,
    modifier = Modifier
      .size(sizeDp.dp)
      .clip(CircleShape)
      .clickable(onClick = onClick)
      .testTag(testTag),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
private fun HoldActionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  accent: Color,
  sizeDp: Int,
  testTag: String,
  onHeldChange: (Boolean) -> Unit,
) {
  var pressed by remember { mutableStateOf(false) }

  Surface(
    color = if (pressed) accent.copy(alpha = 0.85f) else HudGlass,
    border = BorderStroke(2.dp, accent),
    shape = CircleShape,
    modifier = Modifier
      .size(sizeDp.dp)
      .testTag(testTag)
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            val isDown = event.changes.any { it.pressed }
            if (isDown != pressed) {
              pressed = isDown
              onHeldChange(isDown)
            }
            if (event.type == PointerEventType.Release || !isDown) {
              if (pressed) {
                pressed = false
                onHeldChange(false)
              }
            }
          }
        }
      },
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = label, tint = if (pressed) ViceMidnight else accent, modifier = Modifier.size(24.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (pressed) ViceMidnight else Color.White,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
