package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.network.OnlyhavenCreator
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlyhavenScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val creators by viewModel.onlyhavenCreators.collectAsStateWithLifecycle()
    val searchQuery by viewModel.onlyhavenSearchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isOnlyhavenLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isOnlyhavenLoadingMore.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.onlyhavenCanLoadMore.collectAsStateWithLifecycle()
    val currentPage by viewModel.onlyhavenCurrentPage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.onlyhavenErrorMessage.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (creators.isEmpty() && !isLoading) {
            viewModel.loadOnlyhavenCreators(query = searchQuery, page = 1)
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && canLoadMore) {
            viewModel.loadOnlyhavenCreators(query = searchQuery, page = currentPage + 1, append = true)
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }

    val isLight = palette.name.equals("light", ignoreCase = true)
    val circleBorderColor = if (isLight) Color.Black else Color.White

    Scaffold(
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            com.example.ui.components.SearchableTophead(
                title = "Onlyhaven",
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setOnlyhavenSearchQuery(it) },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                placeholder = "Search creator (e.g. demon_mika)...",
                testTag = "search_creators_input",
                onBack = { viewModel.navigateBack() },
                showBorder = false,
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("search_creators_action_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_app_search),
                                contentDescription = "Search",
                                tint = palette.textPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && creators.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent)
                }
            } else if (errorMessage != null && creators.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "Failed to connect to Onlyhaven",
                            color = palette.textSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.loadOnlyhavenCreators(query = searchQuery, page = 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            } else if (creators.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No creators found", color = palette.textSecondary, fontSize = 16.sp)
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 116.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(creators, key = { "${it.service}_${it.id}" }) { creator ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.navigateTo(com.example.ui.ScreenState.OnlyhavenCreatorDetails(creator))
                                },
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isLight) 3.dp else 1.5.dp
                            ),
                            colors = CardDefaults.cardColors(containerColor = palette.cardBg)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 18.dp, start = 8.dp, end = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(palette.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (creator.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = creator.avatarUrl,
                                            contentDescription = creator.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(palette.cardBg.copy(alpha = 0.85f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = creator.name.take(2).uppercase(),
                                                color = accent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(1.5.dp, circleBorderColor, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val label = creator.displayName.ifBlank { creator.name }
                                Text(
                                    text = label,
                                    color = palette.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
