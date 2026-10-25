package com.fotoloco.app.data

import android.net.Uri

/** One photo in the feed. Location fills in after the EXIF/geocoding pass. */
data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val takenAtMillis: Long,
    val dateLabel: String,
    /** False when MediaStore had no DATE_TAKEN, so EXIF may give a better time. */
    val hasCaptureDate: Boolean,
    val location: PhotoLocation = PhotoLocation.Pending,
) {
    /** Label used for "same place" filtering, or null if there is no location. */
    val locationLabel: String?
        get() = (location as? PhotoLocation.Known)?.label

    fun withDetails(details: PhotoDetails): PhotoItem {
        val exifTime = details.exifTakenAtMillis
        return if (!hasCaptureDate && exifTime != null) {
            copy(
                takenAtMillis = exifTime,
                dateLabel = Formatters.formatDate(exifTime),
                location = details.location,
            )
        } else {
            copy(location = details.location)
        }
    }
}

sealed interface PhotoLocation {
    /** Metadata not read yet. */
    data object Pending : PhotoLocation

    /** Photo has no GPS data (or it can't be read). */
    data object Unavailable : PhotoLocation

    /**
     * Photo has GPS data. [label] is "City, State" when geocoding worked,
     * otherwise formatted coordinates.
     */
    data class Known(
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val isPlaceName: Boolean,
    ) : PhotoLocation
}

/** Extra metadata read from the image file itself. */
data class PhotoDetails(
    val exifTakenAtMillis: Long?,
    val location: PhotoLocation,
)
