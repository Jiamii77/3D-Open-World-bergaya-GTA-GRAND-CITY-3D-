package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
  operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
  operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
  operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
  fun length2D(): Float = sqrt(x * x + z * z)
  fun length(): Float = sqrt(x * x + y * y + z * z)
}

data class AABB(
  val minX: Float,
  val minY: Float,
  val minZ: Float,
  val maxX: Float,
  val maxY: Float,
  val maxZ: Float,
) {
  fun intersectsXZ(x: Float, z: Float, radius: Float): Boolean =
    x + radius > minX && x - radius < maxX && z + radius > minZ && z - radius < maxZ

  fun containsXZ(x: Float, z: Float): Boolean =
    x in minX..maxX && z in minZ..maxZ
}

/**
 * 6-plane Frustum extracted from column-major OpenGL View-Projection matrix for fast AABB culling.
 */
class Frustum {
  private val planes = Array(6) { FloatArray(4) }

  fun updateFromViewProjection(vp: FloatArray) {
    // Left
    setPlane(0, vp[3] + vp[0], vp[7] + vp[4], vp[11] + vp[8], vp[15] + vp[12])
    // Right
    setPlane(1, vp[3] - vp[0], vp[7] - vp[4], vp[11] - vp[8], vp[15] - vp[12])
    // Bottom
    setPlane(2, vp[3] + vp[1], vp[7] + vp[5], vp[11] + vp[9], vp[15] + vp[13])
    // Top
    setPlane(3, vp[3] - vp[1], vp[7] - vp[5], vp[11] - vp[9], vp[15] - vp[13])
    // Near
    setPlane(4, vp[3] + vp[2], vp[7] + vp[6], vp[11] + vp[10], vp[15] + vp[14])
    // Far
    setPlane(5, vp[3] - vp[2], vp[7] - vp[6], vp[11] - vp[10], vp[15] - vp[14])
  }

  private fun setPlane(idx: Int, a: Float, b: Float, c: Float, d: Float) {
    val len = sqrt(a * a + b * b + c * c).coerceAtLeast(1e-6f)
    planes[idx][0] = a / len
    planes[idx][1] = b / len
    planes[idx][2] = c / len
    planes[idx][3] = d / len
  }

  fun isBoxVisible(box: AABB): Boolean {
    for (i in 0 until 6) {
      val p = planes[i]
      val px = if (p[0] >= 0f) box.maxX else box.minX
      val py = if (p[1] >= 0f) box.maxY else box.minY
      val pz = if (p[2] >= 0f) box.maxZ else box.minZ
      if (p[0] * px + p[1] * py + p[2] * pz + p[3] < 0f) {
        return false
      }
    }
    return true
  }
}

object MathUtil {
  const val DEG_TO_RAD = (PI / 180.0).toFloat()
  const val RAD_TO_DEG = (180.0 / PI).toFloat()

  fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  fun clamp(v: Float, min: Float, max: Float): Float =
    if (v < min) min else if (v > max) max else v

  fun wrapAngleDeg(deg: Float): Float {
    var a = deg % 360f
    if (a > 180f) a -= 360f
    if (a < -180f) a += 360f
    return a
  }

  fun lerpAngleDeg(a: Float, b: Float, t: Float): Float {
    val diff = wrapAngleDeg(b - a)
    return wrapAngleDeg(a + diff * t)
  }

  fun distSq2D(ax: Float, az: Float, bx: Float, bz: Float): Float {
    val dx = bx - ax
    val dz = bz - az
    return dx * dx + dz * dz
  }

  fun dist2D(ax: Float, az: Float, bx: Float, bz: Float): Float = sqrt(distSq2D(ax, az, bx, bz))

  fun yawFromDirDeg(dx: Float, dz: Float): Float =
    (atan2(dx.toDouble(), dz.toDouble()) * RAD_TO_DEG).toFloat()

  fun forwardX(yawDeg: Float): Float = sin(yawDeg * DEG_TO_RAD)
  fun forwardZ(yawDeg: Float): Float = cos(yawDeg * DEG_TO_RAD)

  fun angleDiffDeg(a: Float, b: Float): Float = abs(wrapAngleDeg(b - a))
}
