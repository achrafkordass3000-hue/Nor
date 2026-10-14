package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.network.OnlyhavenCreator
import com.example.network.OnlyhavenMediaItem
import com.example.network.OnlyhavenScraper
import com.example.ui.MainViewModel
import com.example.ui.components.AppLottieLoadingAnimation
import com.example.ui.components.Miniplayer
import com.example.ui.components.ShimmerBrush
import com.example.ui.components.ZoomableImage
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette
import kotlinx.coroutines.launch

enum class CreatorMediaTab {
    ALL,
    IMAGES,
    VIDEOS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorDetailsScreen(
    viewModel: MainViewModel,
    creator: OnlyhavenCreator
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(CreatorMediaTab.ALL) }
    var mediaItems by remember { mutableStateOf<List<OnlyhavenMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var canLoadMore by remember { mutableStateOf(true) }
    val savedKeys by viewModel.savedCreatorKeys.collectAsStateWithLifecycle()
    val isSaved = savedKeys.contains("${creator.service}_${creator.id}")
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedPreviewItem by remember { mutableStateOf<OnlyhavenMediaItem?>(null) }
    var initialPreviewIndex by remember { mutableIntStateOf(0) }

    val scrollKey = remember(creator.id, creator.service) { "onlyhaven_creator_${creator.service}_${creator.id}" }
    val initialScroll = remember(scrollKey) { viewModel.getScrollPosition(scrollKey) }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScroll.first,
        initialFirstVisibleItemScrollOffset = initialScroll.second
    )

