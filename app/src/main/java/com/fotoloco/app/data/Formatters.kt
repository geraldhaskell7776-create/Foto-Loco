package com.fotoloco.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy '•' h:mm a", Locale.getDefault())

    /** e.g. "Oct 24, 2026 • 2:15 PM" */
    fun formatDate(millis: Long): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** e.g. "40.3040° N, 112.0070° W" */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.4f° %s, %.4f° %s",
            abs(latitude), ns, abs(longitude), ew,
        )
    }
}
