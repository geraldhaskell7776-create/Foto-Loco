package com.fotoloco.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fotoloco.app.data.PhotoDetails
import com.fotoloco.app.data.PhotoItem
import com.fotoloco.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class LoadState { Idle, Loading, Loaded, Error }

data class FeedUiState(
    val loadState: LoadState = LoadState.Idle,
    /** Photos to show (already filtered). */
    val photos: List<PhotoItem> = emptyList(),
    val totalCount: Int = 0,
    val locationFilter: String? = null,
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val allPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())
    private val loadState = MutableStateFlow(LoadState.Idle)
    private val locationFilter = MutableStateFlow<String?>(null)

    /** EXIF + geocode results survive reloads so returning to the app is instant. */
    private val detailsCache = ConcurrentHashMap<Long, PhotoDetails>()
    private var loadJob: Job? = null

    val uiState: StateFlow<FeedUiState> =
        combine(allPhotos, loadState, locationFilter) { photos, load, filter ->
            FeedUiState(
                loadState = load,
                photos = if (filter == null) photos else photos.filter { it.locationLabel == filter },
                totalCount = photos.size,
                locationFilter = filter,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    /** Loads (or refreshes) the photo list, then fills in dates/locations in the background. */
    fun loadPhotos() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadState.value = LoadState.Loading
            val photos = try {
                repository.loadPhotos()
            } catch (e: SecurityException) {
                loadState.value = LoadState.Error
                return@launch
            }
            allPhotos.value = photos.map { photo ->
                detailsCache[photo.id]?.let(photo::withDetails) ?: photo
            }
            loadState.value = LoadState.Loaded
            resolveDetails(photos.filter { !detailsCache.containsKey(it.id) })
        }
    }

    fun setLocationFilter(label: String) {
        locationFilter.value = label
    }

    fun clearLocationFilter() {
        locationFilter.value = null
    }

    private suspend fun resolveDetails(photos: List<PhotoItem>) {
        // Newest first, a small batch at a time, so the top of the feed fills in quickly.
        photos.chunked(BATCH_SIZE).forEach { batch ->
            val results = coroutineScope {
                batch.map { photo ->
                    async(Dispatchers.IO) { photo.id to repository.readDetails(photo.uri) }
                }.awaitAll()
            }
            results.forEach { (id, details) -> detailsCache[id] = details }
            val updated = results.toMap()
            allPhotos.update { list ->
                list.map { photo -> updated[photo.id]?.let(photo::withDetails) ?: photo }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 12
    }
}
