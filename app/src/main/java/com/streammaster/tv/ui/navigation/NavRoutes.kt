package com.streammaster.tv.ui.navigation

/**
 * CONTRACT §2 — the single source of truth for navigation routes.
 * kind values: "live" | "film" | "series" | "recording" | "download".
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val LOADING = "loading"
    const val HOME = "home"
    const val LIVE = "live"
    const val FILMS = "films"
    const val SERIES = "series"
    const val EPG = "epg"
    const val MULTIVIEW = "multiview"
    const val RECORDINGS = "recordings"
    const val DOWNLOADS = "downloads"
    const val MATCH_CENTER = "match_center"
    const val REMOTE = "remote"
    const val FAVORIS = "favoris"
    const val WATCH_LATER = "watch_later"
    const val HISTORIQUE = "historique"
    const val PLAYLISTS = "playlists"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
    const val PARENTAL = "parental"
    const val SEARCH = "search"
    const val PLAYER = "player/{kind}/{id}"
    const val DETAIL = "detail/{kind}/{id}"

    fun player(kind: String, id: String) = "player/$kind/$id"
    fun detail(kind: String, id: String) = "detail/$kind/$id"
}
