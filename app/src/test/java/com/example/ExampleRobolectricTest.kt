package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.GameEngine
import com.example.game.PlayerProfileState
import com.example.game.VehicleType
import com.example.game.WeaponCategory
import com.example.network.RemotePlayerInterpolator
import com.example.network.RemotePlayerRenderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Vice Roam`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Vice Roam", appName)
  }

  @Test
  fun `game engine singleplayer simulation updates movement weapons and wanted level`() {
    val engine = GameEngine()
    engine.startSession(PlayerProfileState(nickname = "TestAce"), online = false)

    // Verify overpass ramp elevation
    val groundUnder = engine.map.getGroundHeight(0f, 0f, 0f)
    val groundOverpass = engine.map.getGroundHeight(0f, 0f, 4.0f)
    assertEquals(0f, groundUnder, 0.05f)
    assertEquals(4.4f, groundOverpass, 0.05f)

    // Verify initial safe zone around spawn
    assertTrue(engine.map.isInsideSafeZone(engine.player.x, engine.player.z))

    // Walk forward using virtual joystick
    val startZ = engine.player.z
    engine.joystickY = 1.0f
    repeat(15) { engine.stepSimulation(0.03f) }
    engine.joystickY = 0f
    assertTrue(engine.player.z != startZ)

    // Switch weapon & fire pistol
    engine.onSelectWeapon(WeaponCategory.PISTOL)
    val clipBefore = engine.weaponSystem.currentClip()
    engine.fireHeld = true
    engine.stepSimulation(0.03f)
    engine.fireHeld = false
    assertTrue(engine.weaponSystem.currentClip() < clipBefore)

    // Garage spawn + driving
    engine.spawnVehicleFromGarage(VehicleType.SEDAN)
    assertTrue(engine.player.activeVehicleId != null)
    engine.driveThrottle = true
    repeat(10) { engine.stepSimulation(0.03f) }
    engine.driveThrottle = false
    assertTrue(engine.hudState.value.inVehicle)
    assertFalse(engine.hudState.value.isWasted)
  }

  @Test
  fun `remote player interpolator smooths position transitions`() {
    val interp = RemotePlayerInterpolator()
    val t0 = 1000L
    val base = RemotePlayerRenderState(
      userId = "u1",
      nickname = "Rival",
      skinColorIndex = 0,
      outfitColorIndex = 1,
      x = 0f,
      y = 0f,
      z = 0f,
      yawDeg = 0f,
      speed = 5f,
      animState = "RUN",
      vehicleType = VehicleType.NONE,
      vehicleHp = 100,
      hp = 100,
      armor = 50,
      wantedStars = 0,
      weaponIndex = 1,
      inSafeZone = false,
      shotSeq = 0,
      emoteText = null,
    )
    interp.pushSnapshot(base, t0)
    interp.pushSnapshot(base.copy(x = 10f, yawDeg = 90f), t0 + 10L)

    val mid = interp.getInterpolatedPlayers(t0 + 90L).first()
    assertTrue(mid.x in 1f..9.5f)
    assertTrue(mid.yawDeg in 5f..85f)
  }
}
