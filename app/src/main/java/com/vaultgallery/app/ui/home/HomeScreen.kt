package com.vaultgallery.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.R
import com.vaultgallery.app.data.prefs.SortOrder
import com.vaultgallery.app.domain.model.Album
import com.vaultgallery.app.domain.model.MediaItem
import com.vaultgallery.app.ui.components.MediaThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (MediaItem) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tab by viewModel.tab.collectAsState()
    val media by viewModel.media.collectAsState()
    val albums by viewModel.albums.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortItem(R.string.sort_date_desc) { viewModel.setSortOrder(SortOrder.DATE_DESC); sortMenu = false }
                            SortItem(R.string.sort_date_asc) { viewModel.setSortOrder(SortOrder.DATE_ASC); sortMenu = false }
                            SortItem(R.string.sort_name) { viewModel.setSortOrder(SortOrder.NAME); sortMenu = false }
                            SortItem(R.string.sort_size) { viewModel.setSortOrder(SortOrder.SIZE); sortMenu = false }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.PHOTOS,
                    onClick = { viewModel.selectTab(HomeTab.PHOTOS) },
                    icon = { Icon(Icons.Default.Image, null) },
                    label = { Text(stringResource(R.string.tab_photos)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.VIDEOS,
                    onClick = { viewModel.selectTab(HomeTab.VIDEOS) },
                    icon = { Icon(Icons.Default.Videocam, null) },
                    label = { Text(stringResource(R.string.tab_videos)) }
                )
                NavigationBarItem(
                    selected = tab == HomeTab.ALBUMS,
                    onClick = { viewModel.selectTab(HomeTab.ALBUMS) },
                    icon = { Icon(Icons.Default.FolderOpen, null) },
                    label = { Text(stringResource(R.string.tab_albums)) }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.import_media)) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                HomeTab.ALBUMS -> AlbumGrid(albums, onOpenAlbum)
                else -> MediaGrid(media, onOpenItem)
            }
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, onOpenItem: (MediaItem) -> Unit) {
    if (items.isEmpty()) {
        EmptyState()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            MediaThumbnail(
                item = item,
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f)
                    .clickable { onOpenItem(item) }
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onOpenAlbum(album.id) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text(album.name, style = MaterialTheme.typography.bodyLarge)
                Text("${album.itemCount}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.empty_vault),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun SortItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}
