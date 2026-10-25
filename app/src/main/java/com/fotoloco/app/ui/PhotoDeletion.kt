package com.fotoloco.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fotoloco.app.data.DeleteOutcome
import com.fotoloco.app.data.PhotoDeleter
import com.fotoloco.app.data.PhotoItem

/**
 * Returns a function that deletes photos, showing whatever confirmation the
 * Android version needs. [onDeleted] gets the ids that were actually removed.
 */
@Composable
fun rememberPhotoDeleter(onDeleted: (List<Long>) -> Unit): (List<PhotoItem>) -> Unit {
    val context = LocalContext.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    var awaitingConsent by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var confirming by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }

    lateinit var handle: (DeleteOutcome) -> Unit
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val remaining = awaitingConsent
        awaitingConsent = emptyList()
        if (result.resultCode == Activity.RESULT_OK && remaining.isNotEmpty()) {
            handle(PhotoDeleter.afterConsent(context, remaining))
        }
    }
    handle = { outcome ->
        when (outcome) {
            is DeleteOutcome.Done -> currentOnDeleted(outcome.deletedIds)
            is DeleteOutcome.NeedsConsent -> {
                if (outcome.deletedIds.isNotEmpty()) currentOnDeleted(outcome.deletedIds)
                awaitingConsent = outcome.remaining
                consentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
            }
        }
    }

    // Older Android has no system dialog, so ask ourselves first.
    if (confirming.isNotEmpty()) {
        val count = confirming.size
        AlertDialog(
            onDismissRequest = { confirming = emptyList() },
            title = { Text(if (count == 1) "Delete this photo?" else "Delete $count photos?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val photos = confirming
                    confirming = emptyList()
                    handle(PhotoDeleter.delete(context, photos))
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = emptyList() }) { Text("Cancel") }
            },
        )
    }

    return { photos ->
        if (photos.isNotEmpty()) {
            if (PhotoDeleter.usesSystemConfirmation) {
                handle(PhotoDeleter.delete(context, photos))
            } else {
                confirming = photos
            }
        }
    }
}
