package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.example.network.OnlyhavenSortMode
import com.example.ui.components.vaultTopGlow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val currentSort by viewModel.onlyhavenSortMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isOnlyhavenLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isOnlyhavenLoadingMore.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.onlyhavenCanLoadMore.collectAsStateWithLifecycle()
    val currentPage by viewModel.onlyhavenCurrentPage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.onlyhavenErrorMessage.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }

    val scrollKey = "onlyhaven_creators"
    val initialScroll = remember(scrollKey) { viewModel.getScrollPosition(scrollKey) }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScroll.first,
        initialFirstVisibleItemScrollOffset = initialScroll.second
    )

    // Continuously save scroll position into ViewModel registry
    LaunchedEffect(gridState, scrollKey) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (creators.isNotEmpty()) {
                    viewModel.saveScrollPosition(scrollKey, index, offset)
                }
            }
    }

    // Restore scroll position if creators were loaded asynchronously
    var hasRestoredInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(creators.isNotEmpty()) {
        if (creators.isNotEmpty() && !hasRestoredInitialScroll) {
            val (savedIndex, savedOffset) = viewModel.getScrollPosition(scrollKey)
            if (savedIndex > 0 || savedOffset > 0) {
                if (savedIndex < creators.size) {
                    gridState.scrollToItem(savedIndex, savedOffset)
                }
            }
            hasRestoredInitialScroll = true
        }
    }

    var previousQuery by rememberSaveable { mutableStateOf(searchQuery) }
    var previousSort by rememberSaveable { mutableStateOf(currentSort.name) }
    var needsScrollToTop by remember { mutableStateOf(false) }

    // Reset scroll position to top when search query changes
    LaunchedEffect(searchQuery) {
        if (previousQuery != searchQuery) {
            previousQuery = searchQuery
            needsScrollToTop = true
            viewModel.saveScrollPosition(scrollKey, 0, 0)
            gridState.scrollToItem(0, 0)
        }
    }

    // Reset scroll position to top when sort mode changes
    LaunchedEffect(currentSort) {
        if (previousSort != currentSort.name) {
            previousSort = currentSort.name
            needsScrollToTop = true
            viewModel.saveScrollPosition(scrollKey, 0, 0)
            gridState.scrollToItem(0, 0)
        }
    }

    // Ensure top position is applied when new creators arrive after sort or query change
    LaunchedEffect(creators) {
        if (needsScrollToTop && creators.isNotEmpty()) {
            gridState.scrollToItem(0, 0)
            viewModel.saveScrollPosition(scrollKey, 0, 0)
            needsScrollToTop = false
        }
    }

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
                        // Sort Action with Dropdown Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_onlyhaven_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_app_sort),
                                    contentDescription = "Sort Creators",
                                    tint = palette.textPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier
                                    .vaultTopGlow(cornerRadius = 16.dp)
                                    .widthIn(min = 210.dp),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = palette.cardBg
                            ) {
                                OnlyhavenSortMode.entries.forEach { mode ->
                                    val isSelected = currentSort == mode

                                    val animatedTextColor by animateColorAsState(
                                        targetValue = if (isSelected) accent else palette.textPrimary,
                                        animationSpec = tween(durationMillis = 220),
                                        label = "sort_text_color"
                                    )
                                    val animatedBgColor by animateColorAsState(
                                        targetValue = if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent,
                                        animationSpec = tween(durationMillis = 220),
                                        label = "sort_bg_color"
                                    )

                                    DropdownMenuItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(animatedBgColor),
                                        text = {
                                            Text(
                                                text = mode.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = animatedTextColor,
                                                fontSize = 14.5.sp
                                            )
                                        },
                                        leadingIcon = {
                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.6f, animationSpec = tween(180)),
                                                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.6f, animationSpec = tween(140))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (currentSort != mode) {
                                                viewModel.setOnlyhavenSortMode(mode)
                                            }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

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
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 116.dp,
                        start = 14.dp,
                        end = 14.dp
                    ),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(creators, key = { "row_${it.service}_${it.id}" }) { creator ->
                        OnlyhavenRowCard(
                            creator = creator,
                            palette = palette,
                            accent = accent,
                            isLight = isLight,
                            circleBorderColor = circleBorderColor,
                            onClick = {
                                viewModel.navigateTo(com.example.ui.ScreenState.OnlyhavenCreatorDetails(creator))
                            }
                        )
                    }

                    if (isLoadingMore) {
                        item {
                            LoadingMoreIndicator(accent = accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyGridItemScope.OnlyhavenRowCard(
    creator: OnlyhavenCreator,
    palette: com.example.ui.theme.VaultPalette,
    accent: Color,
    isLight: Boolean,
    circleBorderColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarRequest = remember(creator.avatarUrl) {
        ImageRequest.Builder(context)
            .data(creator.avatarUrl)
            .crossfade(true)
            .crossfade(350)
            .build()
    }

    Card(
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("creator_row_${creator.id}"),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isLight) 2.5.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Avatar with circle border & letter placeholder fallback (larger circle)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.surface),
                contentAlignment = Alignment.Center
            ) {
                if (creator.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarRequest,
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

            Spacer(modifier = Modifier.width(16.dp))

            // 2. Creator Name, Handle & Stats (Without service badge & without right chevron button)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val label = creator.displayName.ifBlank { creator.name }
                Text(
                    text = label,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "@${creator.name}",
                    color = palette.textSecondary,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (creator.postCount > 0 || creator.imageCount > 0 || creator.videoCount > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    val statsText = buildString {
                        if (creator.postCount > 0) append("${creator.postCount} posts")
                        if (creator.videoCount > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${creator.videoCount} videos")
                        }
                    }
                    if (statsText.isNotEmpty()) {
                        Text(
                            text = statsText,
                            color = palette.textSecondary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingMoreIndicator(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp))
    }
}