    LaunchedEffect(gridState, scrollKey) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (mediaItems.isNotEmpty()) {
                    viewModel.saveScrollPosition(scrollKey, index, offset)
                }
            }
    }

    var hasRestoredInitialScroll by rememberSaveable(creator.id) { mutableStateOf(false) }
    LaunchedEffect(mediaItems.isNotEmpty()) {
        if (mediaItems.isNotEmpty() && !hasRestoredInitialScroll) {
            val (savedIndex, savedOffset) = viewModel.getScrollPosition(scrollKey)
            if (savedIndex > 0 || savedOffset > 0) {
                if (savedIndex < mediaItems.size) {
                    gridState.scrollToItem(savedIndex, savedOffset)
                }
            }
            hasRestoredInitialScroll = true
        }
    }

    var previousTab by rememberSaveable { mutableStateOf(activeTab.name) }
    LaunchedEffect(activeTab) {
        if (previousTab != activeTab.name) {
            previousTab = activeTab.name
            if (mediaItems.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        }
    }

    fun loadMedia(page: Int, append: Boolean = false) {
        coroutineScope.launch {
            if (append) isLoadingMore = true else {
                isLoading = true
                errorMessage = null
            }
            try {
                val fetched = OnlyhavenScraper.getCreatorMedia(
                    service = creator.service,
                    creatorId = creator.id,
                    creatorName = creator.name,
                    page = page
                )
                if (append) {
                    val existingIds = mediaItems.map { it.id }.toSet()
                    val newUnique = fetched.filter { it.id !in existingIds }
                    mediaItems = mediaItems + newUnique
                } else {
                    mediaItems = fetched.distinctBy { it.id }
                }
                canLoadMore = fetched.isNotEmpty()
                errorMessage = null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!append) {
                    errorMessage = e.message ?: "Failed to load creator media"
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(creator.id, creator.name) {
        loadMedia(1)
    }

    // Infinite scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && canLoadMore) {
            val nextPage = currentPage + 1
            currentPage = nextPage
            loadMedia(nextPage, append = true)
        }
    }

    // Filter media items based on activeTab
    val displayedItems = remember(mediaItems, activeTab) {
        when (activeTab) {
            CreatorMediaTab.ALL -> mediaItems
            CreatorMediaTab.IMAGES -> mediaItems.filter { item ->
                item.postAttachments.any { it.kind == "image" } || item.kind == "image"
            }
            CreatorMediaTab.VIDEOS -> mediaItems.filter { item ->
                item.postAttachments.any { it.kind == "video" } || item.kind == "video"
            }
        }
    }

    BackHandler {
        if (selectedPreviewItem != null) {
            selectedPreviewItem = null
        } else {
            viewModel.navigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = palette.bg,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                com.example.ui.components.AppTophead(
                    title = "Creator Details",
                    navIconType = com.example.ui.components.NavIconType.BACK,
                    onNavClick = { viewModel.navigateBack() },
                    showBorder = false,
                    actions = {
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

                        IconButton(
                            onClick = {
                                isPopAnimating = true
                                viewModel.toggleSaveOnlyhavenCreator(creator)
                            },
                            modifier = Modifier
                                .scale(heartScale)
                                .testTag("save_creator_details_button")
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                                ),
                                contentDescription = if (isSaved) "Unsave Creator" else "Save Creator",
                                tint = if (isSaved) Color(0xFFFF3B5C) else palette.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 1. Creator Profile Header Info
                Surface(
                    color = palette.bg,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circular Avatar
                            if (creator.avatarUrl.isNotEmpty()) {
                                val context = LocalContext.current
                                val avatarRequest = remember(creator.avatarUrl) {
                                    ImageRequest.Builder(context)
                                        .data(creator.avatarUrl)
                                        .crossfade(true)
                                        .crossfade(350)
                                        .build()
                                }
                                var isAvatarLoaded by remember(creator.avatarUrl) { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .border(2.5.dp, accent, CircleShape)
                                        .background(palette.border),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isAvatarLoaded) {
                                        val shimmerBrush = ShimmerBrush(targetValue = 900f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(palette.cardBg)
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
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.2f))
                                        .border(2.5.dp, accent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = creator.name.take(2).uppercase(),
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val displayTitle = creator.displayName.ifBlank { creator.name }
                            Text(
                                text = displayTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (creator.displayName.isNotBlank() && !creator.displayName.equals(creator.name, ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${creator.name}",
                                    fontSize = 14.sp,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 2. Tab Selector Row (ALL, Images, Videos)
                        val indicatorBias by animateFloatAsState(
                            targetValue = when (activeTab) {
                                CreatorMediaTab.ALL -> -1f
                                CreatorMediaTab.IMAGES -> 0f
                                CreatorMediaTab.VIDEOS -> 1f
                            },
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            label = "creator_tab_indicator_bias"
                        )

                        val allTabColor by animateColorAsState(
                            targetValue = if (activeTab == CreatorMediaTab.ALL) accent else palette.textSecondary,
                            animationSpec = tween(durationMillis = 180),
                            label = "all_tab_color"
                        )
                        val imagesTabColor by animateColorAsState(
                            targetValue = if (activeTab == CreatorMediaTab.IMAGES) accent else palette.textSecondary,
                            animationSpec = tween(durationMillis = 180),
                            label = "images_tab_color"
                        )
                        val videosTabColor by animateColorAsState(
                            targetValue = if (activeTab == CreatorMediaTab.VIDEOS) accent else palette.textSecondary,
                            animationSpec = tween(durationMillis = 180),
                            label = "videos_tab_color"
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { activeTab = CreatorMediaTab.ALL }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ALL",
                                            fontWeight = if (activeTab == CreatorMediaTab.ALL) FontWeight.Bold else FontWeight.SemiBold,
                                            color = allTabColor,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { activeTab = CreatorMediaTab.IMAGES }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Images",
                                            fontWeight = if (activeTab == CreatorMediaTab.IMAGES) FontWeight.Bold else FontWeight.SemiBold,
                                            color = imagesTabColor,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { activeTab = CreatorMediaTab.VIDEOS }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Videos",
                                            fontWeight = if (activeTab == CreatorMediaTab.VIDEOS) FontWeight.Bold else FontWeight.SemiBold,
                                            color = videosTabColor,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(1f / 3f)
                                            .fillMaxHeight()
                                            .align(BiasAlignment(indicatorBias, 0f))
                                            .background(accent)
                                    )
                                }
                            }
                            HorizontalDivider(color = palette.border)
                        }
                    }
                }

                // 3. Instagram-Style 4:5 Vertical Portrait Grid
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (isLoading && mediaItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppLottieLoadingAnimation(modifier = Modifier.size(64.dp))
                        }
                    } else if (errorMessage != null && mediaItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage ?: "Failed to load media",
                                    color = palette.textSecondary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { loadMedia(1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    } else if (displayedItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No ${activeTab.name.lowercase()} found",
                                color = palette.textSecondary,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                start = 2.dp,
                                end = 2.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp,
                                top = 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayedItems,
                                key = { item -> "${item.postId}_${item.sha256}" }
                            ) { item ->
                                val targetAttachment = if (activeTab == CreatorMediaTab.VIDEOS) {
                                    item.postAttachments.firstOrNull { it.kind == "video" } ?: item
                                } else {
                                    item.postAttachments.firstOrNull() ?: item
                                }
                                val context = LocalContext.current
                                val thumbRequest = remember(targetAttachment.thumbnailUrl) {
                                    ImageRequest.Builder(context)
                                        .data(targetAttachment.thumbnailUrl)
                                        .crossfade(true)
                                        .crossfade(350)
                                        .build()
                                }
                                var isThumbLoaded by remember(targetAttachment.thumbnailUrl) { mutableStateOf(false) }
                                var isThumbError by remember(targetAttachment.thumbnailUrl) { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 5f)
                                        .background(palette.cardBg)
                                        .clip(RoundedCornerShape(3.dp))
                                        .clickable {
                                            initialPreviewIndex = if (activeTab == CreatorMediaTab.VIDEOS) {
                                                val idx = item.postAttachments.indexOfFirst { it.kind == "video" }
                                                if (idx >= 0) idx else 0
                                            } else 0
                                            selectedPreviewItem = item
                                        }
                                ) {
                                    if (!isThumbLoaded && !isThumbError) {
                                        val shimmerBrush = ShimmerBrush(targetValue = 900f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(palette.cardBg)
                                                .background(shimmerBrush),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppLottieLoadingAnimation(modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    AsyncImage(
                                        model = thumbRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        onSuccess = { isThumbLoaded = true },
                                        onError = {
                                            isThumbLoaded = true
                                            isThumbError = true
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (item.isMultiMedia) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_instagram_carousel),
                                            contentDescription = "Multiple Items",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(20.dp)
                                        )
                                    } else if (targetAttachment.kind == "video") {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_player_play),
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppLottieLoadingAnimation(modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Full-Screen Edge-to-Edge Immersive Preview Overlay (Pure AMOLED Black, Zero System Scrim)
    AnimatedVisibility(
        visible = selectedPreviewItem != null,
        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing))
    ) {
        selectedPreviewItem?.let { previewItem ->
            val postAttachments = remember(previewItem) {
                previewItem.postAttachments.ifEmpty { listOf(previewItem) }
            }
            val safeInitialPage = initialPreviewIndex.coerceIn(0, (postAttachments.size - 1).coerceAtLeast(0))
            val pagerState = rememberPagerState(
                initialPage = safeInitialPage,
                pageCount = { postAttachments.size }
            )

            val zoomedPagesState = remember { mutableStateMapOf<Int, Boolean>() }
            val isCurrentPageZoomed = zoomedPagesState[pagerState.currentPage] == true

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isCurrentPageZoomed,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageItem = postAttachments[page]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageItem.kind == "video") {
                            Miniplayer(
                                videoUrl = pageItem.originalUrl,
                                title = pageItem.originalFilename.ifEmpty { "Onlyhaven Video" },
                                isActiveInPager = pagerState.currentPage == page,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ZoomableImage(
                                model = pageItem.originalUrl,
                                contentDescription = "Original Quality Image",
                                resetKey = page,
                                onZoomChanged = { zoomed ->
                                    zoomedPagesState[page] = zoomed
                                },
                                onNavigateNext = {
                                    if (pagerState.currentPage < postAttachments.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                onNavigatePrevious = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(
                            onClick = { selectedPreviewItem = null },
                            modifier = Modifier.testTag("preview_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (postAttachments.size > 1) {
                            Box(
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${postAttachments.size}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                )
            }
        }
    }
}
