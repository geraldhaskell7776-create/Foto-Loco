package com.fotoloco.app.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore

sealed interface DeleteOutcome {
    /** Finished. [deletedIds] were removed. */
    data class Done(val deletedIds: List<Long>) : DeleteOutcome

    /**
     * Android must ask the user first. Launch [intentSender]; if they agree,
     * call [PhotoDeleter.afterConsent] with [remaining].
     */
    data class NeedsConsent(
        val intentSender: IntentSender,
        val deletedIds: List<Long>,
        val remaining: List<PhotoItem>,
    ) : DeleteOutcome
}

/**
 * Deletes photos the way each Android version allows:
 * - Android 11+: one system dialog for all photos; they go to the system trash (restorable ~30 days).
 * - Android 10: delete directly; Android asks per photo the app didn't create.
 * - Android 8–9: delete directly (needs storage write permission).
 */
object PhotoDeleter {

    val usesSystemConfirmation: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun delete(context: Context, photos: List<PhotoItem>): DeleteOutcome {
        if (photos.isEmpty()) return DeleteOutcome.Done(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createTrashRequest(
                context.contentResolver,
                photos.map { it.uri },
                true,
            )
            return DeleteOutcome.NeedsConsent(request.intentSender, emptyList(), photos)
        }
        return deleteDirectly(context, photos)
    }

    /** Call after the user approved a [DeleteOutcome.NeedsConsent] request. */
    fun afterConsent(context: Context, remaining: List<PhotoItem>): DeleteOutcome =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The system already moved them all to trash.
            DeleteOutcome.Done(remaining.map { it.id })
        } else {
            // Android 10 granted access to the first photo; carry on with the rest.
            deleteDirectly(context, remaining)
        }

    private fun deleteDirectly(context: Context, photos: List<PhotoItem>): DeleteOutcome {
        val deleted = mutableListOf<Long>()
        photos.forEachIndexed { index, photo ->
            try {
                if (context.contentResolver.delete(photo.uri, null, null) > 0) {
                    deleted += photo.id
                }
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return DeleteOutcome.NeedsConsent(
                        e.userAction.actionIntent.intentSender,
                        deleted,
                        photos.drop(index),
                    )
                }
                // No way to get access to this one; skip it.
            }
        }
        return DeleteOutcome.Done(deleted)
    }
}
