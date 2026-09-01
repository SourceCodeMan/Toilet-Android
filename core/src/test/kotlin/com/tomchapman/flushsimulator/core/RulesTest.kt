package com.tomchapman.flushsimulator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The handle: where the window sits, and what each pull does to the flush. */
class FlushGradeTest {

    @Test
    fun `the window is where the constants say it is`() {
        assertEquals(FlushGrade.Weak, FlushGrade.forHold(0.0))
        assertEquals(FlushGrade.Weak, FlushGrade.forHold(0.339))
        assertEquals(FlushGrade.Good, FlushGrade.forHold(0.34))     // weak ends exclusive
        assertEquals(FlushGrade.Good, FlushGrade.forHold(0.549))
        assertEquals(FlushGrade.Perfect, FlushGrade.forHold(0.55))  // window opens
        assertEquals(FlushGrade.Perfect, FlushGrade.forHold(0.879))
        assertEquals(FlushGrade.Good, FlushGrade.forHold(0.88))     // window closes
        assertEquals(FlushGrade.Good, FlushGrade.forHold(1.349))
        assertEquals(FlushGrade.Overheld, FlushGrade.forHold(1.35))
        assertEquals(FlushGrade.Overheld, FlushGrade.forHold(99.0))
    }

    @Test
    fun `only a perfect pull keeps a streak, and only a bad one breaks it`() {
        assertTrue(FlushGrade.Perfect.keepsStreak)
        assertTrue(!FlushGrade.Good.keepsStreak)
        assertTrue(FlushGrade.Weak.breaksStreak)
        assertTrue(FlushGrade.Overheld.breaksStreak)
        // A good pull neither keeps nor breaks: the streak simply stands.
        assertTrue(!FlushGrade.Good.keepsStreak && !FlushGrade.Good.breaksStreak)
    }

    @Test
    fun `a good pull leaves the fixture untouched`() {
        val p = FlushProfile.Standard
        assertSame(p, FlushGrade.Good.applyTo(p))
    }

    @Test
    fun `a weak pull is a smaller, shorter, quieter flush`() {
        val p = FlushProfile.Standard
        val weak = FlushGrade.Weak.applyTo(p)
        assertTrue(weak.surgePeak < p.surgePeak)
        assertTrue(weak.spinPeak < p.spinPeak)
        assertTrue(weak.duration < p.duration)
        assertTrue(weak.rumbleScale < p.rumbleScale)
        // Still surges above resting, or it would not read as a flush at all.
        assertTrue(weak.surgePeak > p.restingLevel)
    }

    @Test
    fun `a perfect pull never brims over`() {
        for (fixture in Fixture.all) {
            val perfect = FlushGrade.Perfect.applyTo(fixture.profile)
            assertTrue(perfect.surgePeak <= 0.995, "${fixture.name} brimmed at ${perfect.surgePeak}")
            assertTrue(perfect.spinPeak > fixture.profile.spinPeak)
        }
    }

    @Test
    fun `holding too long is all the noise and none of the grace`() {
        val p = FlushProfile.Standard
        val held = FlushGrade.Overheld.applyTo(p)
        assertTrue(held.spinPeak < p.spinPeak)       // less swirl
        assertTrue(held.rumbleScale > p.rumbleScale) // more shaking
        assertTrue(held.duration > p.duration)       // and it drags
    }
}

/** Grime, paper, gold and clogs — the loop that makes it a game. */
class UpkeepTest {

    @Test
    fun `a filthy bowl produces no gold at all`() {
        assertEquals(0.0, Upkeep.goldenChance(streak = 0, grime = Upkeep.FILTHY_ABOVE))
        assertEquals(0.0, Upkeep.goldenChance(streak = 99, grime = 1.0))
    }

    @Test
    fun `a clean bowl beats a grimy one`() {
        val clean = Upkeep.goldenChance(0, Upkeep.CLEAN_BELOW)
        val middling = Upkeep.goldenChance(0, 0.4)
        val grimy = Upkeep.goldenChance(0, Upkeep.GRIMY_ABOVE)
        assertTrue(clean > middling, "clean $clean should beat middling $middling")
        assertTrue(middling > grimy, "middling $middling should beat grimy $grimy")
    }

