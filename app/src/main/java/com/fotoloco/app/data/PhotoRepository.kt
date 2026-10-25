package com.fotoloco.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDateTime
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class PhotoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Geocoding results keyed by rounded coordinates. Empty string = lookup failed. */
    private val placeCache = ConcurrentHashMap<String, String>()

    /** Reads every image in MediaStore, newest first. Fast: no file I/O. */
    suspend fun loadPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val photos = mutableListOf<PhotoItem>()
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = if (cursor.isNull(takenCol)) 0L else cursor.getLong(takenCol)
                val addedMillis = cursor.getLong(addedCol) * 1000L
                val hasTaken = taken > 0L
                val millis = if (hasTaken) taken else addedMillis
                photos += PhotoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    takenAtMillis = millis,
                    dateLabel = Formatters.formatDate(millis),
                    hasCaptureDate = hasTaken,
                )
            }
        }
        photos.sortedByDescending { it.takenAtMillis }
    }

    /** Reads EXIF (capture time + GPS) and reverse-geocodes the location. Never throws I/O errors. */
    suspend fun readDetails(uri: Uri): PhotoDetails = withContext(Dispatchers.IO) {
        val exif = openExif(uri) ?: return@withContext PhotoDetails(null, PhotoLocation.Unavailable)
        val exifTime = readExifTimestamp(exif)
        val latLong = exif.latLong?.takeIf { isValidCoordinate(it[0], it[1]) }
        val location = if (latLong == null) {
            PhotoLocation.Unavailable
        } else {
            resolveLocation(latLong[0], latLong[1])
        }
        PhotoDetails(exifTime, location)
    }

    private fun openExif(uri: Uri): ExifInterface? {
        val candidates = buildList {
            // Without setRequireOriginal, Android 10+ strips GPS tags from the stream.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMediaLocationPermission()) {
                add(MediaStore.setRequireOriginal(uri))
            }
            add(uri)
        }
        for (candidate in candidates) {
            try {
                resolver.openInputStream(candidate)?.use { stream ->
                    return ExifInterface(stream)
                }
            } catch (e: IOException) {
                // Try the next candidate.
            } catch (e: SecurityException) {
                // Try the next candidate.
            } catch (e: UnsupportedOperationException) {
                // setRequireOriginal refused; try the plain URI.
            }
        }
        return null
    }

    private fun hasMediaLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun readExifTimestamp(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        return try {
            val local = LocalDateTime.parse(raw.trim(), EXIF_DATE_FORMAT)
            if (offset != null) {
                local.atOffset(ZoneOffset.of(offset.trim())).toInstant().toEpochMilli()
            } else {
                local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (e: DateTimeException) {
            null
        }
    }

    private suspend fun resolveLocation(latitude: Double, longitude: Double): PhotoLocation.Known {
        val key = String.format(Locale.US, "%.3f,%.3f", latitude, longitude)
        val place = placeCache[key] ?: (geocode(latitude, longitude) ?: "").also {
            placeCache[key] = it
        }
        return if (place.isNotEmpty()) {
            PhotoLocation.Known(place, latitude, longitude, isPlaceName = true)
        } else {
            PhotoLocation.Known(
                Formatters.formatCoordinates(latitude, longitude),
                latitude,
                longitude,
                isPlaceName = false,
            )
        }
    }

    private suspend fun geocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return address?.let(::formatAddress)
    }

    /** "City, State" for state-based countries, otherwise "City, Country". */
    private fun formatAddress(address: Address): String? {
        val city = address.locality ?: address.subAdminArea ?: address.subLocality
        val countryCode = address.countryCode?.uppercase(Locale.US)
        val region = when {
            countryCode == "US" -> address.adminArea?.let { US_STATE_CODES[it] ?: it }
            countryCode in STATE_BASED_COUNTRIES -> address.adminArea
            else -> address.countryName
        } ?: address.countryName
        return listOfNotNull(city, region)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)

    private companion object {
        const val GEOCODE_TIMEOUT_MS = 10_000L
        val EXIF_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        val STATE_BASED_COUNTRIES = setOf("CA", "AU", "MX", "BR", "IN")

        val US_STATE_CODES = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
            "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
            "District of Columbia" to "DC", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI",
            "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA",
            "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME",
            "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN",
            "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE",
            "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM",
            "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH",
            "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI",
            "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX",
            "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA",
            "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
            "Puerto Rico" to "PR",
        )
    }
}
