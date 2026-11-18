package com.example.game

import com.example.engine.AABB
import com.example.engine.GpuMesh
import com.example.engine.MathUtil
import com.example.engine.MeshBuilder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class CityChunk(
  val aabb: AABB,
  val mesh: GpuMesh,
)

data class StreetLampPos(val x: Float, val z: Float)

data class TrafficLightState(
  var timerSec: Float = 0f,
  var nsGreen: Boolean = true,
) {
  fun update(dt: Float) {
    timerSec += dt
    if (timerSec >= 9f) {
      timerSec = 0f
      nsGreen = !nsGreen
    }
  }
}

class MapGenerator {
  val worldHalfSize = 152f
  val roadCoords = floatArrayOf(-96f, -48f, 0f, 48f, 96f)
  val roadHalfWidth = 6.0f
  val sidewalkWidth = 2.5f

  val colliders = ArrayList<AABB>()
  val markers = listOf(
    WorldMarker(WorldMarkerType.WEAPON_SHOP, -24f, -13f, 4.2f),
    WorldMarker(WorldMarkerType.GARAGE, 24f, -13f, 4.2f),
    WorldMarker(WorldMarkerType.SAFE_HOUSE, -24f, 24f, 4.5f),
  )
  val safeZoneCenterX = -24f
  val safeZoneCenterZ = 24f
  val safeZoneRadius = 22f

  val streetLamps = ArrayList<StreetLampPos>()
  val sidewalkWaypoints = ArrayList<Pair<Float, Float>>()
  val trafficLight = TrafficLightState()

  init {
    val blockCenters = floatArrayOf(-120f, -72f, -24f, 24f, 72f, 120f)
    for (bx in blockCenters) {
      for (bz in blockCenters) {
        sidewalkWaypoints.add((bx - 16f) to (bz - 16f))
        sidewalkWaypoints.add((bx + 16f) to (bz + 16f))
      }
    }
  }

  fun isInsideSafeZone(x: Float, z: Float): Boolean =
    MathUtil.distSq2D(x, z, safeZoneCenterX, safeZoneCenterZ) <= safeZoneRadius * safeZoneRadius

  /**
   * Returns drivable/walkable ground Y including the central Overpass Bridge on X = 0.
   */
  fun getGroundHeight(x: Float, z: Float, currentY: Float): Float {
    // Overpass deck centered at X = 0, halfWidth = 4.2f
    if (abs(x) <= 4.4f && abs(z) <= 72f) {
      val bridgeY = when {
        abs(z) <= 36f -> 4.4f
        z < -36f -> {
          val t = ((z + 72f) / 36f).coerceIn(0f, 1f)
          t * 4.4f
        }
        else -> {
          val t = ((72f - z) / 36f).coerceIn(0f, 1f)
          t * 4.4f
        }
      }
      // If entity is near ramp entry or already elevated, snap to bridge deck; otherwise pass underneath
      if (currentY >= 1.2f || abs(z) >= 62f) {
        return bridgeY
      }
    }
    return 0f
  }

  /**
   * Resolves horizontal circle-vs-AABB collision against buildings and bridge pillars.
   * Returns adjusted (x, z).
   */
  fun resolveCollision(x: Float, y: Float, z: Float, radius: Float): Pair<Float, Float> {
    var rx = MathUtil.clamp(x, -worldHalfSize + radius, worldHalfSize - radius)
    var rz = MathUtil.clamp(z, -worldHalfSize + radius, worldHalfSize - radius)

    for (box in colliders) {
      if (y > box.maxY - 0.3f) continue
      val closestX = MathUtil.clamp(rx, box.minX, box.maxX)
      val closestZ = MathUtil.clamp(rz, box.minZ, box.maxZ)
      val dx = rx - closestX
      val dz = rz - closestZ
      val distSq = dx * dx + dz * dz
      if (distSq < radius * radius) {
        val dist = kotlin.math.sqrt(distSq)
        if (dist > 0.0001f) {
          val push = radius - dist
          rx += (dx / dist) * push
          rz += (dz / dist) * push
        } else {
          // Deep penetration fallback
          val penLeft = abs(rx - box.minX)
          val penRight = abs(box.maxX - rx)
          val penTop = abs(rz - box.minZ)
          val penBottom = abs(box.maxZ - rz)
          val m = min(min(penLeft, penRight), min(penTop, penBottom))
          when (m) {
            penLeft -> rx = box.minX - radius
            penRight -> rx = box.maxX + radius
            penTop -> rz = box.minZ - radius
            else -> rz = box.maxZ + radius
          }
        }
      }
    }
    return rx to rz
  }

