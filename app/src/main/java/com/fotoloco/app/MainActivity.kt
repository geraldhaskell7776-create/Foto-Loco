package com.fotoloco.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.fotoloco.app.ui.places.PlacesScreen
import com.fotoloco.app.ui.rememberPhotoDeleter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fotoloco.app.data.MediaPermissions
import com.fotoloco.app.ui.feed.PhotoFeedScreen
import com.fotoloco.app.ui.theme.FotoLocoTheme
import com.fotoloco.app.ui.viewer.FullScreenViewer
import kotlinx.coroutines.launch

private const val PAGE_PHOTOS = 0
private const val PAGE_PLACES = 1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FotoLocoTheme {
                FotoLocoApp()
            }
        }
    }
}

@Composable
private fun FotoLocoApp(viewModel: PhotoViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(MediaPermissions.canReadPhotos(context)) }
    var partialAccess by remember { mutableStateOf(MediaPermissions.hasPartialAccess(context)) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    val deletePhotos = rememberPhotoDeleter { deletedIds ->
        selectedIds = selectedIds - deletedIds.toSet()
        viewModel.onPhotosDeleted(deletedIds)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = MediaPermissions.canReadPhotos(context)
        partialAccess = MediaPermissions.hasPartialAccess(context)
        permissionDenied = !hasPermission
        if (hasPermission) viewModel.loadPhotos()
    }

    // Re-check on every return to the app: the user may have changed access in Settings
    // or taken new photos.
    LifecycleResumeEffect(Unit) {
        hasPermission = MediaPermissions.canReadPhotos(context)
        partialAccess = MediaPermissions.hasPartialAccess(context)
        if (hasPermission) viewModel.loadPhotos()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState { 2 }
    val goToPage: (Int) -> Unit = { page -> scope.launch { pagerState.animateScrollToPage(page) } }

    // Back on the Places screen returns to Photos instead of leaving the app.
    BackHandler(enabled = pagerState.currentPage != PAGE_PHOTOS && viewerIndex == null) {
        goToPage(PAGE_PHOTOS)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = pagerState.currentPage == PAGE_PHOTOS,
                        onClick = { goToPage(PAGE_PHOTOS) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Photos") },
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == PAGE_PLACES,
                        onClick = { goToPage(PAGE_PLACES) },
                        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        label = { Text("Places") },
                    )
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
            ) { page ->
                when (page) {
                    PAGE_PHOTOS -> PhotoFeedScreen(
                        state = state,
                        listState = listState,
                        hasPermission = hasPermission,
                        permissionDenied = permissionDenied,
                        partialAccess = partialAccess,
                        onRequestPermission = { permissionLauncher.launch(MediaPermissions.toRequest()) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                        onRefresh = viewModel::loadPhotos,
                        onPhotoClick = { viewerIndex = it },
                        onLocationClick = { label ->
                            viewModel.setLocationFilter(label)
                            scope.launch { listState.scrollToItem(0) }
                        },
                        onClearFilter = {
                            viewModel.clearLocationFilter()
                            scope.launch { listState.scrollToItem(0) }
                        },
                        selectedIds = selectedIds,
                        onToggleSelected = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onClearSelection = { selectedIds = emptySet() },
                        onDeleteSelected = {
                            deletePhotos(state.photos.filter { it.id in selectedIds })
                        },
                    )
                    else -> PlacesScreen(
                        places = state.places,
                        pendingCount = state.pendingLocationCount,
                        hasPhotos = state.totalCount > 0,
                        onPlaceClick = { label ->
                            viewModel.setLocationFilter(label)
                            scope.launch {
                                listState.scrollToItem(0)
                                pagerState.animateScrollToPage(PAGE_PHOTOS)
                            }
                        },
                    )
                }
            }
        }

        val startIndex = viewerIndex
        if (startIndex != null && state.photos.isNotEmpty()) {
            FullScreenViewer(
                photos = state.photos,
                initialPage = startIndex.coerceIn(0, state.photos.lastIndex),
                onDismiss = { page ->
                    viewerIndex = null
                    val offset = if (partialAccess) 1 else 0 // "Choose more" row sits above the photos
                    scope.launch { listState.scrollToItem(page + offset) }
                },
                onDelete = { photo -> deletePhotos(listOf(photo)) },
            )
        }
    }
}
