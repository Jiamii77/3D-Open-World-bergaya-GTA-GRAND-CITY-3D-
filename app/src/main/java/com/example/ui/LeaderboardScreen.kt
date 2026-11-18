package com.example.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.game.CharacterPalettes
import com.example.game.PlayerProfileState
import com.example.network.FirestoreMultiplayerRepository
import com.example.network.LeaderboardScoreDoc
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SunsetCoral
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViceAmber
import com.example.ui.theme.ViceCardBorder
import com.example.ui.theme.ViceCrimson
import com.example.ui.theme.ViceEmerald
import com.example.ui.theme.ViceMidnight
import com.example.ui.theme.ViceSurface
import com.example.ui.theme.ViceSurfaceElevated
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class LeaderboardViewModel(
  private val repo: FirestoreMultiplayerRepository,
  val currentUserId: String,
) : ViewModel() {

  private val refreshTrigger = MutableStateFlow(0)

  @OptIn(ExperimentalCoroutinesApi::class)
  val topScoresState: StateFlow<UiState<List<LeaderboardScoreDoc>>> =
    refreshTrigger
      .flatMapLatest {
        repo.observeTopScores(limitCount = 10L)
          .map<List<LeaderboardScoreDoc>, UiState<List<LeaderboardScoreDoc>>> { scores ->
            UiState.Success(scores)
          }
          .onStart { emit(UiState.Loading) }
          .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w("LeaderboardVM", "Error observing top 10 scores", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Gagal memuat papan peringkat"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val _isErrorFeedback = MutableStateFlow(false)
  val isErrorFeedback: StateFlow<Boolean> = _isErrorFeedback.asStateFlow()

  fun refreshScores() {
    refreshTrigger.value += 1
  }

  fun syncMyScore(profile: PlayerProfileState) {
    if (_isSyncing.value) return
    viewModelScope.launch {
      _isSyncing.value = true
      _feedbackMessage.value = null
      val result = repo.publishPlayerScore(profile)
      result.fold(
        onSuccess = { bestScore ->
          _isErrorFeedback.value = false
          _feedbackMessage.value = "Skor tersinkronisasi: $${formatScore(bestScore)}"
        },
        onFailure = { err ->
          _isErrorFeedback.value = true
          _feedbackMessage.value = err.localizedMessage ?: "Gagal mengirim skor ke server"
        },
      )
      _isSyncing.value = false
    }
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }
}

@Composable
fun LeaderboardRoute(
  currentUser: FirebaseUser?,
  profile: PlayerProfileState,
  onBackToMenu: () -> Unit,
) {
  BackHandler { onBackToMenu() }

  if (currentUser == null) {
    OnlineAuthScreen(
      onBackToMenu = onBackToMenu,
      onAuthSuccess = {},
    )
  } else {
    val uid = currentUser.uid
    key(uid) {
      val leaderboardVm: LeaderboardViewModel = viewModel(
        key = "leaderboard_$uid",
        factory = viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            LeaderboardViewModel(FirestoreMultiplayerRepository(db), uid)
          }
        },
      )
      LeaderboardScreen(
        user = currentUser,
        profile = profile,
        viewModel = leaderboardVm,
        onBackToMenu = onBackToMenu,
      )
    }
  }
}

