package com.example.game

import com.example.engine.MathUtil
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class VehicleEntity(
  val id: Int,
  val type: VehicleType,
  var x: Float,
  var y: Float,
  var z: Float,
  var yawDeg: Float,
  var speed: Float = 0f,
  var hp: Int = type.maxHp,
  var wheelSpinDeg: Float = 0f,
  var colorR: Float,
  var colorG: Float,
  var colorB: Float,
  var isAiTraffic: Boolean = false,
  var isPoliceUnit: Boolean = false,
  var trafficAxisX: Boolean = false,
  var trafficDirSign: Float = 1f,
  var exploded: Boolean = false,
  var respawnTimer: Float = 0f,
)

data class SmokeParticle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vy: Float,
  var lifeSec: Float,
  var maxLife: Float,
  val isFire: Boolean,
)

class VehicleSystem(private val map: MapGenerator) {
  val vehicles = ArrayList<VehicleEntity>()
  val particles = ArrayList<SmokeParticle>()
  private var nextId = 1

  fun spawnInitialFleet() {
    vehicles.clear()
    particles.clear()
    // Parked / Starter vehicles near Safe House & Garage
    spawnVehicle(VehicleType.SEDAN, -15f, 22f, 90f, 0.0f, 0.90f, 1.0f)       // Neon Cyan Sports Sedan
    spawnVehicle(VehicleType.SUV, -33f, 16f, 0f, 0.20f, 0.25f, 0.34f)         // Stealth Urban SUV
    spawnVehicle(VehicleType.MOTO, -18f, 34f, 180f, 1.0f, 0.37f, 0.23f)      // Sunset Coral Moto
    spawnVehicle(VehicleType.SEDAN, 24f, -18f, 180f, 1.0f, 0.76f, 0.03f)     // Gold Garage Sedan
    spawnVehicle(VehicleType.MOTO, 28f, -18f, 180f, 0.18f, 0.90f, 0.62f)     // Emerald Garage Moto
    spawnVehicle(VehicleType.SUV, -24f, -6f, 90f, 0.85f, 0.22f, 0.28f)       // Crimson SUV
  }

  fun spawnVehicle(
    type: VehicleType,
    x: Float,
    z: Float,
    yawDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    isAiTraffic: Boolean = false,
    isPoliceUnit: Boolean = false,
  ): VehicleEntity {
    val entity = VehicleEntity(
      id = nextId++,
      type = type,
      x = x,
      y = map.getGroundHeight(x, z, 0f),
      z = z,
      yawDeg = yawDeg,
      colorR = r,
      colorG = g,
      colorB = b,
      isAiTraffic = isAiTraffic,
      isPoliceUnit = isPoliceUnit,
    )
    vehicles.add(entity)
    return entity
  }

  fun spawnGarageVehicle(type: VehicleType): VehicleEntity {
    // Remove old custom garage spawn if overcrowded
    if (vehicles.size > 26) {
      vehicles.firstOrNull { !it.isAiTraffic && !it.isPoliceUnit }?.let { vehicles.remove(it) }
    }
    val (r, g, b) = when (type) {
      VehicleType.SEDAN -> Triple(0.0f, 0.90f, 1.0f)
      VehicleType.SUV -> Triple(1.0f, 0.76f, 0.03f)
      VehicleType.MOTO -> Triple(1.0f, 0.37f, 0.23f)
      else -> Triple(0.95f, 0.95f, 0.98f)
    }
    return spawnVehicle(type, 24f, -16f, 180f, r, g, b)
  }

  fun getById(id: Int?): VehicleEntity? =
    if (id == null) null else vehicles.firstOrNull { it.id == id }

  fun findNearestEnterable(px: Float, py: Float, pz: Float, maxDist: Float = 4.5f): VehicleEntity? {
    var best: VehicleEntity? = null
    var bestSq = maxDist * maxDist
    for (v in vehicles) {
      if (v.exploded || abs(v.y - py) > 2.5f) continue
      val dSq = MathUtil.distSq2D(px, pz, v.x, v.z)
      if (dSq < bestSq) {
        bestSq = dSq
        best = v
      }
    }
    return best
  }

  fun damageVehicle(
    v: VehicleEntity,
    amount: Int,
    onExplosion: (VehicleEntity) -> Unit,
  ) {
    if (v.exploded) return
    v.hp = (v.hp - amount).coerceAtLeast(0)
    if (v.hp == 0) {
      v.exploded = true
      v.speed = 0f
      v.respawnTimer = 14f
      repeat(16) {
        particles.add(
          SmokeParticle(
            x = v.x + (Random.nextFloat() - 0.5f) * 2.2f,
            y = v.y + 0.8f + Random.nextFloat() * 1.2f,
            z = v.z + (Random.nextFloat() - 0.5f) * 2.2f,
            vy = 2.5f + Random.nextFloat() * 3.5f,
            lifeSec = 1.4f,
            maxLife = 1.4f,
            isFire = true,
          )
        )
      }
      onExplosion(v)
    }
  }

