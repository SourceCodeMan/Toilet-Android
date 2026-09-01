package com.tomchapman.flushsimulator.core

/** What the room around a fixture is made of. */
enum class RoomSurface {
    Tile,       // ordinary bathroom
    Planks,     // weathered boards
    Ornate,     // panelling and gilt
    Panels,     // brushed steel seams
    Bulkhead,   // an instrument-lit hull
}

/**
 * A toilet you can own.
 *
 * A fixture is only ever a palette and a set of numbers — no new geometry, no new
 * layout. The porcelain is drawn from [Palette], and the flush is driven by
 * [FlushProfile], so a new toilet costs about thirty lines and no new drawing code.
 */
data class Fixture(
    val id: String,
    val name: String,

    /** The line shown when it unlocks, and in the picker underneath the name. */
    val blurb: String,

    /** Lifetime flushes needed. The first one is free. */
    val unlockAt: Int,

    /**
     * How much abuse the drain takes before it blocks. 1.0 is ordinary domestic
     * plumbing; below that blocks easily, above that swallows nearly anything.
     */
    val tolerance: Double,

    /**
     * The icon for the picker chip, as the original SF Symbol name — a stable
     * identifier, mapped to a real drawable by the Compose layer.
     */
    val symbol: String,

    /** What the walls and floor are made of. */
    val surface: RoomSurface,

    /** How this one flushes and how it sounds. */
    val profile: FlushProfile,

    /** Built per colour scheme, the same way [Palette.standard] always was. */
    val palette: (Boolean) -> Palette,
) {
    // Identity is the id, as it was in the Swift: two fixtures with the same id are
    // the same toilet however their numbers were built. Hand-written because a data
    // class would otherwise compare the palette lambdas, which are never equal.
    override fun equals(other: Any?): Boolean = other is Fixture && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {

        // The catalogue

        val all: List<Fixture> by lazy { listOf(Standard, Outhouse, Victorian, Chrome, Orbital) }

        fun withId(id: String): Fixture = all.firstOrNull { it.id == id } ?: Standard

        // Fixtures

        val Standard = Fixture(
            id = "standard",
            name = "Standard Issue",
            blurb = "The one you already have.",
            unlockAt = 0,
            tolerance = 1.0,
            symbol = "house.fill",
            surface = RoomSurface.Tile,
            profile = FlushProfile.Standard,
            palette = Palette::standard,
        )

        /** Slow, hollow, and wooden. Barely any tank to speak of. */
        val Outhouse = Fixture(
            id = "outhouse",
            name = "The Outhouse",
            blurb = "No plumbing. Just gravity and hope.",
            unlockAt = 25,
            tolerance = 0.55,
            symbol = "tree.fill",
            surface = RoomSurface.Planks,
            profile = FlushProfile(
                duration = 4.2,
                restingLevel = 0.38,
                surgePeak = 0.72,
                spinPeak = 700.0,
                rumbleScale = 2.6,
                chop = 0.020,
                clunkFrequency = 62.0,
                roarFrom = 640.0,
                roarTo = 180.0,
                bodyFrequency = 96.0,
                gurgleCentre = 380.0,
                gurgleSwing = 190.0,
                hissFrom = 900.0,
                hissTo = 1_250.0,
                valveFrequency = 74.0,
            ),
            palette = Palette::outhouse,
        )

        /** A high cistern and a long chain. Takes its time, and is smug about it. */
        val Victorian = Fixture(
            id = "victorian",
            name = "Victorian Throne",
            blurb = "A high cistern, and no hurry whatsoever.",
            unlockAt = 100,
            tolerance = 0.8,
            symbol = "crown.fill",
            surface = RoomSurface.Ornate,
            profile = FlushProfile(
                duration = 4.6,
                restingLevel = 0.55,
                surgePeak = 0.99,
                spinPeak = 1_050.0,
                rumbleScale = 1.2,
                chop = 0.010,
                clunkFrequency = 128.0,
                roarFrom = 1_020.0,
                roarTo = 300.0,
                bodyFrequency = 140.0,
                gurgleCentre = 520.0,
                gurgleSwing = 300.0,
                hissFrom = 1_700.0,
                hissTo = 2_600.0,
                valveFrequency = 96.0,
            ),
            palette = Palette::victorian,
        )

        /** Airport-grade. Violent, brief, and far too loud. */
        val Chrome = Fixture(
            id = "chrome",
            name = "Chrome Pressure",
            blurb = "Commercial grade. Startles everyone.",
            unlockAt = 400,
            tolerance = 1.9,
            symbol = "bolt.fill",
            surface = RoomSurface.Panels,
            profile = FlushProfile(
                duration = 2.6,
                restingLevel = 0.48,
                surgePeak = 0.88,
                spinPeak = 2_300.0,
                rumbleScale = 3.1,
                chop = 0.022,
                clunkFrequency = 150.0,
                roarFrom = 2_100.0,
                roarTo = 520.0,
                bodyFrequency = 210.0,
                gurgleCentre = 880.0,
                gurgleSwing = 150.0,
                hissFrom = 3_000.0,
                hissTo = 4_400.0,
                valveFrequency = 190.0,
            ),
            palette = Palette::chrome,
        )

        /** Vacuum assisted. There is no water, and there is no down. */
        val Orbital = Fixture(
            id = "orbital",
            name = "Orbital Vacuum",
            blurb = "In space, everyone can hear it.",
            unlockAt = 1_000,
            tolerance = 1.45,
            symbol = "moon.stars.fill",
            surface = RoomSurface.Bulkhead,
            profile = FlushProfile(
                duration = 3.0,
                restingLevel = 0.30,
                surgePeak = 0.60,
                spinPeak = 3_200.0,
                rumbleScale = 0.8,
                chop = 0.006,
                clunkFrequency = 220.0,
                roarFrom = 3_400.0,
                roarTo = 240.0,
                bodyFrequency = 280.0,
                gurgleCentre = 1_400.0,
                gurgleSwing = 600.0,
                hissFrom = 4_200.0,
                hissTo = 6_000.0,
                valveFrequency = 300.0,
            ),
            palette = Palette::orbital,
        )
    }
}
