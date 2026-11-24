package com.moviles.ark.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.components.ActiveFortuneCard
import com.moviles.ark.ui.components.FortuneCookiesRow
import com.moviles.ark.ui.components.RecentFortunesCarousel
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortuneUiState

//ruta composable que conecta el viewmodel con la pantalla
@Composable
fun FortuneCookieRoute(
    onBack: (wasCompleted: Boolean, durationSeconds: Int) -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        val (completed, duration) = viewModel.finishSession()
        onBack(completed, duration)
    }

    FortuneCookieScreen(
        uiState = uiState,
        onBack = {
            val (completed, duration) = viewModel.finishSession()
            onBack(completed, duration)
        },
        onOpenFavorites = onOpenFavorites,
        onCrackCookie = { index -> viewModel.crackCookie(index) },
        onToggleFavorite = { fortune -> viewModel.toggleFavorite(fortune) },
        onSelectRecent = { fortune -> viewModel.selectRecentCookie(fortune) },
        onShareCard = {
            viewModel.exportActiveCard { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Fortune Card"))
            }
        },
        onClearSnackbar = { viewModel.clearSnackbar() }
    )
}

//pantalla principal de la galleta de la fortuna
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortuneCookieScreen(
    uiState: FortuneUiState,
    onBack: () -> Unit,
    onOpenFavorites: () -> Unit,
    onCrackCookie: (Int) -> Unit,
    onToggleFavorite: (FortuneModel) -> Unit,
    onSelectRecent: (FortuneModel) -> Unit,
    onShareCard: () -> Unit,
    onClearSnackbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fortune Cookie",
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFavorites) {
                        BadgedBox(
                            badge = {
                                if (uiState.favoriteFortunes.isNotEmpty()) {
                                    Badge(
                                        containerColor = PrimaryColor,
                                        contentColor = Color.White
                                    ) {
                                        Text("${uiState.favoriteFortunes.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = "Favorites & Saved Cards",
                                tint = TextColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //indicador de intentos restantes disponibles hoy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Chances",
                    fontSize = 14.sp,
                    color = TextColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${uiState.remainingChances} / 5 left today",
                        color = PrimaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            //fila interactiva de las tres galletas rompibles
            FortuneCookiesRow(
                crackingIndex = uiState.crackingCookieIndex,
                openedCookies = uiState.openedCookies,
                onCrackCookie = onCrackCookie
            )

            Spacer(modifier = Modifier.height(10.dp))

            //tarjeta activa con la frase o mensaje de instruccion
            val active = uiState.activeFortune
            if (active != null) {
                ActiveFortuneCard(
                    fortune = active,
                    sourceBadge = uiState.sourceBadge,
                    isExporting = uiState.isExporting,
                    onToggleFavorite = { onToggleFavorite(active) },
                    onShare = onShareCard,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Crack a cookie",
                            tint = PrimaryColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap any unopened cookie above to crack it open and receive your daily wisdom and lucky numbers.",
                            color = TextColor.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            //carrusel de galletas recientes
            RecentFortunesCarousel(
                recents = uiState.recentFortunes,
                onSelectFortune = onSelectRecent,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
