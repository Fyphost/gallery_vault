package com.vaultgallery.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultgallery.app.data.local.entity.MediaType
import com.vaultgallery.app.data.prefs.SortOrder
import com.vaultgallery.app.data.prefs.ViewMode
import com.vaultgallery.app.data.prefs.VaultPreferences
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.domain.model.Album
import com.vaultgallery.app.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level vault tabs. */
enum class HomeTab { PHOTOS, VIDEOS, ALBUMS }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val preferences: VaultPreferences
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.PHOTOS)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Media list reacts to tab (type filter), search query and sort order. */
    val media: StateFlow<List<MediaItem>> =
        combine(_tab, _query, preferences.settings) { tab, query, settings ->
            Triple(tab, query, settings.sortOrder)
        }.flatMapLatest { (tab, query, sort) ->
            val type = when (tab) {
                HomeTab.PHOTOS -> MediaType.IMAGE
                HomeTab.VIDEOS -> MediaType.VIDEO
                HomeTab.ALBUMS -> null
            }
            repository.observeMedia(type, query, sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> =
        repository.observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: HomeTab) { _tab.value = tab }
    fun onQueryChange(value: String) { _query.value = value }

    fun toggleViewMode() {
        viewModelScope.launch {
            val current = preferences.settings
            val mode = settings.value?.viewMode ?: ViewMode.GRID
            preferences.setViewMode(if (mode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferences.setSortOrder(order) }
    }

    fun deleteItems(ids: List<Long>) {
        viewModelScope.launch { repository.deleteItems(ids) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch { repository.setFavorite(item.id, !item.isFavorite) }
    }
}
