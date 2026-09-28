package nl.streamfix.ui.screens.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PlaybackErrorPhase { Hidden, Recovering, Persistent }

/** Een klok per storingsreeks; herhaalde fouten verschuiven de deadlines niet. */
internal class PlaybackErrorTimer(private val scope: CoroutineScope) {
    private val mutablePhase = MutableStateFlow(PlaybackErrorPhase.Hidden)
    val phase = mutablePhase.asStateFlow()
    private var job: Job? = null

    fun onError() {
        if (job != null) return
        job = scope.launch {
            delay(6_000)
            mutablePhase.value = PlaybackErrorPhase.Recovering
            delay(14_000)
            mutablePhase.value = PlaybackErrorPhase.Persistent
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        mutablePhase.value = PlaybackErrorPhase.Hidden
    }

    /** Terug verbergt de melding voor deze reeks, zonder retries te stoppen. */
    fun dismiss() {
        job?.cancel()
        mutablePhase.value = PlaybackErrorPhase.Hidden
    }
}
