package com.vaultgallery.app.ui.navigation

/** Centralized navigation routes. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LOCK = "lock"
    const val HOME = "home"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val IMAGE_VIEWER = "viewer/{itemId}"
    const val PLAYER = "player/{itemId}"
    const val SETTINGS = "settings"
    const val IMPORT = "import"

    fun albumDetail(albumId: Long) = "album/$albumId"
    fun imageViewer(itemId: Long) = "viewer/$itemId"
    fun player(itemId: Long) = "player/$itemId"
}
