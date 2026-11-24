package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.ui.theme.AccentColor
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.BackgroundLightColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.FortuneCookieViewModel
import com.moviles.ark.ui.viewmodels.FortuneUiState

//ruta composable que conecta la pantalla de favoritos con el viewmodel
@Composable
fun FavoriteFortunesRoute(
    onBack: () -> Unit,
    viewModel: FortuneCookieViewModel = viewModel(factory = FortuneCookieViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FavoriteFortunesScreen(
        uiState = uiState,
        onBack = onBack,
        onDeleteFavorite = { fortune -> viewModel.toggleFavorite(fortune) }
    )
}

//pantalla de frases favoritas y galeria de tarjetas guardadas en la nube
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFortunesScreen(
    uiState: FortuneUiState,
    onBack: () -> Unit,
    onDeleteFavorite: (FortuneModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var itemToDelete by remember { mutableStateOf<FortuneModel?>(null) }

    //dialogo de confirmacion para eliminar de favoritos
    val toDelete = itemToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Remove Favorite", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this fortune from your favorites?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFavorite(toDelete)
                        itemToDelete = null
                    }
                ) {
                    Text("Remove", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Saved Fortunes",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            //pestanas para alternar entre frases favoritas y tarjetas visuales
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundColor,
                contentColor = PrimaryColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PrimaryColor
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Favorites (${uiState.favoriteFortunes.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Cards Gallery (${uiState.savedCards.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                //listado lazycolumn de frases favoritas guardadas en room
                if (uiState.favoriteFortunes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = PrimaryColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No favorites saved yet.\nTap the heart icon on any opened fortune to keep it here.",
                                color = TextColor.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.favoriteFortunes, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = BackgroundLightColor
                                            ) {
                                                Text(
                                                    text = item.category.uppercase(),
                                                    color = PrimaryColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Lucky #${item.luckyNumber}",
                                                color = SecondaryColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "\"${item.quote}\"",
                                            color = TextColor,
                                            fontSize = 15.sp,
                                            fontStyle = FontStyle.Italic,
                                            lineHeight = 20.sp
                                        )
                                    }

                                    IconButton(onClick = { itemToDelete = item }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete favorite",
                                            tint = AccentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                //galeria de tarjetas de la fortuna cargadas con coil
                if (uiState.savedCards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = SecondaryColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No saved cards yet.\nExport a fortune card using Save & Share to view it in your cloud gallery.",
                                color = TextColor.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.savedCards, key = { it.id }) { card ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    val imageModel = card.localImageUri.ifEmpty { card.remoteImageUrl }
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(imageModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Saved Card",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = card.category,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Icon(
                                            imageVector = if (card.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                            contentDescription = if (card.isSynced) "Synced with cloud" else "Local only",
                                            tint = if (card.isSynced) SecondaryColor else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
