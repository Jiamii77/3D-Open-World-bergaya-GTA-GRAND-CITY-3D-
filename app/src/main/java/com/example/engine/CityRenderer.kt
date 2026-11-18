package com.example.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.CharacterPalettes
import com.example.game.CityChunk
import com.example.game.GameEngine
import com.example.game.VehicleType
import com.example.game.WorldMarkerType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CityRenderer(private val engine: GameEngine) : GLSurfaceView.Renderer {
  private var programId = 0
  private var posLoc = 0
  private var normalLoc = 0
  private var colorLoc = 0
  private var emissiveLoc = 0

  private var mvpLoc = 0
  private var modelLoc = 0
  private var sunDirLoc = 0
  private var sunColorLoc = 0
  private var ambientColorLoc = 0
  private var fogColorLoc = 0
  private var fogStartLoc = 0
  private var fogEndLoc = 0
  private var nightFactorLoc = 0
  private var cameraPosLoc = 0
  private var tintColorLoc = 0

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)
  private val frustum = Frustum()

  private var cityChunks: List<CityChunk> = emptyList()
  private var unitBoxMesh: GpuMesh? = null
  private var lastFrameNs = System.nanoTime()

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glCullFace(GLES20.GL_BACK)

    programId = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    posLoc = GLES20.glGetAttribLocation(programId, "aPos")
    normalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
    colorLoc = GLES20.glGetAttribLocation(programId, "aColor")
    emissiveLoc = GLES20.glGetAttribLocation(programId, "aEmissive")

    mvpLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
    modelLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
    sunDirLoc = GLES20.glGetUniformLocation(programId, "uSunDir")
    sunColorLoc = GLES20.glGetUniformLocation(programId, "uSunColor")
    ambientColorLoc = GLES20.glGetUniformLocation(programId, "uAmbientColor")
    fogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
    fogStartLoc = GLES20.glGetUniformLocation(programId, "uFogStart")
    fogEndLoc = GLES20.glGetUniformLocation(programId, "uFogEnd")
    nightFactorLoc = GLES20.glGetUniformLocation(programId, "uNightFactor")
    cameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")
    tintColorLoc = GLES20.glGetUniformLocation(programId, "uTintColor")

    cityChunks = engine.map.buildCityChunks()
    val boxBuilder = MeshBuilder(400)
    // Centered unit cube from (-0.5, 0.0, -0.5) to (+0.5, 1.0, +0.5)
    boxBuilder.addBox(0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, emissive = 0f, topShade = 1f, sideShade = 0.94f)
    unitBoxMesh = boxBuilder.buildMesh()
    lastFrameNs = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
    Matrix.perspectiveM(projMatrix, 0, 58f, aspect, 0.4f, 260f)
  }

  override fun onDrawFrame(gl: GL10?) {
    val nowNs = System.nanoTime()
    val dt = ((nowNs - lastFrameNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
    lastFrameNs = nowNs

    // Advance simulation tick
    engine.stepSimulation(dt)

    val p = engine.player
    val quality = engine.profile.graphicsQuality
    val drawDist = quality.drawDistance
    val drawDistSq = drawDist * drawDist

    // Day/Night sky, fog, sun lighting
    val tod = engine.timeOfDayHours
    val sunAngle = ((tod - 6f) / 12f) * PI.toFloat()
    val sunElevation = sin(sunAngle)
    val nightFactor = MathUtil.clamp((-sunElevation + 0.15f) / 0.55f, 0f, 1f)

    // Sky gradient across dawn / day / sunset / midnight
    val (skyR, skyG, skyB) = computeSkyColor(tod)
    GLES20.glClearColor(skyR, skyG, skyB, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    GLES20.glUseProgram(programId)

    // Camera transform (Third-person orbit around player/vehicle)
    val inVehicle = p.activeVehicleId != null
    val camDist = if (inVehicle) 10.5f else 6.4f
    val targetY = p.y + if (inVehicle) 1.55f else 1.65f
    val yawRad = p.cameraYawDeg * MathUtil.DEG_TO_RAD
    val pitchRad = (p.cameraPitchDeg - p.cameraRecoilPitch).coerceIn(3f, 56f) * MathUtil.DEG_TO_RAD

    val horiz = camDist * cos(pitchRad)
    val camX = p.x - sin(yawRad) * horiz
    val camZ = p.z - cos(yawRad) * horiz
    val rawCamY = targetY + camDist * sin(pitchRad)
    val camY = rawCamY.coerceAtLeast(engine.map.getGroundHeight(camX, camZ, p.y) + 0.85f)

    Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, p.x, targetY, p.z, 0f, 1f, 0f)
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    frustum.updateFromViewProjection(vpMatrix)

    // Upload environment uniforms
    val sx = cos(sunAngle) * 0.6f
    val sy = abs(sunElevation).coerceAtLeast(0.25f)
    val sz = 0.5f
    val sLen = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz)
    GLES20.glUniform3f(sunDirLoc, sx / sLen, sy / sLen, sz / sLen)

    val sunR = MathUtil.lerp(1.0f, 0.25f, nightFactor)
    val sunG = MathUtil.lerp(0.95f, 0.32f, nightFactor)
    val sunB = MathUtil.lerp(0.88f, 0.58f, nightFactor)
    GLES20.glUniform3f(sunColorLoc, sunR, sunG, sunB)

    val ambR = MathUtil.lerp(0.45f, 0.16f, nightFactor)
    val ambG = MathUtil.lerp(0.48f, 0.20f, nightFactor)
    val ambB = MathUtil.lerp(0.55f, 0.34f, nightFactor)
    GLES20.glUniform3f(ambientColorLoc, ambR, ambG, ambB)

    GLES20.glUniform3f(fogColorLoc, skyR, skyG, skyB)
    GLES20.glUniform1f(fogStartLoc, drawDist * 0.52f)
    GLES20.glUniform1f(fogEndLoc, drawDist)
    GLES20.glUniform1f(nightFactorLoc, nightFactor)
    GLES20.glUniform3f(cameraPosLoc, camX, camY, camZ)

    // 1. Render Frustum-Culled Static City Chunks
    Matrix.setIdentityM(modelMatrix, 0)
    GLES20.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
    GLES20.glUniformMatrix4fv(mvpLoc, 1, false, vpMatrix, 0)
    GLES20.glUniform4f(tintColorLoc, 1f, 1f, 1f, 0f)

    for (chunk in cityChunks) {
      val cx = (chunk.aabb.minX + chunk.aabb.maxX) * 0.5f
      val cz = (chunk.aabb.minZ + chunk.aabb.maxZ) * 0.5f
      if (MathUtil.distSq2D(p.x, p.z, cx, cz) > (drawDist + 65f) * (drawDist + 65f)) continue
      if (!frustum.isBoxVisible(chunk.aabb)) continue
      chunk.mesh.bindAndDraw(posLoc, normalLoc, colorLoc, emissiveLoc)
    }

    val box = unitBoxMesh ?: return
    val nowMs = System.currentTimeMillis()
    val pulse = (sin(nowMs * 0.005) * 0.5 + 0.5).toFloat()

    // 2. Render World Interaction Beacons (Shop, Garage, Safe House)
    for (m in engine.map.markers) {
      val (r, g, b) = when (m.type) {
        WorldMarkerType.WEAPON_SHOP -> Triple(1.0f, 0.37f, 0.23f)
        WorldMarkerType.GARAGE -> Triple(0.0f, 0.90f, 1.0f)
        WorldMarkerType.SAFE_HOUSE -> Triple(0.18f, 0.90f, 0.62f)
      }
      val floatY = 1.6f + pulse * 0.7f
      drawBox(box, m.x, floatY, m.z, (nowMs * 0.08f) % 360f, 1.1f, 1.1f, 1.1f, r, g, b, emissive = 1f)
      drawBox(box, m.x, 0.05f, m.z, 0f, 2.2f, 0.08f, 2.2f, r, g, b, emissive = 0.85f)
    }

    // 3. Render Intersection Traffic Lights
    val nsGreen = engine.map.trafficLight.nsGreen
    for (rx in engine.map.roadCoords) {
      for (rz in engine.map.roadCoords) {
        if (MathUtil.distSq2D(p.x, p.z, rx, rz) > drawDistSq) continue
        val px = rx + 6.8f
        val pz = rz + 6.8f
        drawBox(box, px, 0f, pz, 0f, 0.28f, 4.0f, 0.28f, 0.2f, 0.22f, 0.26f, 0f)
        if (nsGreen) {
          drawBox(box, px, 3.6f, pz, 0f, 0.55f, 0.55f, 0.55f, 0.15f, 0.95f, 0.45f, 1f)
        } else {
          drawBox(box, px, 3.6f, pz, 0f, 0.55f, 0.55f, 0.55f, 1.0f, 0.22f, 0.25f, 1f)
        }
      }
    }

    // 4. Render Vehicles (with Night Headlights + Police Sirens!)
    for (v in engine.vehicleSystem.vehicles) {
      if (MathUtil.distSq2D(p.x, p.z, v.x, v.z) > drawDistSq) continue
      drawVehicle(
        box = box,
        type = v.type,
        x = v.x,
        y = v.y,
        z = v.z,
        yawDeg = v.yawDeg,
        r = if (v.exploded) 0.14f else v.colorR,
        g = if (v.exploded) 0.14f else v.colorG,
        b = if (v.exploded) 0.15f else v.colorB,
        nightFactor = nightFactor,
        isPolice = v.isPoliceUnit,
        sirenActive = v.isPoliceUnit && engine.npcAi.wantedStars > 0 && !v.exploded,
        pulse = pulse,
      )
    }

    // 5. Render Local Player (when on foot)
    if (p.activeVehicleId == null) {
      val skin = CharacterPalettes.skinRgb[engine.profile.skinColorIndex.coerceIn(0, 5)]
      val outfit = CharacterPalettes.outfitRgb[engine.profile.outfitColorIndex.coerceIn(0, 7)]
      drawCharacter(
        box = box,
        x = p.x,
        y = p.y,
        z = p.z,
        yawDeg = p.yawDeg,
        animPhase = p.animPhase,
        moving = p.speed > 0.3f,
        skin = skin,
        outfit = outfit,
        hasWeapon = engine.weaponSystem.activeWeapon.index > 0,
        muzzleFlash = engine.weaponSystem.muzzleFlashSec > 0f,
      )
    }

    // 6. Render Pedestrian & Police NPCs
    for (n in engine.npcAi.npcs) {
      if (!n.alive) continue
      if (MathUtil.distSq2D(p.x, p.z, n.x, n.z) > drawDistSq) continue
      val skin = CharacterPalettes.skinRgb[n.skinIndex.coerceIn(0, 5)]
      val outfit = if (n.isPolice) floatArrayOf(0.14f, 0.34f, 0.85f) else CharacterPalettes.outfitRgb[n.outfitIndex.coerceIn(0, 7)]
      drawCharacter(
        box = box,
        x = n.x,
        y = n.y,
        z = n.z,
        yawDeg = n.yawDeg,
        animPhase = n.animPhase,
        moving = true,
        skin = skin,
        outfit = outfit,
        hasWeapon = n.isPolice,
        muzzleFlash = false,
      )
    }

    // 7. Render Interpolated Online Multiplayer Players
    if (engine.isOnlineMode) {
      val remotes = engine.interpolator.getInterpolatedPlayers(nowMs)
      for (rp in remotes) {
        if (MathUtil.distSq2D(p.x, p.z, rp.x, rp.z) > drawDistSq) continue
        if (rp.vehicleType != VehicleType.NONE) {
          drawVehicle(
            box = box,
            type = rp.vehicleType,
            x = rp.x,
            y = rp.y,
            z = rp.z,
            yawDeg = rp.yawDeg,
            r = 0.95f,
            g = 0.42f,
            b = 0.18f,
            nightFactor = nightFactor,
            isPolice = rp.vehicleType == VehicleType.POLICE,
            sirenActive = false,
            pulse = pulse,
          )
        } else {
          val skin = CharacterPalettes.skinRgb[rp.skinColorIndex.coerceIn(0, 5)]
          val outfit = CharacterPalettes.outfitRgb[rp.outfitColorIndex.coerceIn(0, 7)]
          drawCharacter(
            box = box,
            x = rp.x,
            y = rp.y,
            z = rp.z,
            yawDeg = rp.yawDeg,
            animPhase = (nowMs * 0.01f) % 360f,
            moving = rp.speed > 0.3f,
            skin = skin,
            outfit = outfit,
            hasWeapon = rp.weaponIndex > 0,
            muzzleFlash = rp.animState == "SHOOT",
          )
        }
        // Floating online player marker diamond above head
        drawBox(box, rp.x, rp.y + 2.45f, rp.z, (nowMs * 0.1f) % 360f, 0.35f, 0.35f, 0.35f, 0.0f, 0.90f, 1.0f, 1f)
      }
    }

    // 8. Render Loot Pickups, Bullet Tracers & Smoke/Fire Particles
    for (loot in engine.weaponSystem.lootDrops) {
      val spin = (nowMs * 0.12f) % 360f
      if (loot.isCash) {
        drawBox(box, loot.x, loot.y + 0.25f + pulse * 0.2f, loot.z, spin, 0.55f, 0.32f, 0.35f, 0.18f, 0.92f, 0.52f, 0.95f)
      } else {
        drawBox(box, loot.x, loot.y + 0.25f + pulse * 0.2f, loot.z, spin, 0.48f, 0.38f, 0.48f, 1.0f, 0.78f, 0.15f, 0.95f)
      }
    }

    for (tr in engine.weaponSystem.tracers) {
      val mx = (tr.startX + tr.endX) * 0.5f
      val my = (tr.startY + tr.endY) * 0.5f
      val mz = (tr.startZ + tr.endZ) * 0.5f
      val len = MathUtil.dist2D(tr.startX, tr.startZ, tr.endX, tr.endZ).coerceAtLeast(0.5f)
      val yaw = MathUtil.yawFromDirDeg(tr.endX - tr.startX, tr.endZ - tr.startZ)
      drawBox(box, mx, my, mz, yaw, 0.08f, 0.08f, len, 1.0f, 0.92f, 0.42f, 1f)
    }

    for (pt in engine.vehicleSystem.particles) {
      val s = 0.45f + (1f - pt.lifeSec / pt.maxLife) * 0.9f
      if (pt.isFire) {
        drawBox(box, pt.x, pt.y, pt.z, 0f, s, s, s, 1.0f, 0.42f, 0.10f, 1f)
      } else {
        drawBox(box, pt.x, pt.y, pt.z, 0f, s, s, s, 0.35f, 0.37f, 0.42f, 0f)
      }
    }
  }

  private fun drawCharacter(
    box: GpuMesh,
    x: Float,
    y: Float,
    z: Float,
    yawDeg: Float,
    animPhase: Float,
    moving: Boolean,
    skin: FloatArray,
    outfit: FloatArray,
    hasWeapon: Boolean,
    muzzleFlash: Boolean,
  ) {
    val rad = yawDeg * MathUtil.DEG_TO_RAD
    val fwdX = sin(rad)
    val fwdZ = cos(rad)
    val rightX = cos(rad)
    val rightZ = -sin(rad)

    val stride = if (moving) sin(animPhase) * 0.25f else 0f

    // Legs
    drawBox(box, x - rightX * 0.16f + fwdX * stride, y, z - rightZ * 0.16f + fwdZ * stride, yawDeg, 0.22f, 0.72f, 0.24f, 0.15f, 0.18f, 0.24f, 0f)
    drawBox(box, x + rightX * 0.16f - fwdX * stride, y, z + rightZ * 0.16f - fwdZ * stride, yawDeg, 0.22f, 0.72f, 0.24f, 0.15f, 0.18f, 0.24f, 0f)

    // Torso (Outfit)
    drawBox(box, x, y + 0.72f, z, yawDeg, 0.58f, 0.68f, 0.34f, outfit[0], outfit[1], outfit[2], 0f)

    // Arms
    drawBox(box, x - rightX * 0.38f - fwdX * stride * 0.7f, y + 0.74f, z - rightZ * 0.38f - fwdZ * stride * 0.7f, yawDeg, 0.18f, 0.58f, 0.20f, outfit[0] * 0.85f, outfit[1] * 0.85f, outfit[2] * 0.85f, 0f)
    val rArmFwd = if (hasWeapon) 0.28f else stride * 0.7f
    drawBox(box, x + rightX * 0.38f + fwdX * rArmFwd, y + 0.78f, z + rightZ * 0.38f + fwdZ * rArmFwd, yawDeg, 0.18f, 0.55f, 0.20f, skin[0], skin[1], skin[2], 0f)

    // Head (Skin)
    drawBox(box, x, y + 1.42f, z, yawDeg, 0.38f, 0.38f, 0.38f, skin[0], skin[1], skin[2], 0f)

    // Weapon & Muzzle Flash
    if (hasWeapon) {
      drawBox(box, x + rightX * 0.36f + fwdX * 0.48f, y + 1.12f, z + rightZ * 0.36f + fwdZ * 0.48f, yawDeg, 0.10f, 0.14f, 0.48f, 0.18f, 0.20f, 0.24f, 0f)
      if (muzzleFlash) {
        drawBox(box, x + rightX * 0.36f + fwdX * 0.82f, y + 1.14f, z + rightZ * 0.36f + fwdZ * 0.82f, yawDeg, 0.28f, 0.28f, 0.28f, 1.0f, 0.85f, 0.25f, 1f)
      }
    }
  }

  private fun drawVehicle(
    box: GpuMesh,
    type: VehicleType,
    x: Float,
    y: Float,
    z: Float,
    yawDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    nightFactor: Float,
    isPolice: Boolean,
    sirenActive: Boolean,
    pulse: Float,
  ) {
    val rad = yawDeg * MathUtil.DEG_TO_RAD
    val fwdX = sin(rad)
    val fwdZ = cos(rad)
    val rightX = cos(rad)
    val rightZ = -sin(rad)

    if (type == VehicleType.MOTO) {
      // Motorcycle frame + handlebars + 2 wheels
      drawBox(box, x, y + 0.26f, z, yawDeg, 0.42f, 0.55f, 1.95f, r, g, b, 0f)
      drawBox(box, x + fwdX * 0.55f, y + 0.72f, z + fwdZ * 0.55f, yawDeg, 0.75f, 0.14f, 0.18f, 0.25f, 0.28f, 0.32f, 0f)
      drawBox(box, x + fwdX * 0.78f, y + 0.0f, z + fwdZ * 0.78f, yawDeg, 0.22f, 0.62f, 0.62f, 0.12f, 0.12f, 0.14f, 0f)
      drawBox(box, x - fwdX * 0.78f, y + 0.0f, z - fwdZ * 0.78f, yawDeg, 0.24f, 0.62f, 0.62f, 0.12f, 0.12f, 0.14f, 0f)
      if (nightFactor > 0.25f) {
        drawBox(box, x + fwdX * 1.05f, y + 0.62f, z + fwdZ * 1.05f, yawDeg, 0.25f, 0.20f, 0.15f, 1f, 0.96f, 0.78f, 1f)
      }
      return
    }

    val w = type.halfWidth * 2f
    val l = type.halfLength * 2f
    val bodyH = if (type == VehicleType.SUV) 0.85f else 0.65f
    val cabH = if (type == VehicleType.SUV) 0.68f else 0.54f

    // Lower body chassis
    drawBox(box, x, y + 0.24f, z, yawDeg, w, bodyH, l, r, g, b, 0f)
    // Cabin & windshield
    drawBox(box, x - fwdX * 0.25f, y + 0.24f + bodyH, z - fwdZ * 0.25f, yawDeg, w * 0.84f, cabH, l * 0.52f, r * 0.88f, g * 0.88f, b * 0.88f, 0f)
    drawBox(box, x - fwdX * 0.25f, y + 0.30f + bodyH, z - fwdZ * 0.25f, yawDeg, w * 0.86f, cabH * 0.62f, l * 0.44f, 0.14f, 0.22f, 0.32f, 0.1f)

    // 4 Wheels
    val wx = type.halfWidth - 0.05f
    val wz = type.halfLength - 0.72f
    for (sx in floatArrayOf(-wx, wx)) {
      for (sz in floatArrayOf(-wz, wz)) {
        drawBox(box, x + rightX * sx + fwdX * sz, y, z + rightZ * sx + fwdZ * sz, yawDeg, 0.26f, 0.62f, 0.62f, 0.10f, 0.11f, 0.13f, 0f)
      }
    }

    // Night Headlights & Ground Light Beam
    val headGlow = if (nightFactor > 0.25f) 1f else 0.15f
    val hlX = type.halfWidth * 0.62f
    val hlZ = type.halfLength + 0.02f
    drawBox(box, x - rightX * hlX + fwdX * hlZ, y + 0.48f, z - rightZ * hlX + fwdZ * hlZ, yawDeg, 0.36f, 0.18f, 0.12f, 1.0f, 0.96f, 0.78f, headGlow)
    drawBox(box, x + rightX * hlX + fwdX * hlZ, y + 0.48f, z + rightZ * hlX + fwdZ * hlZ, yawDeg, 0.36f, 0.18f, 0.12f, 1.0f, 0.96f, 0.78f, headGlow)
    if (nightFactor > 0.32f) {
      drawBox(box, x + fwdX * (type.halfLength + 4.5f), y + 0.04f, z + fwdZ * (type.halfLength + 4.5f), yawDeg, 2.6f, 0.04f, 8.2f, 0.95f, 0.92f, 0.68f, 0.42f * nightFactor)
    }

    // Tail lights
    drawBox(box, x - rightX * hlX - fwdX * hlZ, y + 0.48f, z - rightZ * hlX - fwdZ * hlZ, yawDeg, 0.36f, 0.16f, 0.10f, 1.0f, 0.16f, 0.18f, 0.9f)
    drawBox(box, x + rightX * hlX - fwdX * hlZ, y + 0.48f, z + rightZ * hlX - fwdZ * hlZ, yawDeg, 0.36f, 0.16f, 0.10f, 1.0f, 0.16f, 0.18f, 0.9f)

    // Police lightbar
    if (isPolice) {
      val redFlash = if (sirenActive && pulse > 0.5f) 1f else 0.35f
      val blueFlash = if (sirenActive && pulse <= 0.5f) 1f else 0.35f
      drawBox(box, x - rightX * 0.32f - fwdX * 0.25f, y + 0.24f + bodyH + cabH, z - rightZ * 0.32f - fwdZ * 0.25f, yawDeg, 0.45f, 0.18f, 0.28f, 1.0f, 0.12f, 0.18f, redFlash)
      drawBox(box, x + rightX * 0.32f - fwdX * 0.25f, y + 0.24f + bodyH + cabH, z + rightZ * 0.32f - fwdZ * 0.25f, yawDeg, 0.45f, 0.18f, 0.28f, 0.18f, 0.45f, 1.0f, blueFlash)
    }
  }

  private fun drawBox(
    box: GpuMesh,
    x: Float, y: Float, z: Float,
    yawDeg: Float,
    sx: Float, sy: Float, sz: Float,
    r: Float, g: Float, b: Float,
    emissive: Float,
  ) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, x, y, z)
    if (yawDeg != 0f) {
      Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
    }
    Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES20.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)
    GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
    GLES20.glUniform4f(tintColorLoc, r, g, b, emissive)
    box.bindAndDraw(posLoc, normalLoc, colorLoc, emissiveLoc)
  }

  private fun computeSkyColor(tod: Float): Triple<Float, Float, Float> {
    return when {
      tod < 5.5f -> Triple(0.05f, 0.07f, 0.15f) // Deep Night
      tod < 7.5f -> {
        val t = (tod - 5.5f) / 2.0f
        Triple(MathUtil.lerp(0.05f, 0.92f, t), MathUtil.lerp(0.07f, 0.56f, t), MathUtil.lerp(0.15f, 0.46f, t))
      }
      tod < 9.5f -> {
        val t = (tod - 7.5f) / 2.0f
        Triple(MathUtil.lerp(0.92f, 0.42f, t), MathUtil.lerp(0.56f, 0.74f, t), MathUtil.lerp(0.46f, 0.96f, t))
      }
      tod < 16.5f -> Triple(0.42f, 0.74f, 0.96f) // Crisp Daylight
      tod < 19.0f -> {
        val t = (tod - 16.5f) / 2.5f
        Triple(MathUtil.lerp(0.42f, 0.88f, t), MathUtil.lerp(0.74f, 0.36f, t), MathUtil.lerp(0.96f, 0.38f, t))
      }
      tod < 21.0f -> {
        val t = (tod - 19.0f) / 2.0f
        Triple(MathUtil.lerp(0.88f, 0.05f, t), MathUtil.lerp(0.36f, 0.07f, t), MathUtil.lerp(0.38f, 0.15f, t))
      }
      else -> Triple(0.05f, 0.07f, 0.15f)
    }
  }

  private fun compileProgram(vs: String, fs: String): Int {
    val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
    val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
    val prog = GLES20.glCreateProgram()
    GLES20.glAttachShader(prog, vShader)
    GLES20.glAttachShader(prog, fShader)
    GLES20.glLinkProgram(prog)
    return prog
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    return shader
  }

  companion object {
    private const val VERTEX_SHADER = """
      uniform mat4 uMvpMatrix;
      uniform mat4 uModelMatrix;
      uniform vec4 uTintColor;

      attribute vec3 aPos;
      attribute vec3 aNormal;
      attribute vec3 aColor;
      attribute float aEmissive;

      varying vec3 vWorldPos;
      varying vec3 vNormal;
      varying vec3 vColor;
      varying float vEmissive;

      void main() {
        vec4 worldPos = uModelMatrix * vec4(aPos, 1.0);
        vWorldPos = worldPos.xyz;
        vNormal = normalize(mat3(uModelMatrix) * aNormal);
        vColor = aColor * uTintColor.rgb;
        vEmissive = max(aEmissive, uTintColor.a);
        gl_Position = uMvpMatrix * vec4(aPos, 1.0);
      }
    """

    private const val FRAGMENT_SHADER = """
      precision mediump float;

      uniform vec3 uSunDir;
      uniform vec3 uSunColor;
      uniform vec3 uAmbientColor;
      uniform vec3 uFogColor;
      uniform float uFogStart;
      uniform float uFogEnd;
      uniform float uNightFactor;
      uniform vec3 uCameraPos;

      varying vec3 vWorldPos;
      varying vec3 vNormal;
      varying vec3 vColor;
      varying float vEmissive;

      void main() {
        vec3 norm = normalize(vNormal);
        float diff = max(dot(norm, uSunDir), 0.0);
        vec3 lit = vColor * (uAmbientColor + uSunColor * diff * 0.85);

        // Emissive lights intensify automatically at night
        float glowBoost = mix(0.25, 1.35, uNightFactor);
        vec3 emissiveTerm = vColor * vEmissive * glowBoost;
        vec3 color = mix(lit, vColor * 1.15, clamp(vEmissive * glowBoost, 0.0, 1.0)) + emissiveTerm * 0.25;

        // Distance atmospheric fog
        float dist = length(vWorldPos - uCameraPos);
        float fogFactor = clamp((dist - uFogStart) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);
        vec3 finalColor = mix(color, uFogColor, fogFactor);

        gl_FragColor = vec4(finalColor, 1.0);
      }
    """
  }
}
