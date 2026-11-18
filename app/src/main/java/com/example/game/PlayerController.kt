package com.example.game

import com.example.engine.MathUtil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PlayerController(private val map: MapGenerator) {
  var x: Float = -24f
  var y: Float = 0f
  var z: Float = 32f
  var yawDeg: Float = 180f
  var speed: Float = 0f
  var velocityY: Float = 0f
  var grounded: Boolean = true
  var animPhase: Float = 0f
  var animState: String = "IDLE"

  // Health & Armor
  var health: Int = 100
  var armor: Int = 50
  var isWasted: Boolean = false
  private var wastedTimer: Float = 0f

  // Third-person camera orbit
  var cameraYawDeg: Float = 180f
  var cameraPitchDeg: Float = 18f
  var cameraRecoilPitch: Float = 0f

  // Vehicle seat state
  var activeVehicleId: Int? = null

  fun resetToSafeHouse() {
    x = map.safeZoneCenterX
    z = map.safeZoneCenterZ + 8f
    y = 0f
    velocityY = 0f
    grounded = true
    health = 100
    armor = 50
    isWasted = false
    wastedTimer = 0f
    activeVehicleId = null
  }

  fun rotateCamera(deltaX: Float, deltaY: Float, sensitivity: Float) {
    val sens = 0.24f * sensitivity
    cameraYawDeg = MathUtil.wrapAngleDeg(cameraYawDeg - deltaX * sens)
    cameraPitchDeg = MathUtil.clamp(cameraPitchDeg + deltaY * sens * 0.65f, 4f, 52f)
  }

  fun jump() {
    if (activeVehicleId != null || isWasted) return
    if (grounded) {
      velocityY = 8.4f
      grounded = false
    }
  }

  fun applyDamage(amount: Int, inSafeZone: Boolean) {
    if (isWasted || inSafeZone || amount <= 0) return
    var rem = amount
    if (armor > 0) {
      val absorbed = rem.coerceAtMost(armor)
      armor -= absorbed
      rem -= absorbed
    }
    if (rem > 0) {
      health = (health - rem).coerceAtLeast(0)
    }
    if (health == 0) {
      isWasted = true
      wastedTimer = 2.6f
      activeVehicleId = null
      speed = 0f
      animState = "DOWN"
    }
  }

  fun healFull() {
    health = 100
    armor = 100
    isWasted = false
  }

  fun updateOnFoot(
    dt: Float,
    joyX: Float,
    joyY: Float,
  ): Boolean {
    cameraRecoilPitch = MathUtil.lerp(cameraRecoilPitch, 0f, (dt * 12f).coerceAtMost(1f))

    if (isWasted) {
      wastedTimer -= dt
      if (wastedTimer <= 0f) {
        resetToSafeHouse()
        return true // Respawned
      }
      return false
    }

    if (activeVehicleId != null) return false

    val mag = sqrt(joyX * joyX + joyY * joyY).coerceAtMost(1f)
    if (mag > 0.08f) {
      // Joystick direction relative to camera yaw
      val camRad = cameraYawDeg * MathUtil.DEG_TO_RAD
      val fwdX = sin(camRad)
      val fwdZ = cos(camRad)
      val rightX = -cos(camRad)
      val rightZ = sin(camRad)

      // joyY > 0 is stick pushed UP (forward)
      val moveDirX = fwdX * joyY + rightX * joyX
      val moveDirZ = fwdZ * joyY + rightZ * joyX
      val norm = sqrt(moveDirX * moveDirX + moveDirZ * moveDirZ).coerceAtLeast(1e-4f)
      val nx = moveDirX / norm
      val nz = moveDirZ / norm

      val isSprint = mag > 0.72f
      val targetSpeed = if (isSprint) 10.6f else 5.8f * mag
      speed = MathUtil.lerp(speed, targetSpeed, (dt * 10f).coerceAtMost(1f))

      val desiredYaw = MathUtil.yawFromDirDeg(nx, nz)
      yawDeg = MathUtil.lerpAngleDeg(yawDeg, desiredYaw, (dt * 14f).coerceAtMost(1f))

      val nextX = x + nx * speed * dt
      val nextZ = z + nz * speed * dt
      val (resolvedX, resolvedZ) = map.resolveCollision(nextX, y, nextZ, 0.55f)
      x = resolvedX
      z = resolvedZ

      animPhase += dt * (if (isSprint) 13f else 8f)
      animState = if (isSprint) "RUN" else "WALK"
    } else {
      speed = MathUtil.lerp(speed, 0f, (dt * 12f).coerceAtMost(1f))
      animState = "IDLE"
    }

    // Gravity & overpass deck landing
    val groundY = map.getGroundHeight(x, z, y)
    if (!grounded || y > groundY + 0.02f) {
      velocityY -= 22f * dt
      y += velocityY * dt
      if (y <= groundY) {
        y = groundY
        velocityY = 0f
        grounded = true
      }
    } else {
      y = groundY
    }
    return false
  }
}
