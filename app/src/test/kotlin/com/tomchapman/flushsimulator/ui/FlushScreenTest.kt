package com.tomchapman.flushsimulator.ui

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.tomchapman.flushsimulator.core.FlushEngine
import com.tomchapman.flushsimulator.core.FlushGrade
import com.tomchapman.flushsimulator.core.Upkeep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        engine = FlushEngine(settings = MapSettings(saved), scope = own, random = kotlin.random.Random(7))
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

    // The roll

    @Test
    fun `dragging the sheet down pulls squares, and a swipe across tears them off`() {
        start()
        val roll = compose.onNodeWithContentDescription("Toilet paper", substring = true)

        roll.performTouchInput {
            // One square is 26 of the roll's 232 design units.
            val square = height / 232f * 26f
            down(Offset(width / 2f, height * 0.3f))
            advanceEventTime(50)
            moveBy(Offset(0f, square * 3.4f))
            advanceEventTime(50)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(3, engine.state.value.paperPulled)
        assertFalse(engine.state.value.isPaperCut)

        roll.performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            advanceEventTime(50)
            moveBy(Offset(width * 0.6f, 0f))     // well past the 34-unit tear threshold
            advanceEventTime(50)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the sheet should be torn", engine.state.value.isPaperCut)
        assertEquals(3, engine.state.value.loadedPaper)
    }

    // The plunger

    /**
     * An uncut sheet blocks the bowl every time, so the blockage here needs no luck;
     * only that the sheet was not the one-in-a-hundred, which the seed settles.
     */
    private fun block() {
        engine.pullPaper(2)
        assertFalse(engine.state.value.isCashRoll)
        engine.pullHandle(FlushGrade.Perfect)
        advance((engine.state.value.activeProfile.duration * 1_000).toLong() + 1)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the bowl should be blocked", engine.state.value.isClogged)
    }

    @Test
    fun `the plunger only bites once the paper is cut free`() {
        start()
        block()
        assertTrue(engine.state.value.isPaperTrailing)

        // Pumping with the paper still attached does nothing but complain.
        engine.plunge()
        assertEquals(0, engine.state.value.plunges)
        assertEquals("It's still attached. Cut it.", engine.state.value.message?.text)

        engine.cutPaper()
        assertFalse(engine.state.value.isPaperTrailing)
    }

    @Test
    fun `dragging the plunger onto the bowl and pushing down clears the blockage`() {
        start()
        block()
        engine.cutPaper()
        // The plunger reads whether the paper is attached off its last composition, so
        // the cut has to reach the screen before the gesture starts.
        advance(32)

        compose.onNodeWithContentDescription("Plunger", substring = true).performTouchInput {
            // The box is 104 design units wide. It rests at (410, 346) with its cup 46
            // below centre; the bowl is at (235, 216). Move the cup onto the bowl, then
            // push down a stroke at a time.
            val unit = width / 104f
            down(Offset(width / 2f, height * 0.3f))
            advanceEventTime(30)
            moveBy(Offset(-175f * unit, -176f * unit))
            advanceEventTime(30)
            repeat(Upkeep.PLUNGES_TO_CLEAR + 1) {
                moveBy(Offset(0f, 26f * unit))     // past the 22-unit stroke
                advanceEventTime(30)
            }
            up()
        }
        compose.mainClock.advanceTimeByFrame()

        assertFalse("five pumps should clear it", engine.state.value.isClogged)
        assertEquals(0, engine.state.value.plunges)
    }
}
