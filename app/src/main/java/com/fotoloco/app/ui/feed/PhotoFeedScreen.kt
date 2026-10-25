package com.fotoloco.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fotoloco.app.FeedUiState
import com.fotoloco.app.LoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFeedScreen(
    state: FeedUiState,
    listState: LazyListState,
    hasPermission: Boolean,
    permissionDenied: Boolean,
    partialAccess: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPhotoClick: (index: Int) -> Unit,
    onLocationClick: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Foto Loco", style = MaterialTheme.typography.titleLarge)
                        val subtitle = when {
                            state.locationFilter != null ->
                                "${state.photos.size} photos in ${state.locationFilter}"
                            state.totalCount > 0 -> "${state.totalCount} photos"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh photos")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedVisibility(visible = state.locationFilter != null) {
                FilterBanner(
                    label = state.locationFilter.orEmpty(),
                    onClear = onClearFilter,
                )
            }
            when {
                !hasPermission -> PermissionPrompt(
                    denied = permissionDenied,
                    onRequest = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                )
                state.photos.isEmpty() &&
                    (state.loadState == LoadState.Loading || state.loadState == LoadState.Idle) ->
                    CenteredBox { CircularProgressIndicator() }
                state.loadState == LoadState.Error -> MessageState(
                    title = "Couldn't load photos",
                    body = "Something went wrong reading your gallery.",
                    actionLabel = "Try again",
                    onAction = onRefresh,
                )
                state.photos.isEmpty() -> MessageState(
                    title = "No photos yet",
                    body = if (partialAccess) {
                        "You've only shared some photos with Foto Loco. Allow more to see them here."
                    } else {
                        "Photos you take will show up here."
                    },
                    actionLabel = if (partialAccess) "Choose photos" else null,
                    onAction = onRequestPermission,
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (partialAccess) {
                        item(key = "partial") {
                            OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                                Text("Showing selected photos · Choose more")
                            }
                        }
                    }
                    itemsIndexed(state.photos, key = { _, photo -> photo.id }) { index, photo ->
                        PhotoCardItem(
                            photo = photo,
                            isFilterActive = state.locationFilter != null &&
                                photo.locationLabel == state.locationFilter,
                            onPhotoClick = { onPhotoClick(index) },
                            onLocationClick = onLocationClick,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBanner(label: String, onClear: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        InputChip(
            selected = true,
            onClick = onClear,
            label = {
                Text(
                    "Filtered by: $label",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            },
            modifier = Modifier.semantics {
                contentDescription = "Filtered by $label. Tap to clear filter and show all photos."
            },
        )
    }
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MessageState(
        title = "See your photos, by place",
        body = "Foto Loco needs access to your photos to show them with the date and the place they were taken.",
        actionLabel = if (denied) "Try again" else "Allow access",
        onAction = onRequest,
        secondaryLabel = if (denied) "Open settings" else null,
        onSecondary = onOpenSettings,
    )
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    CenteredBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryLabel != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
