package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.network.AuthManager
import com.example.ui.AppRoute
import com.example.ui.GameScreen
import com.example.ui.MainGameViewModel
import com.example.ui.MainMenuScreen
import com.example.ui.OnlineLobbyRoute
import com.example.ui.OnlineSessionViewModel
import com.example.ui.theme.ViceRoamTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ViceRoamTheme {
        ViceRoamApp()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
  }
}

@Composable
fun ViceRoamApp(mainVm: MainGameViewModel = viewModel()) {
  val route by mainVm.route.collectAsStateWithLifecycle()
  val profile by mainVm.profile.collectAsStateWithLifecycle()
  val auth = remember { Firebase.auth }
  val currentUser by AuthManager.authStateFlow(auth)
    .collectAsStateWithLifecycle(initialValue = auth.currentUser)

  var activeOnlineSessionVm by remember(currentUser?.uid) {
    mutableStateOf<OnlineSessionViewModel?>(null)
  }

  when (route) {
    AppRoute.MAIN_MENU -> {
      MainMenuScreen(
        profile = profile,
        signedInEmail = currentUser?.email,
        onPlaySingleplayer = {
          activeOnlineSessionVm = null
          mainVm.launchSingleplayer()
        },
        onOpenOnlineLobby = {
          mainVm.navigateTo(AppRoute.ONLINE_LOBBY)
        },
        onSaveCustomization = { nick, skin, outfit ->
          mainVm.updateCustomization(nick, skin, outfit)
        },
        onSaveSettings = { quality, sens, vol ->
          mainVm.updateSettings(quality, sens, vol)
        },
      )
    }

    AppRoute.ONLINE_LOBBY -> {
      OnlineLobbyRoute(
        currentUser = currentUser,
        profile = profile,
        gameEngine = mainVm.gameEngine,
        onSyncCloudProfileToLocal = { cloudProfile ->
          mainVm.updateProfile(cloudProfile)
        },
        onEnterOnlineGame = { sessionVm ->
          activeOnlineSessionVm = sessionVm
          mainVm.navigateTo(AppRoute.IN_GAME)
        },
        onBackToMenu = {
          mainVm.navigateTo(AppRoute.MAIN_MENU)
        },
      )
    }

    AppRoute.IN_GAME -> {
      GameScreen(
        engine = mainVm.gameEngine,
        profile = profile,
        onlineSessionVm = activeOnlineSessionVm,
        onExitToMenu = {
          mainVm.exitGameToMenu()
        },
      )
    }
  }
}
