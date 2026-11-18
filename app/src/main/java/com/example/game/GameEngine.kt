package com.example.game

import com.example.engine.MathUtil
import com.example.engine.SoundEngine
import com.example.network.RemotePlayerInterpolator
import com.example.network.RemotePlayerRenderState
import com.example.network.RoomPlayerDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GameEngine {
  val map = MapGenerator()
  val weaponSystem = WeaponSystem()
  val vehicleSystem = VehicleSystem(map)
  val player = PlayerController(map)
  val npcAi = NPCAI(map, vehicleSystem, weaponSystem)
  val soundEngine = SoundEngine()
  val interpolator = RemotePlayerInterpolator()

  // Input state updated from touch UI
  @Volatile var joystickX: Float = 0f
  @Volatile var joystickY: Float = 0f
  @Volatile var driveThrottle: Boolean = false
  @Volatile var driveBrake: Boolean = false
  @Volatile var driveSteer: Float = 0f
  @Volatile var fireHeld: Boolean = false

  // Session configuration
  @Volatile var profile: PlayerProfileState = PlayerProfileState()
  @Volatile var isOnlineMode: Boolean = false
  @Volatile var onlineRoomId: String = ""
  @Volatile var onlineRoomName: String = ""
  @Volatile var pvpEnabled: Boolean = true
  @Volatile var localUserId: String = "local"

  // Outbound multiplayer callbacks
  var onSyncLocalPlayer: ((RoomPlayerDoc) -> Unit)? = null
  var onSendPvpHit: ((targetUid: String, damage: Int) -> Unit)? = null
  var onSaveProfileProgress: ((cash: Int, unlocked: Set<WeaponCategory>) -> Unit)? = null

  // Day/Night cycle: starts at golden hour (17.5h) so user sees transition into neon night quickly!
  @Volatile var timeOfDayHours: Float = 17.5f

  private val _hudState = MutableStateFlow(HudState())
  val hudState: StateFlow<HudState> = _hudState.asStateFlow()

  private val _chatFeed = ArrayList<ChatFeedItem>()
  private var cash: Int = 1250
  private var statusBanner: String? = null
  private var statusBannerTimer: Float = 0f
  private var lastRemoteShotSeqs = HashMap<String, Long>()
  private var lastSyncMs = 0L
  private var lastHudEmitMs = 0L
  private var fpsAccumFrames = 0
  private var fpsTimer = 0f
  private var measuredFps = 60

  @Volatile var currentAutoAimTarget: AutoAimTarget? = null
    private set

  fun startSession(
    profileState: PlayerProfileState,
    online: Boolean,
    roomId: String = "",
    roomName: String = "",
    pvp: Boolean = true,
    uid: String = "local",
  ) {
    profile = profileState
    cash = profileState.cash
    weaponSystem.syncUnlocked(profileState.unlockedWeapons)
    soundEngine.volume = profileState.audioVolume
    isOnlineMode = online
    onlineRoomId = roomId
    onlineRoomName = roomName
    pvpEnabled = pvp
    localUserId = uid
    timeOfDayHours = 17.6f

    interpolator.clear()
    lastRemoteShotSeqs.clear()
    _chatFeed.clear()
    player.resetToSafeHouse()
    npcAi.clearWanted()
    vehicleSystem.spawnInitialFleet()
    npcAi.populateCity(profileState.graphicsQuality)
    showBanner(if (online) "Terhubung ke $roomName" else "Mode Singleplayer Siap")
    emitHud(System.currentTimeMillis())
  }

  fun updateProfileSettings(updated: PlayerProfileState) {
    profile = updated
    soundEngine.volume = updated.audioVolume
  }

  fun pushChatItem(item: ChatFeedItem) {
    synchronized(_chatFeed) {
      _chatFeed.add(item)
      if (_chatFeed.size > 24) _chatFeed.removeAt(0)
    }
  }

  fun applyIncomingPvpDamage(damage: Int, attackerName: String) {
    if (!isOnlineMode || !pvpEnabled) return
    val inSafe = map.isInsideSafeZone(player.x, player.z)
    if (inSafe) return
    player.applyDamage(damage, inSafeZone = false)
    showBanner("Diserang oleh $attackerName (-$damage HP)")
  }

  fun showBanner(msg: String, durationSec: Float = 3.0f) {
    statusBanner = msg
    statusBannerTimer = durationSec
  }

  fun onJumpOrHorn() {
    if (player.activeVehicleId != null) {
      soundEngine.playHorn()
    } else {
      player.jump()
    }
  }

  fun onReload() {
    if (weaponSystem.startReload()) {
      showBanner("Reloading ${weaponSystem.activeWeapon.shortTag}...", 1.1f)
    }
  }

  fun onCycleWeapon() {
    val next = weaponSystem.cycleNextWeapon()
    showBanner("Senjata: ${next.displayName}", 1.4f)
  }

  fun onSelectWeapon(cat: WeaponCategory) {
    weaponSystem.selectWeapon(cat)
  }

  fun onToggleVehicle() {
    val currentId = player.activeVehicleId
    if (currentId != null) {
      val v = vehicleSystem.getById(currentId)
      player.activeVehicleId = null
      if (v != null) {
        val rad = v.yawDeg * MathUtil.DEG_TO_RAD
        val exitX = v.x - cos(rad) * (v.type.halfWidth + 1.0f)
        val exitZ = v.z + sin(rad) * (v.type.halfWidth + 1.0f)
        val (rx, rz) = map.resolveCollision(exitX, v.y, exitZ, 0.55f)
        player.x = rx
        player.z = rz
        player.y = v.y
      }
      return
    }
    val near = vehicleSystem.findNearestEnterable(player.x, player.y, player.z)
    if (near != null) {
      player.activeVehicleId = near.id
      player.speed = 0f
      player.animState = "DRIVE"
      showBanner("Mengendarai ${near.type.displayName}", 1.8f)
    }
  }

  fun buyOrRefillWeapon(cat: WeaponCategory): Boolean {
    if (cat !in weaponSystem.unlockedWeapons) {
      if (cash < cat.unlockPrice) {
        showBanner("Uang tidak cukup (butuh $${cat.unlockPrice})")
        return false
      }
      cash -= cat.unlockPrice
      weaponSystem.unlockedWeapons.add(cat)
      weaponSystem.selectWeapon(cat)
      weaponSystem.refillAllAmmo()
      soundEngine.playPickup()
      onSaveProfileProgress?.invoke(cash, weaponSystem.unlockedWeapons.toSet())
      showBanner("${cat.displayName} dibeli!")
      return true
    } else {
      if (cat.ammoPrice > 0 && cash >= cat.ammoPrice) {
        cash -= cat.ammoPrice
        weaponSystem.addAmmo(cat, cat.magSize * 2)
        soundEngine.playPickup()
        onSaveProfileProgress?.invoke(cash, weaponSystem.unlockedWeapons.toSet())
        showBanner("Peluru ${cat.shortTag} diisi ulang")
        return true
      }
      return false
    }
  }

  fun spawnVehicleFromGarage(type: VehicleType) {
    val v = vehicleSystem.spawnGarageVehicle(type)
    player.activeVehicleId = v.id
    player.x = v.x
    player.y = v.y
    player.z = v.z
    soundEngine.playHorn()
    showBanner("${type.displayName} keluar dari Garasi!")
  }

  fun useSafeHouse() {
    player.healFull()
    npcAi.clearWanted()
    soundEngine.playPickup()
    onSaveProfileProgress?.invoke(cash, weaponSystem.unlockedWeapons.toSet())
    showBanner("HP & Armor Penuh • Wanted Dibersihkan")
  }

  fun stepSimulation(rawDt: Float) {
    val dt = rawDt.coerceIn(0.001f, 0.05f)
    val nowMs = System.currentTimeMillis()

    // FPS tracking
    fpsAccumFrames++
    fpsTimer += dt
    if (fpsTimer >= 0.5f) {
      measuredFps = (fpsAccumFrames / fpsTimer).toInt().coerceIn(1, 120)
      fpsAccumFrames = 0
      fpsTimer = 0f
    }

    // Day/Night progression (full 24h cycle ~ 4 minutes real time)
    timeOfDayHours = (timeOfDayHours + dt * 0.10f) % 24f
    map.trafficLight.update(dt)

    if (statusBannerTimer > 0f) {
      statusBannerTimer -= dt
      if (statusBannerTimer <= 0f) statusBanner = null
    }

    // 1. Player or Vehicle physics
    val activeVehicle = vehicleSystem.getById(player.activeVehicleId)
    if (activeVehicle != null && !player.isWasted) {
      vehicleSystem.updatePlayerDriving(
        dt = dt,
        vehicle = activeVehicle,
        throttle = driveThrottle,
        brake = driveBrake,
        steerInput = driveSteer,
        player = player,
        onExplosion = { exploded ->
          soundEngine.playExplosion()
          player.applyDamage(55, inSafeZone = map.isInsideSafeZone(player.x, player.z))
          npcAi.addWantedHeat(70f)
          showBanner("${exploded.type.displayName} Meledak!")
        },
      )
      soundEngine.tickEngine(
        inVehicle = true,
        speedRatio = (abs(activeVehicle.speed) / activeVehicle.type.maxSpeed).coerceIn(0f, 1f),
        nowMs = nowMs,
      )
      // Vehicle ramming NPCs
      if (abs(activeVehicle.speed) > 9f) {
        for (n in npcAi.npcs) {
          if (!n.alive) continue
          if (MathUtil.distSq2D(activeVehicle.x, activeVehicle.z, n.x, n.z) < 2.2f * 2.2f) {
            npcAi.damageNpc(n, 80, fromPlayer = true)
          }
        }
      }
    } else {
      val respawned = player.updateOnFoot(dt, joystickX, joystickY)
      if (respawned) {
        npcAi.clearWanted()
        showBanner("Respawn di Safe House")
      }
    }

    // 2. Auto-aim candidate gathering + firing
    val remotePlayers = if (isOnlineMode) interpolator.getInterpolatedPlayers(nowMs) else emptyList()
    val inSafeZone = map.isInsideSafeZone(player.x, player.z)

    val candidates = ArrayList<AutoAimTarget>(npcAi.npcs.size + 8)
    for (n in npcAi.npcs) {
      if (!n.alive) continue
      candidates.add(
        AutoAimTarget(
          x = n.x,
          y = n.y + 1.15f,
          z = n.z,
          kind = if (n.isPolice) AutoAimTarget.Kind.POLICE else AutoAimTarget.Kind.NPC,
          idString = n.id.toString(),
          idInt = n.id,
        )
      )
    }
    if (isOnlineMode && pvpEnabled && !inSafeZone) {
      for (rp in remotePlayers) {
        if (rp.hp > 0 && !rp.inSafeZone) {
          candidates.add(
            AutoAimTarget(
              x = rp.x,
              y = rp.y + 1.15f,
              z = rp.z,
              kind = AutoAimTarget.Kind.REMOTE_PLAYER,
              idString = rp.userId,
              idInt = -1,
            )
          )
        }
      }
    }

    val aimRefYaw = player.cameraYawDeg
    currentAutoAimTarget = if (player.activeVehicleId == null && !player.isWasted) {
      weaponSystem.findAutoAimTarget(
        originX = player.x,
        originY = player.y + 1.25f,
        originZ = player.z,
        aimYawDeg = aimRefYaw,
        maxRange = weaponSystem.activeWeapon.range,
        candidates = candidates,
      )
    } else null

    if (fireHeld && player.activeVehicleId == null && !player.isWasted) {
      val originY = player.y + 1.28f
      val rays = weaponSystem.tryFire(
        nowMs = nowMs,
        baseYawDeg = aimRefYaw,
        autoAimTarget = currentAutoAimTarget,
        originX = player.x,
        originY = originY,
        originZ = player.z,
      )
      if (rays != null) {
        soundEngine.playShot(weaponSystem.activeWeapon)
        player.cameraRecoilPitch = (player.cameraRecoilPitch + weaponSystem.activeWeapon.recoilKick).coerceAtMost(8f)
        player.yawDeg = currentAutoAimTarget?.let {
          MathUtil.yawFromDirDeg(it.x - player.x, it.z - player.z)
        } ?: aimRefYaw
        player.animState = "SHOOT"
        if (weaponSystem.activeWeapon != WeaponCategory.MELEE) {
          npcAi.triggerGunshotPanic(player.x, player.z)
        }
        resolveShotRays(rays, originY, remotePlayers, inSafeZone)
      }
    }

    // 3. Pick up dropped loot
    val lootIt = weaponSystem.lootDrops.iterator()
    while (lootIt.hasNext()) {
      val drop = lootIt.next()
      if (MathUtil.distSq2D(player.x, player.z, drop.x, drop.z) <= 2.3f * 2.3f) {
        if (drop.isCash) {
          cash += drop.amount
          showBanner("+$${drop.amount}", 1.2f)
        } else {
          weaponSystem.addAmmo(weaponSystem.activeWeapon, drop.amount)
          showBanner("+${drop.amount} Peluru", 1.2f)
        }
        soundEngine.playPickup()
        onSaveProfileProgress?.invoke(cash, weaponSystem.unlockedWeapons.toSet())
        lootIt.remove()
      }
    }

    // 4. Update Weapon, Vehicle Particles, NPCs & Police AI
    weaponSystem.update(dt, nowMs)
    vehicleSystem.updateParticlesAndRespawns(dt)
    npcAi.update(dt, player, inSafeZone) { dmg, sx, sy, sz ->
      player.applyDamage(dmg, inSafeZone)
      weaponSystem.tracers.add(BulletTracer(sx, sy, sz, player.x, player.y + 1.15f, player.z, 0.08f))
    }
    soundEngine.tickSiren(npcAi.wantedStars > 0 && !inSafeZone, nowMs)

    // 5. Remote gunshot flash feedback
    for (rp in remotePlayers) {
      val prevSeq = lastRemoteShotSeqs[rp.userId] ?: rp.shotSeq
      if (rp.shotSeq > prevSeq) {
        val fwdX = MathUtil.forwardX(rp.yawDeg)
        val fwdZ = MathUtil.forwardZ(rp.yawDeg)
        weaponSystem.tracers.add(
          BulletTracer(rp.x, rp.y + 1.25f, rp.z, rp.x + fwdX * 18f, rp.y + 1.25f, rp.z + fwdZ * 18f, 0.08f)
        )
      }
      lastRemoteShotSeqs[rp.userId] = rp.shotSeq
    }

    // 6. Sync local player to Online Multiplayer (~10Hz)
    if (isOnlineMode && nowMs - lastSyncMs >= 100L) {
      lastSyncMs = nowMs
      val v = vehicleSystem.getById(player.activeVehicleId)
      onSyncLocalPlayer?.invoke(
        RoomPlayerDoc(
          userId = localUserId,
          roomId = onlineRoomId,
          nickname = profile.nickname,
          skinColorIndex = profile.skinColorIndex.toLong(),
          outfitColorIndex = profile.outfitColorIndex.toLong(),
          x = player.x.toDouble(),
          y = player.y.toDouble(),
          z = player.z.toDouble(),
          yaw = player.yawDeg.toDouble(),
          speed = player.speed.toDouble(),
          animState = player.animState,
          vehicleType = v?.type?.name ?: "NONE",
          vehicleHp = (v?.hp ?: 100).toLong(),
          hp = player.health.toLong(),
          armor = player.armor.toLong(),
          wantedStars = npcAi.wantedStars.toLong(),
          weaponIndex = weaponSystem.activeWeapon.index.toLong(),
          inSafeZone = inSafeZone,
          shotSeq = weaponSystem.shotSequence,
        )
      )
    }

    // 7. Publish HUD snapshot (~20Hz)
    if (nowMs - lastHudEmitMs >= 50L) {
      lastHudEmitMs = nowMs
      emitHud(nowMs, remotePlayers)
    }
  }

  private fun resolveShotRays(
    rays: List<Triple<Float, Float, Float>>,
    originY: Float,
    remotePlayers: List<RemotePlayerRenderState>,
    inSafeZone: Boolean,
  ) {
    val weapon = weaponSystem.activeWeapon
    val maxRange = weapon.range
    val step = 1.1f

    for ((dx, dy, dz) in rays) {
      var hitX = player.x + dx * maxRange
      var hitY = originY + dy * maxRange
      var hitZ = player.z + dz * maxRange
      var hitSomething = false

      var dist = 1.0f
      while (dist <= maxRange && !hitSomething) {
        val rx = player.x + dx * dist
        val ry = originY + dy * dist
        val rz = player.z + dz * dist

        if (map.isBlockedForRay(rx, ry, rz)) {
          hitX = rx; hitY = ry; hitZ = rz
          hitSomething = true
          break
        }

        // Check NPCs & Police
        for (n in npcAi.npcs) {
          if (!n.alive) continue
          if (MathUtil.distSq2D(rx, rz, n.x, n.z) <= 0.85f * 0.85f && abs(ry - (n.y + 1.0f)) <= 1.25f) {
            npcAi.damageNpc(n, weapon.damage, fromPlayer = true)
            hitX = n.x; hitY = n.y + 1.1f; hitZ = n.z
            hitSomething = true
            break
          }
        }
        if (hitSomething) break

        // Check Remote PvP Players
        if (isOnlineMode && pvpEnabled && !inSafeZone) {
          for (rp in remotePlayers) {
            if (rp.hp > 0 && !rp.inSafeZone && MathUtil.distSq2D(rx, rz, rp.x, rp.z) <= 0.95f * 0.95f) {
              onSendPvpHit?.invoke(rp.userId, weapon.damage)
              hitX = rp.x; hitY = rp.y + 1.1f; hitZ = rp.z
              hitSomething = true
              break
            }
          }
        }
        if (hitSomething) break

        // Check Vehicles
        for (v in vehicleSystem.vehicles) {
          if (v.exploded || v.id == player.activeVehicleId) continue
          if (MathUtil.distSq2D(rx, rz, v.x, v.z) <= (v.type.halfWidth + 0.5f) * (v.type.halfWidth + 0.5f)) {
            vehicleSystem.damageVehicle(v, weapon.damage) {
              soundEngine.playExplosion()
              npcAi.addWantedHeat(65f)
            }
            if (v.isPoliceUnit) npcAi.addWantedHeat(30f)
            hitX = rx; hitY = ry; hitZ = rz
            hitSomething = true
            break
          }
        }

        dist += step
      }

      if (weapon != WeaponCategory.MELEE) {
        weaponSystem.tracers.add(
          BulletTracer(
            startX = player.x + dx * 0.6f,
            startY = originY,
            startZ = player.z + dz * 0.6f,
            endX = hitX,
            endY = hitY,
            endZ = hitZ,
            lifeSec = 0.07f,
          )
        )
      }
    }
  }

  private fun emitHud(nowMs: Long, remotePlayers: List<RemotePlayerRenderState> = emptyList()) {
    val activeV = vehicleSystem.getById(player.activeVehicleId)
    val nearV = if (activeV == null) vehicleSystem.findNearestEnterable(player.x, player.y, player.z) else null
    val nearM = map.markers.firstOrNull { MathUtil.distSq2D(player.x, player.z, it.x, it.z) <= it.radius * it.radius }

    val blips = ArrayList<MinimapBlip>(32)
    for (m in map.markers) {
      val kind = when (m.type) {
        WorldMarkerType.WEAPON_SHOP -> BlipKind.SHOP
        WorldMarkerType.GARAGE -> BlipKind.GARAGE
        WorldMarkerType.SAFE_HOUSE -> BlipKind.SAFE_HOUSE
      }
      blips.add(MinimapBlip(m.x, m.z, kind, m.type.label))
    }
    for (n in npcAi.npcs) {
      if (!n.alive) continue
      if (n.isPolice) {
        blips.add(MinimapBlip(n.x, n.z, BlipKind.POLICE))
      }
    }
    for (v in vehicleSystem.vehicles) {
      if (v.exploded) continue
      if (v.isPoliceUnit && npcAi.wantedStars > 0) {
        blips.add(MinimapBlip(v.x, v.z, BlipKind.POLICE))
      } else if (!v.isAiTraffic && v.id != player.activeVehicleId) {
        blips.add(MinimapBlip(v.x, v.z, BlipKind.VEHICLE))
      }
    }
    for (rp in remotePlayers) {
      blips.add(MinimapBlip(rp.x, rp.z, BlipKind.REMOTE_PLAYER, rp.nickname))
    }

    val feedCopy = synchronized(_chatFeed) { _chatFeed.toList() }
    val night = timeOfDayHours >= 19.0f || timeOfDayHours < 6.0f

    _hudState.value = HudState(
      health = player.health,
      armor = player.armor,
      cash = cash,
      wantedStars = npcAi.wantedStars,
      wantedCooldownRatio = npcAi.wantedCooldownRatio,
      activeWeapon = weaponSystem.activeWeapon,
      clipAmmo = weaponSystem.currentClip(),
      reserveAmmo = weaponSystem.currentReserve(),
      isReloading = weaponSystem.isReloading,
      unlockedWeapons = weaponSystem.unlockedWeapons.toSet(),
      inVehicle = activeV != null,
      vehicleType = activeV?.type ?: VehicleType.NONE,
      vehicleHp = activeV?.hp ?: 100,
      vehicleMaxHp = activeV?.type?.maxHp ?: 100,
      vehicleSpeedKmh = ((abs(activeV?.speed ?: 0f)) * 4.2f).toInt(),
      nearVehicleType = nearV?.type,
      nearMarker = nearM?.type,
      inSafeZone = map.isInsideSafeZone(player.x, player.z),
      hasAutoAimTarget = currentAutoAimTarget != null,
      playerX = player.x,
      playerZ = player.z,
      playerYawDeg = player.yawDeg,
      timeOfDayHours = timeOfDayHours,
      isNight = night,
      isOnline = isOnlineMode,
      onlineRoomName = onlineRoomName,
      onlinePlayerCount = remotePlayers.size + 1,
      pvpEnabled = pvpEnabled,
      minimapBlips = blips,
      chatFeed = feedCopy,
      statusBanner = statusBanner,
      isWasted = player.isWasted,
      currentFps = measuredFps,
    )
  }
}
