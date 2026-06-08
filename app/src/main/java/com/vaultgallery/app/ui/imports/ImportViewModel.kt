package com.vaultgallery.app.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.vaultgallery.app.data.local.entity.MediaType
import com.vaultgallery.app.work.ImportScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importScheduler: ImportScheduler
) : ViewModel() {

    /** Classifies a picked URI as image or video by its mime type. */
    fun typeOf(uri: Uri): MediaType {
        val mime = context.contentResolver.getType(uri) ?: ""
        return if (mime.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE
    }

    /** Encrypt-imports the selected items in the background. */
    fun import(uris: List<Uri>, deleteOriginal: Boolean) {
        val items = uris.map { it to typeOf(it) }
        importScheduler.enqueue(items, albumId = null, deleteOriginal = deleteOriginal)
    }
}
