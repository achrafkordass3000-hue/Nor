package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.network.OnlyhavenCreator
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.components.SearchableTophead
import com.example.ui.components.ShimmerBrush
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

@Composable
fun SavedCreatorsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val savedCreators by viewModel.savedOnlyhavenCreators.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedCreatorKeys.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val filteredCreators = remember(savedCreators, searchQuery) {
        if (searchQuery.isBlank()) {
            savedCreators
        } else {
            val q = searchQuery.trim().lowercase()
            savedCreators.filter {
                it.name.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.service.lowercase().contains(q)
            }
        }
    }

    val gridState = rememberLazyGridState()

    BackHandler {
        viewModel.navigateBack()
    }

    val isLight = palette.name.equals("light", ignoreCase = true)
    val circleBorderColor = if (isLight) Color.Black else Color.White

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            SearchableTophead(
                title = if (savedCreators.isNotEmpty()) "Saved Creators (${savedCreators.size})" else "Saved Creators",
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                placeholder = "Search saved creators...",
                testTag = "search_saved_creators_input",
                onBack = { viewModel.navigateBack() },
                showBorder = false,
                actions = {
                    if (!isSearchExpanded && savedCreators.isNotEmpty()) {
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("search_saved_creators_action_button")
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
            if (savedCreators.isEmpty()) {
                // Empty state when user has not saved any creators yet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3B5C).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_heart_filled),
                                contentDescription = null,
                                tint = Color(0xFFFF3B5C),
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Text(
                            text = "No saved creators yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary,
                            fontSize = 18.sp
                        )

                        Text(
                            text = "Browse creators on Onlyhaven and tap the heart icon on any creator to save them here for quick access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { viewModel.navigateTo(ScreenState.Onlyhaven) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Explore Creators", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (filteredCreators.isEmpty()) {
                // Filtered search query yielded no results
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No saved creators matching \"$searchQuery\"",
                            color = palette.textSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("Clear search", color = accent)
                        }
                    }
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
                    items(filteredCreators, key = { "saved_${it.service}_${it.id}" }) { creator ->
                        val isSaved = savedKeys.contains("${creator.service}_${creator.id}")

                        SavedCreatorItemRow(
                            creator = creator,
                            palette = palette,
                            accent = accent,
                            isLight = isLight,
                            circleBorderColor = circleBorderColor,
                            isSaved = isSaved,
                            onToggleSave = {
                                viewModel.toggleSaveOnlyhavenCreator(creator)
                            },
                            onClick = {
                                viewModel.navigateTo(ScreenState.OnlyhavenCreatorDetails(creator))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedCreatorItemRow(
    creator: OnlyhavenCreator,
    palette: com.example.ui.theme.VaultPalette,
    accent: Color,
    isLight: Boolean,
    circleBorderColor: Color,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarRequest = remember(creator.avatarUrl) {
        ImageRequest.Builder(context)
            .data(creator.avatarUrl)
            .crossfade(true)
            .crossfade(350)
            .build()
    }

    var isPopAnimating by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (isPopAnimating) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_pop",
        finishedListener = { isPopAnimating = false }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("saved_creator_row_${creator.id}"),
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
            // 1. Avatar with circle border & letter placeholder fallback
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.surface),
                contentAlignment = Alignment.Center
            ) {
                if (creator.avatarUrl.isNotEmpty()) {
                    var isAvatarLoaded by remember(creator.avatarUrl) { mutableStateOf(false) }
                    if (!isAvatarLoaded) {
                        val shimmerBrush = ShimmerBrush(targetValue = 900f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.surface)
                                .background(shimmerBrush)
                        )
                    }
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = creator.name,
                        contentScale = ContentScale.Crop,
                        onSuccess = { isAvatarLoaded = true },
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

            // 2. Creator Name, Handle & Stats
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

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Heart Save / Unsave Button on the Right (No circle ripple effect)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(heartScale)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isPopAnimating = true
                        onToggleSave()
                    }
                    .testTag("toggle_save_creator_${creator.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                    ),
                    contentDescription = if (isSaved) "Unsave Creator" else "Save Creator",
                    tint = if (isSaved) Color(0xFFFF3B5C) else palette.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
