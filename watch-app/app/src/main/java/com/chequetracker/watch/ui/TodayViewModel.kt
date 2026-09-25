package com.chequetracker.watch.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chequetracker.watch.data.FetchResult
import com.chequetracker.watch.data.Repository
import com.chequetracker.watch.data.TodayData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    /** Last cached data (may be from an earlier day; screens check isForToday). */
    val data: TodayData? = null,
    /** True until the cache has been read once. */
    val cacheLoaded: Boolean = false,
    val refreshing: Boolean = false,
    /** Last refresh couldn't reach the server. */
    val offline: Boolean = false,
    /** Last refresh reached the server but failed. */
    val error: String? = null,
)

private const val OPEN_REFRESH_GAP_MS = 2 * 60 * 1000L

private data class FetchStatus(val refreshing: Boolean = false, val offline: Boolean = false, val error: String? = null)

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val status = MutableStateFlow(FetchStatus())

    val state: StateFlow<UiState> =
        combine(Repository.cachedFlow(app), status) { data, s ->
            UiState(data, cacheLoaded = true, refreshing = s.refreshing, offline = s.offline, error = s.error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private var lastAttemptAt = 0L

    /**
     * On open: fetch unless we already tried in the last 2 minutes (the screen
     * turning off and on over the open app shouldn't cost a network call).
     */
    fun refreshOnOpen() {
        if (SystemClock.elapsedRealtime() - lastAttemptAt < OPEN_REFRESH_GAP_MS && lastAttemptAt != 0L) return
        refresh()
    }

    /** Refresh button: always fetches (unless one is already running). */
    fun refresh() {
        if (status.value.refreshing) return
        lastAttemptAt = SystemClock.elapsedRealtime()
        status.value = status.value.copy(refreshing = true)
        viewModelScope.launch {
            status.value = when (val r = Repository.refresh(getApplication())) {
                is FetchResult.Success -> FetchStatus()
                FetchResult.Offline -> FetchStatus(offline = true)
                is FetchResult.Error -> FetchStatus(error = r.message)
            }
        }
    }
}
