package com.tomchapman.flushsimulator.core

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Grime, paper, and the odds of blocking the thing.
 *
 * These three are one system, not three. Flushing dirties the bowl; a dirty bowl
 * blocks more easily; paper is the thing worth having and the thing that blocks it.
 * Every number that balances that loop lives here so it can be tuned in one place.
 */
object Upkeep {

    // Grime

    /**
     * How much filth one flush leaves behind. Forty flushes takes a clean bowl to a
     * filthy one.
     */
    const val GRIME_PER_FLUSH = 1.0 / 40.0

    /** At or below this, the bowl counts as clean and the golden odds improve. */
    const val CLEAN_BELOW = 0.20

    /**
     * At or above this, the app starts saying something about it — and gold becomes
     * scarce.
     */
    const val GRIMY_ABOVE = 0.65

    /**
     * At or above this the bowl is a health hazard: gold is impossible and a flush
     * costs you your streak. Neglect has to bite, or the wand is decoration.
     */
    const val FILTHY_ABOVE = 0.88

    /** What a grimy-but-not-filthy bowl does to the golden odds. */
    const val GRIMY_GOLD_PENALTY = 0.20

    // Paper

    /** Squares you can put in. Zero is allowed, and pointless. */
    val PAPER_RANGE = 0..5

    /** Where the app starts you. */
    const val DEFAULT_PAPER = 2

    /**
     * What an uncut sheet counts as. The bowl keeps drawing off the roll for the whole
     * flush, so it is far past anything you could have hung there on purpose — which
     * is the point: forgetting to tear is not a small mistake.
     */
    const val RUNAWAY_PAPER = 12

    /**
     * Score multiplier for a flush, by squares used.
     *
     * Rises fast then flattens, so there is a real reason to push past two and a
     * diminishing one to go all the way to five. Using none is deliberately worse
     * than using one: a flush with nothing in it is not worth anything.
     */
    fun multiplierForPaper(squares: Int): Double = when {
        squares < 1 -> 0.5
        squares == 1 -> 1.0
        squares == 2 -> 1.4
        squares == 3 -> 1.8
        squares == 4 -> 2.1
        else -> 2.3
    }

    /** What a golden flush is worth over an ordinary one. */
    const val GOLDEN_BONUS = 3.0

    /**
     * What one flush is worth on the board.
     *
     * The multiplier above is the whole reason to risk more paper, so it has to land
     * somewhere the player can see. Without this, paper is pure downside: more grime
     * and more clogs for nothing.
     */
    fun points(paper: Int, golden: Boolean): Int {
        val base = 100.0 * multiplierForPaper(paper)
        return (if (golden) base * GOLDEN_BONUS else base).roundToInt()
    }

    // The lucky roll

    /**
     * One roll in this many comes off the wall as hundreds instead of paper.
     *
     * Benjamin's idea, and a good one: the roll is the thing you touch before every
     * single flush, so it is exactly where a rare surprise pays off. Rolled once per
     * sheet rather than per pull, or you could just yo-yo the roll until it hit.
     */
    const val CASH_ODDS = 100

    /**
     * What flushing money is worth. Absurd on purpose — this should be the best thing
     * that happens to you all week.
     */
    const val CASH_MULTIPLIER = 10.0

    // The tank

    /**
     * Flushes in one tank. The whole reason a session has a shape: without a bound,
     * nothing you do is a decision, because there is always another flush.
     */
    const val RUN_LENGTH = 20

    /**
     * Scrubbing runs clean water through, so it costs the tank the same as a flush.
     * This is what gives grime a price — a free wand makes neglect free.
     */
    const val WAND_COST = 1

    // Gold

    /** One flush in this many is golden with a filthy bowl and no streak going. */
    const val GOLDEN_BASE_ODDS = 34

    /** A spotless bowl is worth this much more. */
    const val GOLDEN_CLEAN_BONUS = 1.30

    /**
     * Each perfect pull in the current run adds this much, up to [GOLDEN_STREAK_CAP]
     * pulls. Capped because an unbroken run used to drive the odds into the floor and
     * gold stopped feeling like anything.
     */
    const val GOLDEN_STREAK_STEP = 0.14
    const val GOLDEN_STREAK_CAP = 6

    /** However well you play, gold never gets more common than one in this many. */
    const val GOLDEN_BEST_ODDS = 12

    /** The chance the next flush is golden, 0..1. */
    fun goldenChance(streak: Int, grime: Double): Double {
        // A filthy bowl does not produce gold. At all.
        if (grime >= FILTHY_ABOVE) return 0.0

        var p = 1.0 / GOLDEN_BASE_ODDS
        if (grime <= CLEAN_BELOW) p *= GOLDEN_CLEAN_BONUS
        if (grime >= GRIMY_ABOVE) p *= GRIMY_GOLD_PENALTY
        p *= 1 + minOf(streak, GOLDEN_STREAK_CAP) * GOLDEN_STREAK_STEP
        return minOf(p, 1.0 / GOLDEN_BEST_ODDS)
    }

    // Clogging

    /**
     * The chance this flush blocks, 0..1.
     *
     * Paper is the main driver and grime multiplies it, so neglect and greed compound
     * rather than merely adding up. A perfect pull pushes more water through and
     * forgives some of it.
     */
    fun clogChance(paper: Int, grime: Double, grade: FlushGrade, tolerance: Double): Double {
        if (paper <= 1) return 0.0   // one square never blocks anything

        // 2 squares -> 0.02, 5 squares -> 0.20, before anything else touches it.
        val fromPaper = ((paper - 1) / 4.0).pow(1.7) * 0.20
        val fromGrime = 1.0 + grime * 1.4
        val fromPull = when (grade) {
            FlushGrade.Perfect -> 0.55     // a proper flush clears a lot
            FlushGrade.Good -> 1.0
            FlushGrade.Overheld -> 1.15
            FlushGrade.Weak -> 1.9         // half a flush leaves half of it there
        }

        return minOf(fromPaper * fromGrime * fromPull / maxOf(tolerance, 0.2), 0.85)
    }

    /** Pumps needed to clear a blockage. */
    const val PLUNGES_TO_CLEAR = 5
}
