package com.fotoloco.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object MediaPermissions {

    /** Everything to ask for in one dialog flow, per Android version. */
    fun toRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True when the app can see at least some photos (full or "selected photos" access). */
    fun canReadPhotos(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            context.isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            context.isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True when only some photos were shared (Android 14+ "Select photos"). */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
            context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
