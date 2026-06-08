package com.vaultgallery.app.di

import android.content.Context
import coil.ImageLoader
import com.vaultgallery.app.security.crypto.CryptoEngine
import com.vaultgallery.app.ui.image.EncryptedImage
import com.vaultgallery.app.ui.image.EncryptedImageFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    /**
     * An [ImageLoader] that knows how to decrypt [EncryptedImage] blobs. Provided via
     * Hilt so screens can inject it (or obtain through a CompositionLocal).
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        cryptoEngine: CryptoEngine
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(EncryptedImageFetcher.Factory(cryptoEngine, context))
        }
        .crossfade(true)
        .build()
}
