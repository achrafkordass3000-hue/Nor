package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage

/**
 * ZoomableImage: Smooth pinch-to-zoom, pan, and double-tap zoom for full-quality images.
 * Respects HorizontalPager: single-finger horizontal swipes at 1x are not consumed,
 * allowing the pager to scroll seamlessly, while two-finger pinches zoom from 1x instantly.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom automatically when resetKey (e.g. page index) changes
    LaunchedEffect(resetKey) {
        scale = 1f
        offset = Offset.Zero
    }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 200),
        label = "zoom_scale"
    )

    val isZoomed = scale > 1.05f
    LaunchedEffect(isZoomed) {
        onZoomChanged?.invoke(isZoomed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(resetKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pressedCount = event.changes.count { it.pressed }
                            val isCurrentlyZoomed = scale > 1.02f

                            if (pressedCount >= 2) {
                                // Multi-finger pinch gesture: zoom & pan with 2+ fingers
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale

                                if (newScale > 1.02f) {
                                    val maxOffsetX = if (containerSize.width > 0) {
                                        (containerSize.width * (newScale - 1f)) / 2f
                                    } else {
                                        (newScale - 1f) * 600f
                                    }
                                    val maxOffsetY = if (containerSize.height > 0) {
                                        (containerSize.height * (newScale - 1f)) / 2f
                                    } else {
                                        (newScale - 1f) * 900f
                                    }
                                    offset = Offset(
                                        x = (offset.x + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    scale = 1f
                                    offset = Offset.Zero
                                }

                                // Consume touch events while pinching so Pager does not swipe
                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            } else if (isCurrentlyZoomed && pressedCount == 1) {
                                // Single-finger pan when zoomed in
                                val panChange = event.calculatePan()
                                val maxOffsetX = if (containerSize.width > 0) {
                                    (containerSize.width * (scale - 1f)) / 2f
                                } else {
                                    (scale - 1f) * 600f
                                }
                                val maxOffsetY = if (containerSize.height > 0) {
                                    (containerSize.height * (scale - 1f)) / 2f
                                } else {
                                    (scale - 1f) * 900f
                                }
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )

                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }
                            // When pressedCount == 1 and !isCurrentlyZoomed:
                            // We do NOT consume! HorizontalPager receives the swipe event freely.
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