@Composable
fun LeaderboardScreen(
  user: FirebaseUser,
  profile: PlayerProfileState,
  viewModel: LeaderboardViewModel,
  onBackToMenu: () -> Unit,
) {
  val scoresState by viewModel.topScoresState.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val feedbackMsg by viewModel.feedbackMessage.collectAsStateWithLifecycle()
  val isErrorFeedback by viewModel.isErrorFeedback.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            ViceMidnight,
            Color(0xFF0E1628),
            ViceMidnight,
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
      // Top Navigation & Actions Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          IconButton(
            onClick = onBackToMenu,
            modifier = Modifier
              .size(48.dp)
              .testTag("leaderboard_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Kembali ke menu",
              tint = TextPrimary,
            )
          }
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = ViceAmber,
                modifier = Modifier.size(22.dp),
              )
              Text(
                text = stringResource(R.string.leaderboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text = stringResource(R.string.leaderboard_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { viewModel.refreshScores() },
            modifier = Modifier
              .height(48.dp)
              .testTag("leaderboard_refresh_button"),
            border = BorderStroke(1.dp, ViceCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.leaderboard_refresh))
          }

          Button(
            onClick = { viewModel.syncMyScore(profile) },
            enabled = !isSyncing,
            modifier = Modifier
              .height(48.dp)
              .testTag("sync_score_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = ViceAmber,
              contentColor = ViceMidnight,
            ),
          ) {
            if (isSyncing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = ViceMidnight,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.leaderboard_sync_score), fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      AnimatedVisibility(
        visible = feedbackMsg != null,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        feedbackMsg?.let { msg ->
          val accent = if (isErrorFeedback) ViceCrimson else ViceEmerald
          Surface(
            color = accent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, accent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(msg, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
              TextButton(onClick = { viewModel.clearFeedback() }) {
                Text("Tutup", color = accent)
              }
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      val topList = (scoresState as? UiState.Success)?.data ?: emptyList()
      val myRankIndex = topList.indexOfFirst { it.userId == user.uid }

      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
      ) {
        val isWide = maxWidth >= 680.dp
        if (isWide) {
          Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            PlayerRankSummaryCard(
              profile = profile,
              email = user.email,
              myRank = if (myRankIndex >= 0) myRankIndex + 1 else null,
              modifier = Modifier
                .width(270.dp)
                .fillMaxHeight(),
            )
            LeaderboardListPane(
              scoresState = scoresState,
              currentUserId = user.uid,
              onRetry = { viewModel.refreshScores() },
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 600.dp)
              .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            PlayerRankSummaryCard(
              profile = profile,
              email = user.email,
              myRank = if (myRankIndex >= 0) myRankIndex + 1 else null,
              modifier = Modifier.fillMaxWidth(),
            )
            LeaderboardListPane(
              scoresState = scoresState,
              currentUserId = user.uid,
              onRetry = { viewModel.refreshScores() },
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PlayerRankSummaryCard(
  profile: PlayerProfileState,
  email: String?,
  myRank: Int?,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = ViceSurface),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
    shape = RoundedCornerShape(18.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = NeonCyan.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
      ) {
        Text(
          text = "PROFIL PEMAIN AKTIF",
          style = MaterialTheme.typography.labelSmall,
          color = NeonCyan,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CharacterPalettes.outfitColors[profile.outfitColorIndex.coerceIn(0, 7)])
            .border(3.dp, CharacterPalettes.skinColors[profile.skinColorIndex.coerceIn(0, 5)], CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = "Avatar ${profile.nickname}",
            tint = ViceMidnight,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = profile.nickname,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (!email.isNullOrBlank()) {
            Text(
              text = email,
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = ViceSurfaceElevated),
        border = BorderStroke(1.dp, ViceCardBorder),
        shape = RoundedCornerShape(12.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("SKOR LOKAL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
              text = "$${formatScore(profile.cash.toLong())}",
              style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.labelLarge.fontFamily),
              color = ViceEmerald,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("PERINGKAT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
              text = if (myRank != null) "#$myRank" else "Belum Top 10",
              style = MaterialTheme.typography.titleLarge,
              color = if (myRank != null) ViceAmber else TextSecondary,
            )
          }
        }
      }

      Text(
        text = "Kumpulkan uang & selesaikan aksi di kota Vice Roam lalu tekan \"Kirim Skor Saya\" untuk masuk Top 10.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
    }
  }
}

@Composable
private fun LeaderboardListPane(
  scoresState: UiState<List<LeaderboardScoreDoc>>,
  currentUserId: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = ViceSurface),
    border = BorderStroke(1.dp, ViceCardBorder),
    shape = RoundedCornerShape(18.dp),
  ) {
    when (scoresState) {
      is UiState.Loading -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = NeonCyan)
            Text("Memuat Top 10 skor dari Firestore...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
          }
        }
      }

      is UiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = ViceCrimson, modifier = Modifier.size(36.dp))
            Text(scoresState.message, style = MaterialTheme.typography.bodyLarge, color = ViceCrimson)
            Button(
              onClick = onRetry,
              modifier = Modifier
                .height(48.dp)
                .testTag("leaderboard_retry_button"),
              colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ViceMidnight),
            ) {
              Text(stringResource(R.string.leaderboard_retry), fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      is UiState.Success -> {
        val entries = scoresState.data
        if (entries.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = ViceAmber, modifier = Modifier.size(42.dp))
              Text(
                text = stringResource(R.string.leaderboard_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("leaderboard_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            itemsIndexed(
              items = entries,
              key = { _, item -> item.userId.ifBlank { item.id } },
            ) { index, entry ->
              LeaderboardScoreRow(
                rank = index + 1,
                entry = entry,
                isCurrentUser = entry.userId == currentUserId,
                modifier = Modifier.testTag("leaderboard_row_$index"),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LeaderboardScoreRow(
  rank: Int,
  entry: LeaderboardScoreDoc,
  isCurrentUser: Boolean,
  modifier: Modifier = Modifier,
) {
  val rankAccent = when (rank) {
    1 -> ViceAmber
    2 -> Color(0xFFCBD5E1)
    3 -> SunsetCoral
    else -> NeonCyan
  }
  val borderColor = when {
    isCurrentUser -> ViceEmerald
    rank <= 3 -> rankAccent.copy(alpha = 0.7f)
    else -> ViceCardBorder
  }
  val containerColor = when {
    rank == 1 -> ViceAmber.copy(alpha = 0.12f)
    isCurrentUser -> ViceEmerald.copy(alpha = 0.10f)
    else -> ViceSurfaceElevated
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(if (rank <= 3 || isCurrentUser) 1.5.dp else 1.dp, borderColor),
    shape = RoundedCornerShape(14.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Rank Medal / Number
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(rankAccent.copy(alpha = if (rank <= 3) 0.22f else 0.12f))
          .border(1.dp, rankAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) {
        if (rank <= 3) {
          Icon(
            imageVector = if (rank == 1) Icons.Filled.EmojiEvents else Icons.Filled.MilitaryTech,
            contentDescription = "Peringkat $rank",
            tint = rankAccent,
            modifier = Modifier.size(22.dp),
          )
        } else {
          Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge,
            color = rankAccent,
          )
        }
      }

      // Player Avatar
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(CharacterPalettes.outfitColors[entry.outfitColorIndex.toInt().coerceIn(0, 7)])
          .border(2.5.dp, CharacterPalettes.skinColors[entry.skinColorIndex.toInt().coerceIn(0, 5)], CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Person,
          contentDescription = null,
          tint = ViceMidnight,
          modifier = Modifier.size(20.dp),
        )
      }

      // Nickname + Rank Subtitle
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = entry.nickname.ifBlank { "StreetAce" },
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (isCurrentUser) {
            Surface(
              color = ViceEmerald.copy(alpha = 0.22f),
              border = BorderStroke(1.dp, ViceEmerald),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = stringResource(R.string.leaderboard_you_badge),
                style = MaterialTheme.typography.labelSmall,
                color = ViceEmerald,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
        Text(
          text = "Peringkat #$rank",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      // Score Value
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "$${formatScore(entry.score)}",
          style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
            fontWeight = FontWeight.Bold,
          ),
          color = if (rank == 1) ViceAmber else ViceEmerald,
        )
        Text(
          text = "SKOR",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }
    }
  }
}

private fun formatScore(value: Long): String =
  NumberFormat.getNumberInstance(Locale.US).format(value)
