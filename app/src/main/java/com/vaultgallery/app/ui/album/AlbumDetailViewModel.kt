package com.vaultgallery.app.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.prefs.SortOrder
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle["albumId"])

    val items: StateFlow<List<MediaItem>> =
        repository.observeAlbumItems(albumId, SortOrder.DATE_DESC)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
