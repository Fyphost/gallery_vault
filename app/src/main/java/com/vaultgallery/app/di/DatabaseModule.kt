package com.vaultgallery.app.di

import android.content.Context
import androidx.room.Room
import com.vaultgallery.app.data.local.VaultDatabase
import com.vaultgallery.app.data.local.dao.AlbumDao
import com.vaultgallery.app.data.local.dao.IntruderDao
import com.vaultgallery.app.data.local.dao.MediaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, VaultDatabase.NAME)
            // Metadata DB lives in app-private storage. For at-rest DB encryption,
            // wire SQLCipher's SupportFactory here (see README).
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMediaDao(db: VaultDatabase): MediaDao = db.mediaDao()
    @Provides fun provideAlbumDao(db: VaultDatabase): AlbumDao = db.albumDao()
    @Provides fun provideIntruderDao(db: VaultDatabase): IntruderDao = db.intruderDao()
}
