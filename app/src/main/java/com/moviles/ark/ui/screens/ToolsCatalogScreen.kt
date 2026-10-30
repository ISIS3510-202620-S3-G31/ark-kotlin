package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.domain.models.ToolModel
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.ScreenTopPadding
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.SoreanFontFamily
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.HomeUiState
import com.moviles.ark.ui.viewmodels.HomeViewModel

@Composable
fun ToolsCatalogRoute(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onNavigateToTool: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onHomeShown()
    }

    LaunchedEffect(uiState.navigateToToolId) {
        uiState.navigateToToolId?.let { toolId ->
            onNavigateToTool(toolId)
            viewModel.onNavigatedToTool()
        }
    }

    ToolsCatalogScreen(
        uiState = uiState,
        onCategorySelect = viewModel::onCategorySelect,
        onToolClick = viewModel::onToolClick
    )
}

@Composable
fun ToolsCatalogScreen(
    uiState: HomeUiState = HomeUiState(
        tools = ToolModel.DEFAULT_CATALOG,
        filteredTools = ToolModel.DEFAULT_CATALOG,
        availableCategories = ToolModel.availableCategories(ToolModel.DEFAULT_CATALOG, null)
    ),
    onCategorySelect: (ToolCategory?) -> Unit = {},
    onToolClick: (String) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            //barra superior propia de la pantalla del catalogo
            ToolsCatalogTopBar(totalCount = uiState.filteredTools.size)

            //grid completa de herramientas de regulacion con filtros de categoria
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                //filtros de categoria colocados al inicio de la cuadricula
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogCategoryFilterRow(
                        //solo las categorias que tienen alguna herramienta (las calcula el viewmodel)
                        categories = uiState.availableCategories,
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelect = onCategorySelect
                    )
                }

                //items de herramientas en formato cuadrícula
                items(uiState.filteredTools, key = { it.id }) { tool ->
                    ToolGridCardItem(
                        tool = tool,
                        onClick = { onToolClick(tool.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsCatalogTopBar(
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopPadding, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Regulation Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = SoreanFontFamily,
                    color = TextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Find the practice that fits your moment",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }

            Surface(
                shape = CircleShape,
                color = SecondaryColor.copy(alpha = 0.18f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Catalog",
                        tint = SecondaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogCategoryFilterRow(
    categories: List<ToolCategory>,
    selectedCategory: ToolCategory?,
    onCategorySelect: (ToolCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            CatalogCategoryChip(
                label = "All",
                isSelected = selectedCategory == null,
                onClick = { onCategorySelect(null) }
            )
        }
        items(categories) { category ->
            CatalogCategoryChip(
                label = category.displayName,
                isSelected = selectedCategory == category,
                onClick = { onCategorySelect(category) }
            )
        }
    }
}

@Composable
private fun CatalogCategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) PrimaryColor else Color(0xFFEFE0C2),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TextColor,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = TextColor,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ToolGridCardItem(
    tool: ToolModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFE0C2)),
        border = BorderStroke(1.dp, TextColor.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCatalogToolIcon(tool.id),
                        contentDescription = tool.name,
                        tint = TextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                //badge de formato (touch, voice, photo, text)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = tool.format.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = SecondaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun getCatalogToolIcon(toolId: String): ImageVector {
    return when (toolId) {
        "blow_it_out" -> Icons.Default.Whatshot
        "photo_of_the_day" -> Icons.Default.CameraAlt
        "custom_breathing" -> Icons.Default.Air
        "achievement_jar" -> Icons.Default.EmojiEvents
        "scream_tank" -> Icons.Default.Mic
        "body_mapping" -> Icons.Default.AccessibilityNew
        "emotion_detective" -> Icons.Default.Psychology
        else -> Icons.Default.AutoAwesome
    }
}

@Preview(showBackground = true)
@Composable
fun ToolsCatalogScreenPreview() {
    AppTheme {
        ToolsCatalogScreen()
    }
}
//Ahora solo es la lista de las herramientas y el filtro