  fun updatePlayerDriving(
    dt: Float,
    vehicle: VehicleEntity,
    throttle: Boolean,
    brake: Boolean,
    steerInput: Float,
    player: PlayerController,
    onExplosion: (VehicleEntity) -> Unit,
  ) {
    if (vehicle.exploded) {
      player.activeVehicleId = null
      return
    }
    // Hijacking AI car stops AI traffic control
    vehicle.isAiTraffic = false

    val t = vehicle.type
    when {
      throttle -> {
        vehicle.speed = (vehicle.speed + t.acceleration * dt).coerceAtMost(t.maxSpeed)
      }
      brake -> {
        if (vehicle.speed > 1.2f) {
          vehicle.speed = (vehicle.speed - t.acceleration * 1.9f * dt).coerceAtLeast(0f)
        } else {
          vehicle.speed = (vehicle.speed - t.acceleration * 0.8f * dt).coerceAtLeast(-t.reverseSpeed)
        }
      }
      else -> {
        // Rolling drag
        vehicle.speed = MathUtil.lerp(vehicle.speed, 0f, (dt * 2.4f).coerceAtMost(1f))
        if (abs(vehicle.speed) < 0.12f) vehicle.speed = 0f
      }
    }

    // Arcade steering scales smoothly with forward/reverse motion
    val speedFactor = (abs(vehicle.speed) / 6f).coerceIn(0f, 1f)
    val dirSign = if (vehicle.speed >= -0.1f) 1f else -1f
    if (abs(steerInput) > 0.05f && speedFactor > 0.04f) {
      vehicle.yawDeg = MathUtil.wrapAngleDeg(
        vehicle.yawDeg - steerInput * t.turnRateDeg * speedFactor * dirSign * dt
      )
    }

    val rad = vehicle.yawDeg * MathUtil.DEG_TO_RAD
    val nextX = vehicle.x + sin(rad) * vehicle.speed * dt
    val nextZ = vehicle.z + cos(rad) * vehicle.speed * dt
    val radius = (t.halfWidth + t.halfLength) * 0.45f
    val (rx, rz) = map.resolveCollision(nextX, vehicle.y, nextZ, radius)

    // Wall impact damage at high speed
    val blockedDist = MathUtil.dist2D(nextX, nextZ, rx, rz)
    if (blockedDist > 0.04f && abs(vehicle.speed) > 12f) {
      damageVehicle(vehicle, (abs(vehicle.speed) * 0.9f).toInt(), onExplosion)
      vehicle.speed *= -0.25f
    }

    vehicle.x = rx
    vehicle.z = rz
    vehicle.y = MathUtil.lerp(vehicle.y, map.getGroundHeight(rx, rz, vehicle.y), (dt * 18f).coerceAtMost(1f))
    vehicle.wheelSpinDeg = (vehicle.wheelSpinDeg + vehicle.speed * 42f * dt) % 360f

    // Sync driver transform
    player.x = vehicle.x
    player.y = vehicle.y
    player.z = vehicle.z
    player.yawDeg = vehicle.yawDeg
    player.speed = abs(vehicle.speed)
    player.animState = "DRIVE"
  }

  fun updateParticlesAndRespawns(dt: Float) {
    // Emit smoke/fire on damaged vehicles
    for (v in vehicles) {
      if (v.exploded) {
        v.respawnTimer -= dt
        if (v.respawnTimer <= 0f && !v.isAiTraffic && !v.isPoliceUnit) {
          v.exploded = false
          v.hp = v.type.maxHp
        }
        continue
      }
      val hpRatio = v.hp.toFloat() / v.type.maxHp.toFloat()
      if (hpRatio < 0.48f && Random.nextFloat() < dt * 12f) {
        particles.add(
          SmokeParticle(
            x = v.x + (Random.nextFloat() - 0.5f) * 0.7f,
            y = v.y + 0.9f,
            z = v.z + (Random.nextFloat() - 0.5f) * 0.7f,
            vy = 1.6f + Random.nextFloat() * 1.2f,
            lifeSec = 0.8f,
            maxLife = 0.8f,
            isFire = hpRatio < 0.22f,
          )
        )
      }
    }

    val it = particles.iterator()
    while (it.hasNext()) {
      val p = it.next()
      p.y += p.vy * dt
      p.lifeSec -= dt
      if (p.lifeSec <= 0f) it.remove()
    }
  }
}
