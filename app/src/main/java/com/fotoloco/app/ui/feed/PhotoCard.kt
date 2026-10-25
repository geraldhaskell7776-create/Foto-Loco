package com.fotoloco.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fotoloco.app.data.PhotoItem
import com.fotoloco.app.data.PhotoLocation

@Composable
fun PhotoCardItem(
    photo: PhotoItem,
    isFilterActive: Boolean,
    onPhotoClick: () -> Unit,
    onLocationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = "Photo taken ${photo.dateLabel}",
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(onClickLabel = "View full screen", onClick = onPhotoClick),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = photo.dateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            LocationLabel(
                location = photo.location,
                isFilterActive = isFilterActive,
                onClick = onLocationClick,
            )
        }
    }
}

@Composable
private fun LocationLabel(
    location: PhotoLocation,
    isFilterActive: Boolean,
    onClick: (String) -> Unit,
) {
    when (location) {
        PhotoLocation.Pending -> MutedLocationText("Finding location…")
        PhotoLocation.Unavailable -> MutedLocationText("Location unavailable")
        is PhotoLocation.Known -> {
            val colors = MaterialTheme.colorScheme
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isFilterActive) colors.primary else colors.primaryContainer)
                    .clickable(
                        enabled = !isFilterActive,
                        role = Role.Button,
                        onClickLabel = "Show photos from ${location.label}",
                    ) { onClick(location.label) }
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                val contentColor = if (isFilterActive) colors.onPrimary else colors.onPrimaryContainer
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = location.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MutedLocationText(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
