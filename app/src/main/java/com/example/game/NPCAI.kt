package com.example.game

import com.example.engine.MathUtil
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class NpcBehavior {
  WALK,
  PANIC,
  POLICE_CHASE,
}

data class NpcEntity(
  val id: Int,
  var x: Float,
  var y: Float,
  var z: Float,
  var yawDeg: Float,
  var hp: Int,
  val isPolice: Boolean,
  var behavior: NpcBehavior,
  var targetX: Float,
  var targetZ: Float,
  val skinIndex: Int,
  val outfitIndex: Int,
  var animPhase: Float = 0f,
  var panicTimer: Float = 0f,
  var shootCooldown: Float = 0f,
  var alive: Boolean = true,
  var respawnTimer: Float = 0f,
)

class NPCAI(
  private val map: MapGenerator,
  private val vehicleSystem: VehicleSystem,
  private val weaponSystem: WeaponSystem,
) {
  val npcs = ArrayList<NpcEntity>()
  private var nextNpcId = 1

  // Wanted system state
  var wantedStars: Int = 0
    private set
  private var wantedHeat: Float = 0f
  var wantedCooldownRatio: Float = 0f
    private set
  private var timeSinceSeenByPolice: Float = 0f
  private var policeSpawnTimer: Float = 0f

  fun populateCity(quality: GraphicsQuality) {
    npcs.clear()
    // Spawn pedestrian NPCs on sidewalks
    val wps = map.sidewalkWaypoints
    val count = quality.maxNpcs
    if (wps.isNotEmpty()) {
      for (i in 0 until count) {
        val wp = wps[i % wps.size]
        val dest = wps[(i + 5) % wps.size]
        npcs.add(
          NpcEntity(
            id = nextNpcId++,
            x = wp.first,
            y = 0f,
            z = wp.second,
            yawDeg = Random.nextFloat() * 360f,
            hp = 60,
            isPolice = false,
            behavior = NpcBehavior.WALK,
            targetX = dest.first,
            targetZ = dest.second,
            skinIndex = Random.nextInt(6),
            outfitIndex = Random.nextInt(8),
          )
        )
      }
    }

    // Spawn AI traffic cars on main roads
    vehicleSystem.vehicles.removeAll { it.isAiTraffic || it.isPoliceUnit }
    val trafficCount = quality.maxTraffic
    val roadSlots = map.roadCoords
    for (i in 0 until trafficCount) {
      val axisX = i % 2 == 0
      val dir = if ((i / 2) % 2 == 0) 1f else -1f
      val laneOffset = if (dir > 0f) 2.7f else -2.7f
      val roadCoord = roadSlots[(i * 2) % roadSlots.size]
      val posAlong = -110f + (i * 23f) % 220f
      val vx = if (axisX) posAlong else roadCoord + laneOffset
      val vz = if (axisX) roadCoord + laneOffset else posAlong
      val yaw = if (axisX) (if (dir > 0f) 90f else -90f) else (if (dir > 0f) 0f else 180f)
      val type = when (i % 3) {
        0 -> VehicleType.SEDAN
        1 -> VehicleType.SUV
        else -> VehicleType.SEDAN
      }
      val r = 0.25f + Random.nextFloat() * 0.7f
      val g = 0.25f + Random.nextFloat() * 0.7f
      val b = 0.25f + Random.nextFloat() * 0.7f
      val v = vehicleSystem.spawnVehicle(type, vx, vz, yaw, r, g, b, isAiTraffic = true)
      v.trafficAxisX = axisX
      v.trafficDirSign = dir
    }
  }

  fun clearWanted() {
    wantedStars = 0
    wantedHeat = 0f
    wantedCooldownRatio = 0f
    timeSinceSeenByPolice = 0f
    npcs.removeAll { it.isPolice }
    vehicleSystem.vehicles.removeAll { it.isPoliceUnit }
  }

  fun addWantedHeat(amount: Float) {
    wantedHeat = (wantedHeat + amount).coerceAtMost(500f)
    val computed = when {
      wantedHeat >= 360f -> 5
      wantedHeat >= 240f -> 4
      wantedHeat >= 140f -> 3
      wantedHeat >= 65f -> 2
      wantedHeat >= 18f -> 1
      else -> 0
    }
    if (computed > wantedStars) wantedStars = computed
    timeSinceSeenByPolice = 0f
  }

  fun triggerGunshotPanic(shotX: Float, shotZ: Float) {
    for (n in npcs) {
      if (!n.alive || n.isPolice) continue
      if (MathUtil.distSq2D(n.x, n.z, shotX, shotZ) <= 36f * 36f) {
        n.behavior = NpcBehavior.PANIC
        n.panicTimer = 7.5f
      }
    }
  }

  fun damageNpc(npc: NpcEntity, amount: Int, fromPlayer: Boolean) {
    if (!npc.alive) return
    npc.hp = (npc.hp - amount).coerceAtLeast(0)
    if (fromPlayer) {
      addWantedHeat(if (npc.isPolice) 45f else 28f)
      if (!npc.isPolice) {
        npc.behavior = NpcBehavior.PANIC
        npc.panicTimer = 8f
      }
    }
    if (npc.hp == 0) {
      npc.alive = false
      npc.respawnTimer = 15f
      weaponSystem.spawnLoot(npc.x, npc.y, npc.z, isCash = true, amount = if (npc.isPolice) 120 else 65)
      if (Random.nextFloat() < 0.45f) {
        weaponSystem.spawnLoot(npc.x + 0.7f, npc.y, npc.z + 0.4f, isCash = false, amount = 15)
      }
    }
  }

  fun update(
    dt: Float,
    player: PlayerController,
    playerInSafeZone: Boolean,
    onPoliceShotAtPlayer: (damage: Int, shooterX: Float, shooterY: Float, shooterZ: Float) -> Unit,
  ) {
    // 1. Update Pedestrian & Police NPCs
    val wps = map.sidewalkWaypoints
    var policeSeeingPlayer = false

    for (n in npcs) {
      if (!n.alive) {
        n.respawnTimer -= dt
        if (n.respawnTimer <= 0f && !n.isPolice && wps.isNotEmpty()) {
          val wp = wps[Random.nextInt(wps.size)]
          n.x = wp.first
          n.z = wp.second
          n.hp = 60
          n.behavior = NpcBehavior.WALK
          n.alive = true
        }
        continue
      }

      if (n.isPolice) {
        if (wantedStars == 0 || playerInSafeZone) {
          n.behavior = NpcBehavior.WALK
          continue
        }
        val dx = player.x - n.x
        val dz = player.z - n.z
        val dist = MathUtil.dist2D(n.x, n.z, player.x, player.z)
        if (dist < 52f) {
          policeSeeingPlayer = true
        }
        n.yawDeg = MathUtil.yawFromDirDeg(dx, dz)

        if (dist > 14f) {
          val spd = 6.8f
          val nx = n.x + (dx / dist) * spd * dt
          val nz = n.z + (dz / dist) * spd * dt
          val (rx, rz) = map.resolveCollision(nx, n.y, nz, 0.5f)
          n.x = rx
          n.z = rz
          n.animPhase += dt * 10f
        } else {
          // Police Officer fires bursts at player
          n.shootCooldown -= dt
          if (n.shootCooldown <= 0f) {
            n.shootCooldown = (1.15f - wantedStars * 0.14f).coerceAtLeast(0.45f)
            onPoliceShotAtPlayer(5 + wantedStars * 2, n.x, n.y + 1.35f, n.z)
          }
        }
        n.y = map.getGroundHeight(n.x, n.z, n.y)
        continue
      }

      // Civilian Pedestrian
      when (n.behavior) {
        NpcBehavior.PANIC -> {
          n.panicTimer -= dt
          val dx = n.x - player.x
          val dz = n.z - player.z
          val dist = MathUtil.dist2D(n.x, n.z, player.x, player.z).coerceAtLeast(0.1f)
          val spd = 6.2f
          val (rx, rz) = map.resolveCollision(n.x + (dx / dist) * spd * dt, n.y, n.z + (dz / dist) * spd * dt, 0.45f)
          n.x = rx
          n.z = rz
          n.yawDeg = MathUtil.yawFromDirDeg(dx, dz)
          n.animPhase += dt * 11f
          if (n.panicTimer <= 0f) {
            n.behavior = NpcBehavior.WALK
          }
        }
        NpcBehavior.WALK -> {
          val dx = n.targetX - n.x
          val dz = n.targetZ - n.z
          val dist = MathUtil.dist2D(n.x, n.z, n.targetX, n.targetZ)
          if (dist < 2.0f && wps.isNotEmpty()) {
            val nextWp = wps[Random.nextInt(wps.size)]
            n.targetX = nextWp.first
            n.targetZ = nextWp.second
          } else if (dist > 0.1f) {
            val spd = 2.1f
            val (rx, rz) = map.resolveCollision(n.x + (dx / dist) * spd * dt, n.y, n.z + (dz / dist) * spd * dt, 0.45f)
            // If stuck on corner, pick new waypoint
            if (MathUtil.distSq2D(rx, rz, n.x, n.z) < 0.0001f && wps.isNotEmpty()) {
              val nextWp = wps[Random.nextInt(wps.size)]
              n.targetX = nextWp.first
              n.targetZ = nextWp.second
            } else {
              n.x = rx
              n.z = rz
            }
            n.yawDeg = MathUtil.yawFromDirDeg(dx, dz)
            n.animPhase += dt * 5f
          }
        }
        NpcBehavior.POLICE_CHASE -> Unit
      }
    }

    // 2. Update AI Traffic Vehicles & Police Cruisers
    for (v in vehicleSystem.vehicles) {
      if (v.exploded) continue
      if (v.id == player.activeVehicleId) continue

      if (v.isPoliceUnit) {
        if (wantedStars == 0 || playerInSafeZone) {
          v.speed = MathUtil.lerp(v.speed, 0f, dt * 3f)
          continue
        }
        val dx = player.x - v.x
        val dz = player.z - v.z
        val dist = MathUtil.dist2D(v.x, v.z, player.x, player.z)
        if (dist < 58f) policeSeeingPlayer = true

        val desiredYaw = MathUtil.yawFromDirDeg(dx, dz)
        v.yawDeg = MathUtil.lerpAngleDeg(v.yawDeg, desiredYaw, (dt * 3.5f).coerceAtMost(1f))
        val targetSpd = if (dist > 18f) 21f + wantedStars * 1.5f else 0f
        v.speed = MathUtil.lerp(v.speed, targetSpd, dt * 2.8f)

        val rad = v.yawDeg * MathUtil.DEG_TO_RAD
        val (rx, rz) = map.resolveCollision(v.x + sin(rad) * v.speed * dt, v.y, v.z + cos(rad) * v.speed * dt, 1.4f)
        v.x = rx
        v.z = rz
        v.y = map.getGroundHeight(rx, rz, v.y)
        v.wheelSpinDeg = (v.wheelSpinDeg + v.speed * 40f * dt) % 360f
        continue
      }

      if (v.isAiTraffic) {
        updateTrafficCar(dt, v, player)
      }
    }

    // 3. Wanted System Spawner & Escape Cooldown
    if (wantedStars > 0) {
      if (policeSeeingPlayer && !playerInSafeZone) {
        timeSinceSeenByPolice = 0f
        wantedCooldownRatio = 0f
      } else {
        timeSinceSeenByPolice += dt
        val escapeSec = 10f
        wantedCooldownRatio = (timeSinceSeenByPolice / escapeSec).coerceIn(0f, 1f)
        if (timeSinceSeenByPolice >= escapeSec) {
          clearWanted()
          return
        }
      }

      policeSpawnTimer -= dt
      val desiredUnits = (wantedStars + 1).coerceAtMost(5)
      val activePoliceNpcs = npcs.count { it.isPolice && it.alive }
      val activePoliceCars = vehicleSystem.vehicles.count { it.isPoliceUnit && !it.exploded }

      if (policeSpawnTimer <= 0f) {
        policeSpawnTimer = 4.0f
        val angle = Random.nextFloat() * 360f * MathUtil.DEG_TO_RAD
        val spawnDist = 36f
        val sx = MathUtil.clamp(player.x + sin(angle) * spawnDist, -135f, 135f)
        val sz = MathUtil.clamp(player.z + cos(angle) * spawnDist, -135f, 135f)
        val (rx, rz) = map.resolveCollision(sx, 0f, sz, 1.5f)

        if (activePoliceNpcs < desiredUnits) {
          npcs.add(
            NpcEntity(
              id = nextNpcId++,
              x = rx,
              y = 0f,
              z = rz,
              yawDeg = 0f,
              hp = 85,
              isPolice = true,
              behavior = NpcBehavior.POLICE_CHASE,
              targetX = player.x,
              targetZ = player.z,
              skinIndex = 1,
              outfitIndex = 7,
            )
          )
        }
        if (activePoliceCars < (wantedStars + 1) / 2) {
          vehicleSystem.spawnVehicle(
            VehicleType.POLICE,
            rx + 2.5f,
            rz + 2.5f,
            MathUtil.yawFromDirDeg(player.x - rx, player.z - rz),
            0.12f, 0.18f, 0.35f,
            isPoliceUnit = true,
          )
        }
      }
    }
  }

  private fun updateTrafficCar(dt: Float, v: VehicleEntity, player: PlayerController) {
    // Check traffic light before entering intersection
    var shouldStop = false
    val light = map.trafficLight
    val greenForMe = if (v.trafficAxisX) !light.nsGreen else light.nsGreen

    if (!greenForMe) {
      val coord = if (v.trafficAxisX) v.x else v.z
      for (rc in map.roadCoords) {
        val distToStop = (rc - v.trafficDirSign * 8.5f - coord) * v.trafficDirSign
        if (distToStop in 0.5f..7.0f) {
          shouldStop = true
          break
        }
      }
    }

    // Check obstacle ahead (player or another vehicle)
    val rad = v.yawDeg * MathUtil.DEG_TO_RAD
    val lookX = v.x + sin(rad) * 6.5f
    val lookZ = v.z + cos(rad) * 6.5f
    if (MathUtil.distSq2D(lookX, lookZ, player.x, player.z) < 4.5f * 4.5f) {
      shouldStop = true
    }
    if (!shouldStop) {
      for (other in vehicleSystem.vehicles) {
        if (other.id == v.id || other.exploded) continue
        if (MathUtil.distSq2D(lookX, lookZ, other.x, other.z) < 4.2f * 4.2f) {
          shouldStop = true
          break
        }
      }
    }

    val targetSpd = if (shouldStop) 0f else 11.5f
    v.speed = MathUtil.lerp(v.speed, targetSpd, (dt * 4.5f).coerceAtMost(1f))

    var nextX = v.x + sin(rad) * v.speed * dt
    var nextZ = v.z + cos(rad) * v.speed * dt

    // Wrap smoothly around city boundaries
    if (nextX > 136f) nextX = -136f
    if (nextX < -136f) nextX = 136f
    if (nextZ > 136f) nextZ = -136f
    if (nextZ < -136f) nextZ = 136f

    v.x = nextX
    v.z = nextZ
    v.y = 0f
    v.wheelSpinDeg = (v.wheelSpinDeg + v.speed * 40f * dt) % 360f
  }
}
