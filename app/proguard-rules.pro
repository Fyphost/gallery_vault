# Keep Room entities/DAO metadata
-keep class com.vaultgallery.app.data.local.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep crypto provider classes
-keep class androidx.security.crypto.** { *; }