    @Test
    fun `a streak helps, but only up to the cap`() {
        val none = Upkeep.goldenChance(0, 0.4)
        val some = Upkeep.goldenChance(3, 0.4)
        val capped = Upkeep.goldenChance(Upkeep.GOLDEN_STREAK_CAP, 0.4)
        val beyond = Upkeep.goldenChance(Upkeep.GOLDEN_STREAK_CAP + 50, 0.4)
        assertTrue(some > none)
        assertTrue(capped > some)
        assertEquals(capped, beyond, 1e-12, "past the cap a streak stops paying")
    }

    @Test
    fun `gold never gets more common than the best odds`() {
        for (streak in 0..100) {
            for (grime in listOf(0.0, 0.1, 0.3, 0.7, 0.87)) {
                val p = Upkeep.goldenChance(streak, grime)
                assertTrue(p <= 1.0 / Upkeep.GOLDEN_BEST_ODDS + 1e-12, "streak=$streak grime=$grime gave $p")
            }
        }
    }

    @Test
    fun `one square never blocks anything`() {
        for (grime in listOf(0.0, 0.5, 1.0)) {
            for (grade in FlushGrade.entries) {
                assertEquals(0.0, Upkeep.clogChance(0, grime, grade, 1.0))
                assertEquals(0.0, Upkeep.clogChance(1, grime, grade, 1.0))
            }
        }
    }

    @Test
    fun `more paper and more grime compound`() {
        val two = Upkeep.clogChance(2, 0.0, FlushGrade.Good, 1.0)
        val five = Upkeep.clogChance(5, 0.0, FlushGrade.Good, 1.0)
        assertTrue(five > two * 5, "paper should compound, not merely add: $two -> $five")

        val clean = Upkeep.clogChance(3, 0.0, FlushGrade.Good, 1.0)
        val filthy = Upkeep.clogChance(3, 1.0, FlushGrade.Good, 1.0)
        assertTrue(filthy > clean)
    }

    @Test
    fun `a perfect pull forgives, a weak one does not`() {
        fun odds(grade: FlushGrade) = Upkeep.clogChance(4, 0.5, grade, 1.0)
        assertTrue(odds(FlushGrade.Perfect) < odds(FlushGrade.Good))
        assertTrue(odds(FlushGrade.Good) < odds(FlushGrade.Overheld))
        assertTrue(odds(FlushGrade.Overheld) < odds(FlushGrade.Weak))
    }

    @Test
    fun `a tolerant drain swallows more`() {
        val domestic = Upkeep.clogChance(4, 0.4, FlushGrade.Good, Fixture.Standard.tolerance)
        val outhouse = Upkeep.clogChance(4, 0.4, FlushGrade.Good, Fixture.Outhouse.tolerance)
        val commercial = Upkeep.clogChance(4, 0.4, FlushGrade.Good, Fixture.Chrome.tolerance)
        assertTrue(outhouse > domestic, "the outhouse should block easiest")
        assertTrue(commercial < domestic, "commercial grade should block least")
    }

    @Test
    fun `clog odds never certain`() {
        for (paper in 0..5) for (grime in listOf(0.0, 0.5, 1.0)) for (g in FlushGrade.entries) {
            val p = Upkeep.clogChance(paper, grime, g, 0.2)
            assertTrue(p in 0.0..0.85, "paper=$paper grime=$grime gave $p")
        }
    }

    @Test
    fun `an empty flush is worth less than one square`() {
        assertTrue(Upkeep.points(0, golden = false) < Upkeep.points(1, golden = false))
    }

    @Test
    fun `paper pays, with diminishing returns`() {
        val points = (0..5).map { Upkeep.points(it, golden = false) }
        // Rises from one square on.
        for (i in 1 until points.size - 1) {
            assertTrue(points[i + 1] > points[i], "$points did not rise at $i")
        }
        // And the steps get smaller.
        val steps = (1 until points.size - 1).map { points[it + 1] - points[it] }
        assertTrue(steps.first() > steps.last(), "returns should diminish: $steps")
    }

