package com.example.ui.components

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.video.spherical.SphericalGLSurfaceView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.network.StreamQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerMode {
    FULLSCREEN,
    INLINE_CARD
}

/**
 * Unified Modern High-Performance Media Player Engine
 */
@OptIn(UnstableApi::class)
@Composable
fun CoreMediaPlayer(
    mode: PlayerMode,
    title: String,
    qualities: List<StreamQuality>,
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    enableGestures: Boolean = true,
    onClose: () -> Unit,
    onFullscreen: ((currentPositionMs: Long) -> Unit)? = null,
    onFullscreenWithMode: ((currentPositionMs: Long, startInLandscape: Boolean) -> Unit)? = null,
    onEnterPip: ((currentPositionMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isFullscreen = mode == PlayerMode.FULLSCREEN

    val fallbackPlayer = remember(context, exoPlayer == null) {
        if (exoPlayer == null) PlayerFactory.createPlayer(context) else null
    }
    val activeExoPlayer = exoPlayer ?: fallbackPlayer ?: remember(context) { PlayerFactory.createPlayer(context) }

    DisposableEffect(fallbackPlayer) {
        onDispose { fallbackPlayer?.release() }
    }

    var activeQualityIndex by remember(qualities) {
        val defaultIdx = qualities.indexOfFirst { it.isDefault }
        mutableIntStateOf(if (defaultIdx >= 0) defaultIdx else 0)
    }
    val primaryQuality = remember(qualities, activeQualityIndex) {
        qualities.getOrNull(activeQualityIndex) ?: qualities.firstOrNull()
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPos by remember {
        mutableLongStateOf(initialPositionMs.coerceAtLeast(0L))
    }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    if (isFullscreen) {
        LaunchedEffect(startInLandscape) {
            if (startInLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetails by remember { mutableStateOf<String?>(null) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    var isGestureSeeking by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxAudioVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var gestureVolumePercent by remember { mutableStateOf<Int?>(null) }
    var gestureBrightnessPercent by remember { mutableStateOf<Int?>(null) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }

    var isInPipMode by remember {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }

    var isVrMode by remember { mutableStateOf(false) }
    var vrUseGyro by remember { mutableStateOf(true) }
    var vrRecenterTrigger by remember { mutableIntStateOf(0) }
    var sphericalViewRef by remember { mutableStateOf<SphericalGLSurfaceView?>(null) }
    var vrStereoMode by remember(primaryQuality?.url) {
        val url = primaryQuality?.url?.lowercase() ?: ""
        val titleLower = title.lowercase()
        // Accurate VR 180 / SBS / 3D detection avoiding false positive number IDs
        val sbsRegex = Regex("""\b(vr180|180vr|half-sbs|sbs)\b""")
        val topBottomRegex = Regex("""\b(over-under|top-bottom|topbottom|half-ou|ou)\b""")
        val isSbs = sbsRegex.containsMatchIn(url) || sbsRegex.containsMatchIn(titleLower)
        val isTopBottom = topBottomRegex.containsMatchIn(url) || topBottomRegex.containsMatchIn(titleLower)
        mutableIntStateOf(
            when {
                isTopBottom -> C.STEREO_MODE_TOP_BOTTOM
                isSbs -> C.STEREO_MODE_LEFT_RIGHT
                else -> C.STEREO_MODE_MONO
            }
        )
    }

    LaunchedEffect(activeExoPlayer.videoFormat) {
        val formatStereo = activeExoPlayer.videoFormat?.stereoMode
        if (formatStereo != null && formatStereo != androidx.media3.common.Format.NO_VALUE && formatStereo != C.STEREO_MODE_MONO) {
            vrStereoMode = formatStereo
        }
    }

    // Lifecycle binding for SphericalGLSurfaceView (prevents gyroscope sensor & GL thread battery drain in background)
    DisposableEffect(activity, isVrMode, sphericalViewRef) {
        val compAct = activity as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (isVrMode) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        sphericalViewRef?.onPause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        sphericalViewRef?.onResume()
                    }
                    else -> Unit
                }
            }
        }
        compAct?.lifecycle?.addObserver(observer)
        onDispose {
            compAct?.lifecycle?.removeObserver(observer)
            if (isVrMode) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                showControls = false
            }
        }
        val compAct = activity as? ComponentActivity
        compAct?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            compAct?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    val handleBackAction = {
        if (isVrMode) {
            isVrMode = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else if (isFullscreen && isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (!isInPipMode) {
            onClose()
        }
    }

    if (isFullscreen || isVrMode) {
        BackHandler { handleBackAction() }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isScrubbing, isGestureSeeking) {
        if (showControls && isPlaying && !isScrubbing && !isGestureSeeking) {
            delay(4500)
            showControls = false
        }
    }

    var isRealHdrStream by remember { mutableStateOf(false) }

    // Platform effects (System bars, brightness, wide color gamut)
    ManageFullscreenWindowEffects(
        activity = activity,
        isFullscreen = isFullscreen,
        isRealHdrStream = isRealHdrStream
    )

    // Lifecycle binding (Preserving PiP playback)
    val lifecycleOwner = LocalLifecycleOwner.current
    BindPlayerLifecycle(lifecycleOwner, activeExoPlayer, activity)

    val activeHeaders = remember(primaryQuality, defaultHeaders) {
        val streamHeaders = primaryQuality?.headers ?: emptyMap()
        defaultHeaders + streamHeaders
    }

    // Player state & format listener
    BindPlayerStateListener(
        player = activeExoPlayer,
        onStateChanged = { buffering, playing, dur ->
            isBuffering = buffering
            isPlaying = playing
            if (dur > 0) duration = dur
        },
        onFormatChanged = {
            isRealHdrStream = checkIsVideoRealHdr(activeExoPlayer)
        },
        onError = { title, details ->
            isBuffering = false
            if (activeQualityIndex < qualities.lastIndex &&
                (title.contains("Codec", ignoreCase = true) || title.contains("Decoder", ignoreCase = true) || title.contains("Limits", ignoreCase = true) || title.contains("Unsupported", ignoreCase = true))
            ) {
                val nextIdx = activeQualityIndex + 1
                val nextQuality = qualities[nextIdx]
                val currentQualityLabel = qualities.getOrNull(activeQualityIndex)?.quality ?: "Stream"
                Toast.makeText(context, "$currentQualityLabel exceeds hardware, trying ${nextQuality.quality}...", Toast.LENGTH_SHORT).show()
                activeQualityIndex = nextIdx
            } else {
                errorMessage = title
                errorDetails = details
            }
        },
        onClearError = {
            errorMessage = null
            errorDetails = null
        }
    )

    // Adaptive position tracking
    TrackPlayerPositions(
        player = activeExoPlayer,
        showControls = showControls,
        isScrubbing = isScrubbing,
        isGestureSeeking = isGestureSeeking,
        lastSeekTime = lastSeekTime,
        primaryQuality = primaryQuality,
        onTick = { dur, buf, pos ->
            if (dur > 0) duration = dur
            bufferedPos = buf
            if (pos >= 0) currentPos = pos
        }
    )

    // Media preparation
    PreparePlayerMediaSource(
        context = context,
        player = activeExoPlayer,
        primaryQuality = primaryQuality,
        activeHeaders = activeHeaders,
        initialPositionMs = initialPositionMs,
        onBufferingChanged = { isBuffering = it },
        onDurationChanged = { duration = it },
        onPositionChanged = { currentPos = it },
        onError = { title, details ->
            isBuffering = false
            errorMessage = title
            errorDetails = details
        },
        onClearError = {
            errorMessage = null
            errorDetails = null
        }
    )

    val playerGesturesModifier = if (!isVrMode) {
        Modifier.playerTouchGestures(
            duration = duration,
            enableGestures = enableGestures,
            currentPositionProvider = { activeExoPlayer.currentPosition },
            audioManager = audioManager,
            maxAudioVolume = maxAudioVolume,
            activity = activity,
            isScrubbing = isScrubbing,
            onGestureSeekingChanged = { seeking ->
                isGestureSeeking = seeking
                activeExoPlayer.setSeekParameters(if (seeking) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
            },
            onSeekLive = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
            },
            onSeekFinal = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
                isBuffering = true
                activeExoPlayer.setSeekParameters(SeekParameters.EXACT)
                activeExoPlayer.seekTo(target)
            },
            onVolumeChanged = { percent ->
                if (percent != null) {
                    volumeHideJob?.cancel()
                    gestureVolumePercent = percent
                } else {
                    volumeHideJob?.cancel()
                    volumeHideJob = coroutineScope.launch {
                        delay(800)
                        gestureVolumePercent = null
                    }
                }
            },
            onBrightnessChanged = { percent ->
                if (percent != null) {
                    brightnessHideJob?.cancel()
                    gestureBrightnessPercent = percent
                } else {
                    brightnessHideJob?.cancel()
                    brightnessHideJob = coroutineScope.launch {
                        delay(800)
                        gestureBrightnessPercent = null
                    }
                }
            },
            onDoubleTap = { isForward ->
                if (isForward) {
                    val target = (activeExoPlayer.currentPosition + 10000).coerceAtMost(duration).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                } else {
                    val target = (activeExoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                }
                lastSeekTime = System.currentTimeMillis()
            },
            onSingleTap = {
                showControls = !showControls
            }
        )
    } else {
        val currentDuration by rememberUpdatedState(duration)
        val currentExoPlayer by rememberUpdatedState(activeExoPlayer)
        var vrSingleTapJob by remember { mutableStateOf<Job?>(null) }
        var lastVrTapTime by remember { mutableLongStateOf(0L) }
        var lastVrTapX by remember { mutableFloatStateOf(0f) }
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startPos = down.position
                val startTime = System.currentTimeMillis()
                var hasDragged = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) {
                        val dist = (change.position - startPos).getDistance()
                        if (dist > 16f) {
                            hasDragged = true
                        }
                    } else {
                        val elapsed = System.currentTimeMillis() - startTime
                        val dist = (change.position - startPos).getDistance()
                        if (!hasDragged && dist < 16f && elapsed < 350L) {
                            val now = System.currentTimeMillis()
                            val isDoubleTap = (now - lastVrTapTime < 320L) && (kotlin.math.abs(startPos.x - lastVrTapX) < 140f)
                            if (isDoubleTap && startPos.x < size.width * 0.25f) {
                                vrSingleTapJob?.cancel()
                                vrSingleTapJob = null
                                // Double-tap left side in Magic Window -> Seek -10s
                                val dur = currentDuration
                                val player = currentExoPlayer
                                if (dur > 0 && dur != C.TIME_UNSET) {
                                    val target = (player.currentPosition - 10000).coerceAtLeast(0L)
                                    isBuffering = true
                                    player.seekTo(target)
                                    currentPos = target
                                } else {
                                    player.seekBack()
                                }
                                lastSeekTime = now
                                lastVrTapTime = 0L
                            } else if (isDoubleTap && startPos.x > size.width * 0.75f) {
                                vrSingleTapJob?.cancel()
                                vrSingleTapJob = null
                                // Double-tap right side in Magic Window -> Seek +10s
                                val dur = currentDuration
                                val player = currentExoPlayer
                                if (dur > 0 && dur != C.TIME_UNSET) {
                                    val target = (player.currentPosition + 10000).coerceAtMost(dur).coerceAtLeast(0L)
                                    isBuffering = true
                                    player.seekTo(target)
                                    currentPos = target
                                } else {
                                    player.seekForward()
                                }
                                lastSeekTime = now
                                lastVrTapTime = 0L
                            } else {
                                lastVrTapTime = now
                                lastVrTapX = startPos.x
                                vrSingleTapJob?.cancel()
                                vrSingleTapJob = coroutineScope.launch {
                                    delay(280)
                                    showControls = !showControls
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(playerGesturesModifier)
            .testTag(if (isFullscreen) "video_player_overlay" else "inline_video_player")
    ) {
        if (isVrMode) {
            // High-Performance Monoscopic & Stereo 360° / 180° Spherical GL Surface (Gyroscope & Touch Dragging)
            key(vrRecenterTrigger, vrStereoMode) {
                AndroidView(
                    factory = { ctx ->
                        SphericalGLSurfaceView(ctx).apply {
                            setDefaultStereoMode(vrStereoMode)
                            setUseSensorRotation(vrUseGyro)
                            addVideoSurfaceListener(object : SphericalGLSurfaceView.VideoSurfaceListener {
                                override fun onVideoSurfaceCreated(surface: Surface) {
                                    activeExoPlayer.setVideoSurface(surface)
                                    if (activeExoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
                                        activeExoPlayer.play()
                                    }
                                }
                                override fun onVideoSurfaceDestroyed(surface: Surface) {
                                    activeExoPlayer.clearVideoSurface(surface)
                                }
                            })
                            activeExoPlayer.setVideoFrameMetadataListener(videoFrameMetadataListener)
                            activeExoPlayer.setCameraMotionListener(cameraMotionListener)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            sphericalViewRef = this
                            onResume()
                        }
                    },
                    update = { sphericalView ->
                        sphericalViewRef = sphericalView
                        sphericalView.setUseSensorRotation(vrUseGyro)
                    },
                    onRelease = { sphericalView ->
                        sphericalView.onPause()
                        activeExoPlayer.clearVideoSurface()
                        activeExoPlayer.clearVideoFrameMetadataListener(sphericalView.videoFrameMetadataListener)
                        activeExoPlayer.clearCameraMotionListener(sphericalView.cameraMotionListener)
                        sphericalViewRef = null
                    },
                    onReset = { sphericalView ->
                        sphericalView.onPause()
                        activeExoPlayer.clearVideoSurface()
                        activeExoPlayer.clearVideoFrameMetadataListener(sphericalView.videoFrameMetadataListener)
                        activeExoPlayer.clearCameraMotionListener(sphericalView.cameraMotionListener)
                        sphericalViewRef = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Standard 2D Surface with Safe Lifecycle Detachment
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = activeExoPlayer
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player != activeExoPlayer) {
                        playerView.player = activeExoPlayer
                    }
                    playerView.keepScreenOn = true
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                onReset = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffering Animation - Prominent centered Lottie loader
        if (isBuffering && errorMessage == null) {
            AppLottieLoadingAnimation(
                modifier = Modifier
                    .size(if (isFullscreen) 92.dp else 70.dp)
                    .align(Alignment.Center)
            )
        }

        // Error Dialog
        if (errorMessage != null && !isInPipMode) {
            PlayerErrorDialog(
                errorMessage = errorMessage ?: "Playback Error",
                errorDetails = errorDetails,
                onRetry = {
                    errorMessage = null
                    errorDetails = null
                    isBuffering = true
                    com.example.network.debrid.DebridManager.invalidateCache(primaryQuality?.url)
                    if (isVrMode) {
                        isVrMode = false // Graceful fallback to 2D view on decoder failure
                    }
                    activeExoPlayer.prepare()
                    activeExoPlayer.play()
                },
                onOpenExternal = {
                    openExternalVideoPlayer(context, primaryQuality?.url, title, activeHeaders)
                }
            )
        }

        // Side Indicators (Volume & Brightness)
        val overlaySidePadding = if (isFullscreen && isLandscape) 32.dp else 16.dp
        val volumePercent = gestureVolumePercent ?: 0
        VerticalSideBarIndicator(
            visible = gestureVolumePercent != null,
            percent = volumePercent,
            painter = painterResource(
                id = if (volumePercent == 0) R.drawable.ic_gesture_volume_mute else R.drawable.ic_gesture_volume_up
            ),
            barHeight = if (isFullscreen && isLandscape) 160.dp else 110.dp,
            sidePadding = overlaySidePadding,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        VerticalSideBarIndicator(
            visible = gestureBrightnessPercent != null,
            percent = gestureBrightnessPercent ?: 0,
            painter = painterResource(id = R.drawable.ic_gesture_brightness),
            barHeight = if (isFullscreen && isLandscape) 160.dp else 110.dp,
            sidePadding = overlaySidePadding,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Overlay Controls
        PlayerControlsOverlay(
            visible = showControls && errorMessage == null && !isInPipMode,
            isFullscreen = isFullscreen,
            isLandscape = isLandscape,
            title = title,
            isPlaying = isPlaying,
            currentPos = currentPos,
            bufferedPos = bufferedPos,
            duration = duration,
            isBuffering = isBuffering,
            is4kOrHdr = isRealHdrStream,
            isVrMode = isVrMode,
            vrUseGyro = vrUseGyro,
            vrStereoMode = vrStereoMode,
            onToggleVrMode = {
                val nextState = !isVrMode
                isVrMode = nextState
                if (nextState) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            },
            onToggleVrGyro = {
                vrUseGyro = !vrUseGyro
            },
            onRecenterVr = {
                vrRecenterTrigger++
            },
            onToggleVrStereoMode = {
                vrStereoMode = when (vrStereoMode) {
                    C.STEREO_MODE_LEFT_RIGHT -> C.STEREO_MODE_TOP_BOTTOM
                    C.STEREO_MODE_TOP_BOTTOM -> C.STEREO_MODE_MONO
                    else -> C.STEREO_MODE_LEFT_RIGHT
                }
            },
            onBack = handleBackAction,
            onRewind10s = {
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val target = (activeExoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                } else {
                    activeExoPlayer.seekBack()
                }
                lastSeekTime = System.currentTimeMillis()
            },
            onTogglePlayPause = {
                if (activeExoPlayer.isPlaying) {
                    activeExoPlayer.pause()
                    isPlaying = false
                    isBuffering = false
                } else {
                    if (activeExoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        activeExoPlayer.seekTo(0L)
                        currentPos = 0L
                    }
                    activeExoPlayer.play()
                    isPlaying = true
                    if (activeExoPlayer.playbackState != androidx.media3.common.Player.STATE_READY) {
                        isBuffering = true
                    }
                }
            },
            onForward10s = {
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val target = (activeExoPlayer.currentPosition + 10000).coerceAtMost(duration).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                } else {
                    activeExoPlayer.seekForward()
                }
                lastSeekTime = System.currentTimeMillis()
            },
            onEnterPip = {
                triggerPipMode(
                    context = context,
                    activity = activity,
                    player = activeExoPlayer,
                    currentPos = currentPos,
                    onCustomPip = onEnterPip,
                    onBeforeEnter = { showControls = false }
                )
            },
            onOpenExternal = {
                openExternalVideoPlayer(context, primaryQuality?.url, title, activeHeaders)
            },
            onToggleFullscreen = {
                if (isFullscreen) {
                    toggleScreenOrientation(activity, isLandscape)
                } else {
                    if (onFullscreenWithMode != null) {
                        onFullscreenWithMode(currentPos, true)
                    } else {
                        onFullscreen?.invoke(currentPos)
                    }
                }
            },
            onScrubbingChanged = { scrubbing ->
                isScrubbing = scrubbing
                activeExoPlayer.setSeekParameters(if (scrubbing) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
            },
            onSeekLive = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
            },
            onSeekFinal = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
                isBuffering = true
                activeExoPlayer.setSeekParameters(SeekParameters.EXACT)
                activeExoPlayer.seekTo(target)
            }
        )
    }
}

/**
 * Fullscreen Video Player Overlay
 */
@Composable
fun GoPlayer(
    title: String,
    qualities: List<StreamQuality>,
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    onClose: () -> Unit
) {
    CoreMediaPlayer(
        mode = PlayerMode.FULLSCREEN,
        title = title,
        qualities = qualities,
        defaultHeaders = defaultHeaders,
        initialPositionMs = initialPositionMs,
        startInLandscape = startInLandscape,
        exoPlayer = exoPlayer,
        onClose = onClose
    )
}

@Deprecated(
    message = "ExoPlayerOverlay is deprecated, use GoPlayer directly",
    replaceWith = ReplaceWith("GoPlayer(title, qualities, defaultHeaders, initialPositionMs, startInLandscape, exoPlayer, onClose)")
)
@Composable
fun ExoPlayerOverlay(
    title: String,
    qualities: List<StreamQuality>,
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    onClose: () -> Unit
) {
    GoPlayer(
        title = title,
        qualities = qualities,
        defaultHeaders = defaultHeaders,
        initialPositionMs = initialPositionMs,
        startInLandscape = startInLandscape,
        exoPlayer = exoPlayer,
        onClose = onClose
    )
}

/**
 * Embedded 16:9 Inline Video Player for Card Covers
 */
@Composable
fun InlineCardPlayer(
    title: String,
    qualities: List<StreamQuality>,
    defaultHeaders: Map<String, String> = emptyMap(),
    exoPlayer: ExoPlayer? = null,
    enableGestures: Boolean = true,
    onClose: () -> Unit,
    onFullscreen: (currentPositionMs: Long) -> Unit,
    onFullscreenWithMode: ((currentPositionMs: Long, startInLandscape: Boolean) -> Unit)? = null,
    onEnterPip: ((currentPositionMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CoreMediaPlayer(
        mode = PlayerMode.INLINE_CARD,
        title = title,
        qualities = qualities,
        defaultHeaders = defaultHeaders,
        exoPlayer = exoPlayer,
        enableGestures = enableGestures,
        onClose = onClose,
        onFullscreen = onFullscreen,
        onFullscreenWithMode = onFullscreenWithMode,
        onEnterPip = onEnterPip,
        modifier = modifier
    )
}