  fun isBlockedForRay(x: Float, y: Float, z: Float): Boolean {
    for (box in colliders) {
      if (y <= box.maxY && x in box.minX..box.maxX && z in box.minZ..box.maxZ) {
        return true
      }
    }
    return false
  }

  /**
   * Generates chunked VBO meshes for the entire 3D open-world city for Frustum Culling.
   */
  fun buildCityChunks(): List<CityChunk> {
    colliders.clear()
    streetLamps.clear()
    sidewalkWaypoints.clear()

    val chunkMin = -160f
    val chunkSize = 80f
    val builders = Array(4) { Array(4) { MeshBuilder(4096) } }

    fun builderFor(x: Float, z: Float): MeshBuilder {
      val cx = (((x - chunkMin) / chunkSize).toInt()).coerceIn(0, 3)
      val cz = (((z - chunkMin) / chunkSize).toInt()).coerceIn(0, 3)
      return builders[cx][cz]
    }

    // 1. Base City Ground & Sidewalk Grid per chunk
    for (ix in 0 until 4) {
      for (iz in 0 until 4) {
        val cx = chunkMin + ix * chunkSize + chunkSize * 0.5f
        val cz = chunkMin + iz * chunkSize + chunkSize * 0.5f
        builders[ix][iz].addGroundRect(cx, cz, chunkSize, chunkSize, -0.04f, 0.22f, 0.27f, 0.24f)
      }
    }

    // 2. Asphalt Roads & Lane Markings along X & Z road corridors
    for (rc in roadCoords) {
      // North-South road segments per chunk
      for (iz in 0 until 4) {
        val cz = chunkMin + iz * chunkSize + chunkSize * 0.5f
        val b = builderFor(rc, cz)
        b.addGroundRect(rc, cz, roadHalfWidth * 2f, chunkSize, 0.0f, 0.14f, 0.16f, 0.20f)
        // Sidewalk curbs
        b.addBox(rc - roadHalfWidth - sidewalkWidth * 0.5f, 0f, cz, sidewalkWidth, 0.12f, chunkSize, 0.45f, 0.47f, 0.52f)
        b.addBox(rc + roadHalfWidth + sidewalkWidth * 0.5f, 0f, cz, sidewalkWidth, 0.12f, chunkSize, 0.45f, 0.47f, 0.52f)
        // Dashed yellow centerlines
        var zMark = cz - chunkSize * 0.5f + 4f
        while (zMark < cz + chunkSize * 0.5f) {
          val onIntersection = roadCoords.any { abs(zMark - it) < roadHalfWidth + 1.5f }
          if (!onIntersection) {
            b.addGroundRect(rc, zMark, 0.28f, 3.2f, 0.02f, 0.95f, 0.78f, 0.18f)
          }
          zMark += 8f
        }
      }

      // East-West road segments per chunk
      for (ix in 0 until 4) {
        val cx = chunkMin + ix * chunkSize + chunkSize * 0.5f
        val b = builderFor(cx, rc)
        b.addGroundRect(cx, rc, chunkSize, roadHalfWidth * 2f, 0.005f, 0.14f, 0.16f, 0.20f)
        b.addBox(cx, 0f, rc - roadHalfWidth - sidewalkWidth * 0.5f, chunkSize, 0.12f, sidewalkWidth, 0.45f, 0.47f, 0.52f)
        b.addBox(cx, 0f, rc + roadHalfWidth + sidewalkWidth * 0.5f, chunkSize, 0.12f, sidewalkWidth, 0.45f, 0.47f, 0.52f)
        var xMark = cx - chunkSize * 0.5f + 4f
        while (xMark < cx + chunkSize * 0.5f) {
          val onIntersection = roadCoords.any { abs(xMark - it) < roadHalfWidth + 1.5f }
          if (!onIntersection) {
            b.addGroundRect(xMark, rc, 3.2f, 0.28f, 0.022f, 0.95f, 0.78f, 0.18f)
          }
          xMark += 8f
        }
      }
    }

    // 3. Street Lamps & Sidewalk NPC Waypoints at block corners
    val blockCenters = floatArrayOf(-120f, -72f, -24f, 24f, 72f, 120f)
    for (bx in blockCenters) {
      for (bz in blockCenters) {
        sidewalkWaypoints.add((bx - 16f) to (bz - 16f))
        sidewalkWaypoints.add((bx + 16f) to (bz + 16f))
      }
    }

    for (rc in roadCoords) {
      for (pos in listOf(-108f, -72f, -24f, 24f, 72f, 108f)) {
        val lx = rc + roadHalfWidth + 1.1f
        val lz = pos
        streetLamps.add(StreetLampPos(lx, lz))
        val b = builderFor(lx, lz)
        b.addBox(lx, 0.1f, lz, 0.24f, 5.2f, 0.24f, 0.24f, 0.28f, 0.34f)
        b.addBox(lx - 0.5f, 5.1f, lz, 1.2f, 0.22f, 0.28f, 0.24f, 0.28f, 0.34f)
        // Emissive streetlamp bulb (glows at night)
        b.addBox(lx - 0.8f, 4.95f, lz, 0.48f, 0.18f, 0.40f, 1.0f, 0.88f, 0.55f, emissive = 1.0f)
      }
    }

    // 4. Elevated Overpass / Highway Bridge along X = 0 (Z in -72..+72)
    run {
      // North ramp (-72..-36)
      builderFor(0f, -54f).addRampZ(0f, 8.2f, -72f, 0.05f, -36f, 4.4f, 0.45f, 0.22f, 0.25f, 0.30f)
      // South ramp (72..36)
      builderFor(0f, 54f).addRampZ(0f, 8.2f, 72f, 0.05f, 36f, 4.4f, 0.45f, 0.22f, 0.25f, 0.30f)
      // Elevated deck (-36..+36)
      builderFor(0f, -18f).addBox(0f, 3.95f, -18f, 8.2f, 0.45f, 36f, 0.22f, 0.25f, 0.30f)
      builderFor(0f, 18f).addBox(0f, 3.95f, 18f, 8.2f, 0.45f, 36f, 0.22f, 0.25f, 0.30f)
      // Guardrails
      for (side in floatArrayOf(-3.95f, 3.95f)) {
        builderFor(side, -18f).addBox(side, 4.4f, -18f, 0.25f, 0.55f, 36f, 0.0f, 0.85f, 0.95f, emissive = 0.6f)
        builderFor(side, 18f).addBox(side, 4.4f, 18f, 0.25f, 0.55f, 36f, 0.0f, 0.85f, 0.95f, emissive = 0.6f)
      }
      // Support pillars clear of Z=0 crossroad
      for (pz in floatArrayOf(-34f, -14f, 14f, 34f)) {
        for (px in floatArrayOf(-3.6f, 3.6f)) {
          builderFor(px, pz).addBox(px, 0f, pz, 0.9f, 4.0f, 0.9f, 0.38f, 0.40f, 0.45f)
          colliders.add(AABB(px - 0.5f, 0f, pz - 0.5f, px + 0.5f, 4.0f, pz + 0.5f))
        }
      }
    }

    // 5. Modular City Districts inside each 31m x 31m block
    val rng = Random(4242)
    val innerCenters = floatArrayOf(-72f, -24f, 24f, 72f)
    for (bx in innerCenters) {
      for (bz in innerCenters) {
        val b = builderFor(bx, bz)
        when {
          // Central City Park (24, 24)
          bx == 24f && bz == 24f -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.16f, 0.48f, 0.22f)
            // Park fountain
            b.addBox(bx, 0f, bz, 6.2f, 0.55f, 6.2f, 0.65f, 0.68f, 0.75f)
            b.addGroundRect(bx, bz, 5.2f, 5.2f, 0.58f, 0.12f, 0.78f, 0.96f, emissive = 0.4f)
            b.addBox(bx, 0.55f, bz, 1.4f, 1.8f, 1.4f, 0.72f, 0.76f, 0.84f)
            colliders.add(AABB(bx - 3.1f, 0f, bz - 3.1f, bx + 3.1f, 2.3f, bz + 3.1f))
            for (dx in floatArrayOf(-10f, 10f)) {
              for (dz in floatArrayOf(-10f, 10f)) {
                b.addTree(bx + dx, bz + dz, 1.15f)
                colliders.add(AABB(bx + dx - 0.5f, 0f, bz + dz - 0.5f, bx + dx + 0.5f, 3f, bz + dz + 0.5f))
              }
            }
          }

          // Safe House & Spawn Plaza (-24, 24)
          bx == -24f && bz == 24f -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.18f, 0.24f, 0.32f)
            b.addRing(bx, 0.06f, bz, safeZoneRadius, 0.55f, 36, 0.18f, 0.90f, 0.62f, emissive = 0.85f)
            // Safe house villa offset behind spawn point
            val hx = bx - 6f
            val hz = bz + 8f
            b.addBox(hx, 0f, hz, 12f, 5.2f, 10f, 0.82f, 0.85f, 0.90f)
            b.addBox(hx, 5.2f, hz, 13f, 0.8f, 11f, 0.15f, 0.75f, 0.82f, emissive = 0.25f)
            b.addBox(hx, 1.8f, hz - 5.05f, 3.6f, 0.7f, 0.2f, 0.18f, 0.90f, 0.62f, emissive = 1.0f)
            colliders.add(AABB(hx - 6f, 0f, hz - 5f, hx + 6f, 6f, hz + 5f))
            b.addTree(bx + 10f, bz + 10f, 1.0f)
          }