    @Test
    fun `gold triples it`() {
        for (paper in 0..5) {
            assertEquals(
                Upkeep.points(paper, golden = false) * 3,
                Upkeep.points(paper, golden = true),
                "paper=$paper",
            )
        }
    }
}

/** Unearned titles. */
class RankTest {

    @Test
    fun `you start a rookie and end royal`() {
        assertEquals("Bathroom Rookie", Rank.current(0).title)
        assertEquals("Their Royal Flushness", Rank.current(1_000).title)
        assertEquals("Their Royal Flushness", Rank.current(999_999).title)
    }

    @Test
    fun `each threshold promotes you exactly on the flush that earns it`() {
        for (rank in Rank.all) {
            assertEquals(rank.title, Rank.current(rank.threshold).title)
            if (rank.threshold > 0) {
                assertTrue(Rank.current(rank.threshold - 1).title != rank.title)
            }
        }
    }

    @Test
    fun `progress runs zero to one between ranks and maxes out at the top`() {
        assertEquals(0.0, Rank.progress(0), 1e-9)
        assertEquals(1.0, Rank.progress(1_000), 1e-9)
        assertEquals(1.0, Rank.progress(5_000), 1e-9)
        for (flushes in 0..1_200) {
            assertTrue(Rank.progress(flushes) in 0.0..1.0, "progress out of range at $flushes")
        }
    }

    @Test
    fun `there is always a next rank until the last one`() {
        assertNotNull(Rank.next(0))
        assertEquals(1_000, Rank.next(999)?.threshold)
        assertNull(Rank.next(1_000))
    }

    @Test
    fun `thresholds only ever climb`() {
        assertEquals(Rank.all.map { it.threshold }.sorted(), Rank.all.map { it.threshold })
    }
}

/** The catalogue. */
class FixtureTest {

    @Test
    fun `ids are unique and resolvable`() {
        assertEquals(Fixture.all.size, Fixture.all.map { it.id }.toSet().size)
        for (fixture in Fixture.all) {
            assertEquals(fixture, Fixture.withId(fixture.id))
        }
    }

    @Test
    fun `an unknown id falls back to the standard toilet`() {
        assertEquals(Fixture.Standard, Fixture.withId("a toilet that does not exist"))
        assertEquals(Fixture.Standard, Fixture.withId(""))
    }

    @Test
    fun `the first one is free and the rest are earned in order`() {
        assertEquals(0, Fixture.all.first().unlockAt)
        assertEquals(Fixture.all.map { it.unlockAt }.sorted(), Fixture.all.map { it.unlockAt })
    }

    @Test
    fun `every fixture has a palette in both schemes`() {
        for (fixture in Fixture.all) {
            for (dark in listOf(false, true)) {
                val palette = fixture.palette(dark)
                // Opaque where the Swift was opaque: a transparent porcelain would
                // mean a component was dropped in the copy.
                assertEquals(255, palette.porcelainLight.alpha, "${fixture.name} dark=$dark")
                assertEquals(255, palette.waterDark.alpha, "${fixture.name} dark=$dark")
            }
        }
    }

    @Test
    fun `fixtures compare by id, not by their palette closures`() {
        // A data class would have compared the lambdas and never matched.
        val copy = Fixture.Standard.copy(name = "Renamed")
        assertEquals(Fixture.Standard, copy)
        assertEquals(Fixture.Standard.hashCode(), copy.hashCode())
    }
}

/** Colour, carried as a value rather than as a piece of UI. */
class ArgbTest {

    @Test
    fun `components round-trip`() {
        val c = rgb(1.0, 0.0, 0.5)
        assertEquals(255, c.alpha)
        assertEquals(255, c.red)
        assertEquals(0, c.green)
        assertEquals(128, c.blue)
    }

    @Test
    fun `opacity keeps the colour and changes only the alpha`() {
        val faded = Argb.White.opacity(0.35)
        assertEquals(89, faded.alpha)
        assertEquals(255, faded.red)
        assertEquals(255, faded.green)
        assertEquals(255, faded.blue)
    }

    @Test
    fun `out of range components clamp rather than wrap`() {
        val c = rgb(2.0, -1.0, 0.5)
        assertEquals(255, c.red)
        assertEquals(0, c.green)
    }
}
