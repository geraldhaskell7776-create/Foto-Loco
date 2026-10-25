package com.fotoloco.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fotoloco.app.data.PhotoItem
import com.fotoloco.app.data.PhotoLocation

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Edge-to-edge black viewer. Swipe to page, pinch or double-tap to zoom,
 * tap or press back to return. [onDismiss] gets the page being shown.
 */
@Composable
fun FullScreenViewer(
    photos: List<PhotoItem>,
    initialPage: Int,
    onDismiss: (page: Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }
    var currentPageZoomed by remember { mutableStateOf(false) }
    val dismiss = { onDismiss(pagerState.currentPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) }

    BackHandler(onBack = dismiss)
    ImmersiveSystemBars()
    LaunchedEffect(pagerState.currentPage) { currentPageZoomed = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { photos[it].id },
            beyondViewportPageCount = 1,
            // While zoomed, one-finger drags pan the photo instead of changing pages.
            userScrollEnabled = !currentPageZoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                photo = photos[page],
                isCurrentPage = page == pagerState.currentPage,
                onZoomChanged = { zoomed ->
                    if (page == pagerState.currentPage) currentPageZoomed = zoomed
                },
                onTap = dismiss,
            )
        }

        val photo = photos.getOrNull(pagerState.currentPage)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = dismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close viewer", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        if (photo != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(photo.dateLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
                val place = when (val loc = photo.location) {
                    is PhotoLocation.Known -> loc.label
                    PhotoLocation.Unavailable -> "Location unavailable"
                    PhotoLocation.Pending -> null
                }
                if (place != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(place, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    photo: PhotoItem,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    // Pages swiped away reset to fit.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    fun clampOffset(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1f) / 2f
        val maxY = size.height * (forScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // Zoom in toward the tapped point.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clampOffset((center - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                            scale = DOUBLE_TAP_SCALE
                        }
                        currentOnZoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // At 1x, single-finger drags are left for the pager to page with.
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            if (centroid != Offset.Unspecified) {
                                // Keep the point under the fingers fixed while zooming.
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val k = newScale / scale
                                offset = clampOffset((centroid - center) * (1f - k) + offset * k + pan, newScale)
                            }
                            scale = newScale
                            if (scale <= 1f) offset = Offset.Zero
                            currentOnZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = "Photo taken ${photo.dateLabel}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/** Hides status and navigation bars while the viewer is open. Swipe from an edge to peek. */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val wasLightStatusBars = controller.isAppearanceLightStatusBars
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = wasLightStatusBars
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
