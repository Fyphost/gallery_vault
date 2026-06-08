package com.vaultgallery.app.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a decrypted media file to an external player (e.g. XPlayer) via an
 * ACTION_VIEW intent with a temporary content URI. Read permission is granted only
 * to the target app and only for the lifetime of the task.
 */
@Singleton
class ExternalPlayerLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /**
     * @param uri content URI from [PlaybackPreparer.contentUriFor]
     * @param mimeType the item's mime type
     * @param packageName preferred external player package (empty = system chooser)
     * @return true if an external player was launched
     */
    fun play(uri: Uri, mimeType: String, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName.isNotBlank() && isInstalled(packageName)) {
                setPackage(packageName)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // Fallback to a chooser if the preferred player isn't available.
            runCatching {
                val chooser = Intent.createChooser(
                    intent.apply { setPackage(null) },
                    "Open with"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                true
            }.getOrDefault(false)
        }
    }
}
