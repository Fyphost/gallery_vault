package com.vaultgallery.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.media.DeviceMedia
import com.vaultgallery.app.media.MediaStoreSource
import com.vaultgallery.app.work.ImportScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportUiState(
    val loading: Boolean = false,
    val device: List<DeviceMedia> = emptyList()
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val mediaStoreSource: MediaStoreSource,
    private val importScheduler: ImportScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /**
     * Loads images + videos from MediaStore so the user can pick which to vault.
     * Using our own MediaStore query (rather than the system photo picker) gives us
     * the real content URIs needed to delete the originals after import.
     */
    fun loadDeviceMedia() {
        if (_state.value.device.isNotEmpty() || _state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                (mediaStoreSource.queryImages() + mediaStoreSource.queryVideos())
                    .sortedByDescending { it.dateAdded }
            }
            _state.value = ImportUiState(loading = false, device = items)
        }
    }

    /** Encrypt-imports the selected items in the background, optionally deleting them. */
    fun import(selected: List<DeviceMedia>, deleteOriginal: Boolean) {
        val items = selected.map { it.uri to it.type }
        importScheduler.enqueue(items, albumId = null, deleteOriginal = deleteOriginal)
    }
}
