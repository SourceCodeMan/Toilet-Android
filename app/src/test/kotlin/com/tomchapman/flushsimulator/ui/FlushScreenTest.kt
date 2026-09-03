package com.tomchapman.flushsimulator.ui

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.tomchapman.flushsimulator.core.FlushEngine
import com.tomchapman.flushsimulator.core.FlushGrade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * The screen actually working, rather than merely rendering.
 *
 * A screenshot cannot tell you whether the handle is wired to anything. This drives
 * real touches at the lever and checks the engine on the other side: that a hold of a
 * given length grades the way the rules say, that the tally only moves once the water
 * settles, and that a gesture the system cancels is not a flush.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FlushScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var engine: FlushEngine
    private var scope: CoroutineScope? = null

    /**
     * The engine's scope is main-confined, and the main looper is shared between every
     * Robolectric test in the run. Left uncancelled, one test's settle can still be
     * queued when the next one starts advancing the clock — which is exactly the kind
     * of failure that only appears when the whole suite runs.
     */
    @After
    fun tearDown() {
        scope?.cancel()
        scope = null
    }

    private fun start(saved: Map<String, Any> = emptyMap()) {
        compose.mainClock.autoAdvance = false
        val own = CoroutineScope(Dispatchers.Main)
        scope = own
        engine = FlushEngine(settings = MapSettings(saved), scope = own)
        compose.setContent { FlushScreen(engine) }
        compose.mainClock.advanceTimeByFrame()
    }

    /**
     * Moves both clocks.
     *
     * The engine settles a flush with `delay` on the main dispatcher, which Robolectric
     * runs off the looper; Compose's `mainClock` only drives frames. Advancing one and
     * not the other leaves the flush running forever.
     */
    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.mainClock.advanceTimeBy(millis)
    }

    /** Presses the handle, holds for [millis] of event time, and lets go. */
    private fun pullFor(millis: Long) {
        compose.onNodeWithContentDescription("Flush handle", substring = true)
            .performTouchInput {
                // The lever sits in the upper-left of the fixture; the hit area around
                // it is generous on purpose.
                val at = Offset(width * 0.28f, height * 0.14f)
                down(at)
                advanceEventTime(millis)
                up()
            }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `a held handle starts a flush and the tally waits for the water`() {
        start()
        pullFor(700)   // inside the perfect window

        assertEquals(FlushGrade.Perfect, engine.state.value.grade)
        assertTrue("the bowl should be flushing", engine.state.value.isFlushing)
        assertEquals(0, engine.state.value.totalFlushes)

        advance(4_000)
        assertEquals(1, engine.state.value.totalFlushes)
        assertEquals(1, engine.state.value.streak)
        assertNotNull("the app should say something", engine.state.value.message)
    }

    @Test
    fun `a quick tap is only half a flush`() {
        start()
        pullFor(100)
        assertEquals(FlushGrade.Weak, engine.state.value.grade)

        advance(4_000)
        assertEquals(1, engine.state.value.totalFlushes)
        assertEquals("a weak pull keeps no streak", 0, engine.state.value.streak)
    }

    @Test
    fun `leaning on the handle counts, but nobody is impressed`() {
        start()
        pullFor(2_000)
        assertEquals(FlushGrade.Overheld, engine.state.value.grade)
    }

    @Test
    fun `a cancelled gesture is not a flush`() {
        start()
        compose.onNodeWithContentDescription("Flush handle", substring = true)
            .performTouchInput {
                down(Offset(width * 0.28f, height * 0.14f))
                advanceEventTime(700)
                cancel()
            }
        advance(4_000)

        assertTrue(!engine.state.value.isFlushing)
        assertEquals(0, engine.state.value.totalFlushes)
    }

    @Test
    fun `a touch away from the handle does nothing`() {
        start()
        compose.onNodeWithContentDescription("Flush handle", substring = true)
            .performTouchInput {
                // Down by the foot of the bowl, nowhere near the lever.
                down(Offset(width * 0.5f, height * 0.9f))
                advanceEventTime(700)
                up()
            }
        advance(4_000)

        assertEquals(0, engine.state.value.totalFlushes)
        assertNull(engine.state.value.message)
    }

    @Test
    fun `the tally survives being read back off the save`() {
        start(mapOf("totalFlushes" to 24))
        pullFor(700)
        advance(5_000)

        assertEquals(25, engine.state.value.totalFlushes)
        // And the twenty-fifth is what earns the outhouse.
        assertEquals("Unlocked — The Outhouse", engine.state.value.message?.text)
    }
}