          // Ammu-Nation Weapon Shop (-24, -24)
          bx == -24f && bz == -24f -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.22f, 0.20f, 0.26f)
            val sx = bx
            val sz = bz - 5f
            b.addBox(sx, 0f, sz, 16f, 6.2f, 12f, 0.26f, 0.24f, 0.32f)
            // Neon Coral Ammu-Nation trim
            b.addBox(sx, 6.2f, sz, 16.6f, 0.6f, 12.6f, 1.0f, 0.37f, 0.23f, emissive = 1.0f)
            b.addBox(sx, 3.6f, sz + 6.08f, 9f, 1.0f, 0.2f, 1.0f, 0.37f, 0.23f, emissive = 1.0f)
            colliders.add(AABB(sx - 8f, 0f, sz - 6f, sx + 8f, 6.8f, sz + 6f))
          }

          // Garage / Vehicle Spawn (24, -24)
          bx == 24f && bz == -24f -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.22f, 0.24f, 0.28f)
            val gx = bx + 5f
            val gz = bz - 6f
            b.addBox(gx, 0f, gz, 14f, 5.5f, 11f, 0.24f, 0.30f, 0.38f)
            b.addBox(gx, 5.5f, gz, 14.4f, 0.5f, 11.4f, 0.0f, 0.90f, 1.0f, emissive = 0.95f)
            b.addBox(gx, 0f, gz + 5.55f, 7.5f, 3.6f, 0.2f, 0.12f, 0.14f, 0.18f)
            colliders.add(AABB(gx - 7f, 0f, gz - 5.5f, gx + 7f, 6f, gz + 5.5f))
          }

          // Downtown Office Skyscrapers (North half bz < 0)
          bz < 0f -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.25f, 0.27f, 0.32f)
            val h1 = 16f + rng.nextInt(18).toFloat()
            val h2 = 12f + rng.nextInt(14).toFloat()
            emitSkyscraper(b, bx - 6.5f, bz, 12f, h1, 14f, rng)
            emitSkyscraper(b, bx + 7.0f, bz + 1.5f, 11f, h2, 13f, rng)
          }

          // Residential Area (South half bz > 0)
          else -> {
            b.addGroundRect(bx, bz, 30f, 30f, 0.03f, 0.18f, 0.38f, 0.20f)
            emitHouse(b, bx - 7f, bz - 6f, rng)
            emitHouse(b, bx + 7f, bz + 6f, rng)
            b.addTree(bx + 8f, bz - 8f, 0.95f)
            b.addTree(bx - 8f, bz + 8f, 0.95f)
          }
        }
      }
    }

    // 6. Outer Perimeter Skyline Towers (keeps horizon rich without cluttering play area)
    for (bx in floatArrayOf(-120f, 120f)) {
      for (bz in floatArrayOf(-120f, -72f, -24f, 24f, 72f, 120f)) {
        val b = builderFor(bx, bz)
        val h = 14f + rng.nextInt(16).toFloat()
        emitSkyscraper(b, bx, bz, 18f, h, 18f, rng)
      }
    }
    for (bx in floatArrayOf(-72f, -24f, 24f, 72f)) {
      for (bz in floatArrayOf(-120f, 120f)) {
        val b = builderFor(bx, bz)
        val h = 14f + rng.nextInt(16).toFloat()
        emitSkyscraper(b, bx, bz, 18f, h, 18f, rng)
      }
    }

    // Convert 4x4 grid to CityChunks
    val chunks = ArrayList<CityChunk>(16)
    for (ix in 0 until 4) {
      for (iz in 0 until 4) {
        val minX = chunkMin + ix * chunkSize
        val minZ = chunkMin + iz * chunkSize
        chunks.add(
          CityChunk(
            aabb = AABB(minX, -1f, minZ, minX + chunkSize, 45f, minZ + chunkSize),
            mesh = builders[ix][iz].buildMesh(),
          )
        )
      }
    }
    return chunks
  }

  private fun emitSkyscraper(
    b: MeshBuilder,
    cx: Float, cz: Float,
    w: Float, h: Float, d: Float,
    rng: Random,
  ) {
    val shade = 0.22f + rng.nextFloat() * 0.12f
    b.addBox(cx, 0f, cz, w, h, d, shade, shade + 0.04f, shade + 0.12f)
    // Neon roof rim
    val cyanAccent = rng.nextBoolean()
    if (cyanAccent) {
      b.addBox(cx, h, cz, w + 0.4f, 0.45f, d + 0.4f, 0.05f, 0.85f, 0.98f, emissive = 0.9f)
    } else {
      b.addBox(cx, h, cz, w + 0.4f, 0.45f, d + 0.4f, 1.0f, 0.45f, 0.25f, emissive = 0.9f)
    }
    // Emissive window bands along building height
    var wy = 3.2f
    while (wy < h - 2.2f) {
      val wr = if (cyanAccent) 0.55f else 0.95f
      val wg = if (cyanAccent) 0.92f else 0.82f
      val wb = if (cyanAccent) 1.0f else 0.48f
      b.addBox(cx, wy, cz, w + 0.12f, 0.9f, d + 0.12f, wr, wg, wb, emissive = 0.85f)
      wy += 3.6f
    }
    colliders.add(AABB(cx - w * 0.5f, 0f, cz - d * 0.5f, cx + w * 0.5f, h, cz + d * 0.5f))
  }

  private fun emitHouse(b: MeshBuilder, cx: Float, cz: Float, rng: Random) {
    val palette = rng.nextInt(3)
    val (r, g, bl) = when (palette) {
      0 -> Triple(0.84f, 0.76f, 0.66f)
      1 -> Triple(0.75f, 0.82f, 0.88f)
      else -> Triple(0.86f, 0.68f, 0.62f)
    }
    b.addBox(cx, 0f, cz, 9.5f, 4.2f, 8.5f, r, g, bl)
    b.addBox(cx, 4.2f, cz, 10.3f, 1.1f, 9.3f, 0.68f, 0.28f, 0.22f)
    b.addBox(cx, 5.3f, cz, 6.8f, 0.9f, 5.8f, 0.58f, 0.22f, 0.18f)
    // Glowing warm window at night
    b.addBox(cx, 1.6f, cz + 4.3f, 2.4f, 1.2f, 0.15f, 1.0f, 0.85f, 0.45f, emissive = 0.9f)
    b.addBox(cx, 1.6f, cz - 4.3f, 2.4f, 1.2f, 0.15f, 1.0f, 0.85f, 0.45f, emissive = 0.9f)
    colliders.add(AABB(cx - 4.8f, 0f, cz - 4.3f, cx + 4.8f, 6f, cz + 4.3f))
  }
}
