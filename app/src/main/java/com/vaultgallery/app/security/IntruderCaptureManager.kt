package com.vaultgallery.app.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vaultgallery.app.data.repository.VaultRepository
import com.vaultgallery.app.security.crypto.CryptoEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silently captures a front-camera photo when an intruder fails to unlock the vault.
 * The image is encrypted with the vault master key and stored alongside an
 * [com.vaultgallery.app.data.local.entity.IntruderLogEntity] record.
 *
 * No preview surface is shown; we bind only [ImageCapture] to a lifecycle owner
 * and take a single shot.
 */
@Singleton
class IntruderCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoEngine: CryptoEngine,
    private val repository: VaultRepository
) {
    fun hasFrontCamera(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    /**
     * Captures and stores an intruder selfie. Must be called with a valid
     * [lifecycleOwner] (typically the auth Activity). Safe to call from a
     * coroutine on the main dispatcher.
     */
    suspend fun captureIntruder(
        lifecycleOwner: LifecycleOwner,
        enteredLength: Int,
        method: String
    ) {
        if (!hasFrontCamera()) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageCapture
            )
            val bytes = takePictureBytes(imageCapture)
            val outFile = File(intruderDir(), "intruder_${System.currentTimeMillis()}.enc")
            outFile.outputStream().use { out ->
                cryptoEngine.encryptFile(ByteArrayInputStream(bytes), out)
            }
            repository.logIntruder(outFile.absolutePath, enteredLength, method)
        } catch (_: Exception) {
            // Capture is best-effort; never crash the auth flow.
        } finally {
            cameraProvider.unbindAll()
        }
    }

    private suspend fun takePictureBytes(imageCapture: ImageCapture): ByteArray =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        image.close()
                        if (cont.isActive) cont.resumeWith(Result.success(bytes))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(exception))
                    }
                }
            )
        }

    private fun intruderDir(): File =
        File(context.filesDir, "intruders").apply { if (!exists()) mkdirs() }
}
