package nl.streamfix.ui.screens.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackErrorTimerTest {
    @Test
    fun meldingVerschijntExactNaZesEnTwintigSeconden() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Recovering, timer.phase.value)
        advanceTimeBy(13_999)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Recovering, timer.phase.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Persistent, timer.phase.value)
    }

    @Test
    fun herstelVoorZesSecondenAnnuleertBeideMeldingen() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(4_000)
        timer.reset()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
    }

    @Test
    fun resetNaEersteMeldingAnnuleertVervolgtip() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        timer.reset()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
    }

    @Test
    fun nieuweStoringKrijgtVolledigeGratieperiode() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(4_000)
        timer.reset()
        timer.onError()
        runCurrent()
        advanceTimeBy(5_999)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Recovering, timer.phase.value)
    }

    @Test
    fun herhaaldeFoutenVerschuivenDeadlineNiet() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(3_000)
        timer.onError()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Recovering, timer.phase.value)
        advanceTimeBy(14_000)
        runCurrent()
        timer.onError()
        assertEquals(PlaybackErrorPhase.Persistent, timer.phase.value)
    }

    @Test
    fun terugOnderdruktOokLatereMeldingenTotReset() = runTest {
        val timer = PlaybackErrorTimer(backgroundScope)
        timer.onError()
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        timer.dismiss()
        timer.onError()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Hidden, timer.phase.value)
        timer.reset()
        timer.onError()
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(PlaybackErrorPhase.Recovering, timer.phase.value)
    }
}
