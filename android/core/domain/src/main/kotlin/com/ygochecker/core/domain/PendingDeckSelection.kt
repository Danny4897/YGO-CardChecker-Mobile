package com.ygochecker.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-tab handoff: a deck built elsewhere (e.g. "Build deck from this card" in Search)
 * sets the id here so the Decks tab can pre-select it, without a shared nav graph. Both tabs
 * are Activity-scoped Composables sharing one ViewModelStore, so the Decks tab's ViewModel
 * may already exist — it collects [pending] continuously rather than reading it only once.
 */
@Singleton
class PendingDeckSelection @Inject constructor() {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun set(deckId: Long) {
        _pending.value = deckId
    }

    /** Clears after the Decks tab has applied the selection. */
    fun consume() {
        _pending.value = null
    }
}
