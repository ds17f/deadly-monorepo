package com.grateful.deadly.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot request to open the Favorites screen on its "Show Queue" tab.
 *
 * The player/playlist "View Show Queue" menu links navigate to the Favorites
 * bottom-nav destination and set this flag; FavoritesScreen consumes it on the
 * next composition to select the tab. Decoupled like [ToastController] so the
 * player/playlist features don't need a route into Favorites' internal tab state.
 */
@Singleton
class ShowQueueTabRequest @Inject constructor() {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() { _pending.value = true }
    fun consume() { _pending.value = false }
}
