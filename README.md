# Flush Simulator — Android

An Android port of [Flush Simulator](https://github.com/SourceCodeMan/Toilet-iOS), which
is a picture of a toilet you push the handle on.

**Status: all three tiers ported, and the gameplay expansion with them.** The rules,
the drawing, the screen, the leaderboard, the synthesised flush and the rumble, plus
the tank, the daily, the roll on the wall and the plunger on the floor. The original
loop has been played on a real phone; the expansion has been played only by tests so
far.

## What's done

`:core` — everything the app *decides*, as a plain Kotlin/JVM module with no Android
dependency at all.

| | |
|---|---|
| `FlushTimeline.kt` | One flush, as pure functions of time |
| `FlushProfile.kt` | The numbers that give a flush its character |
| `FlushGrade.kt` | The hold window, and what each pull does to the flush |
| `Upkeep.kt` | Grime, paper, gold, the tank, and the odds of blocking it |
| `Fixture.kt` | The five toilets, what each is worth and what each pays |
| `DailyChallenge.kt` | Today's puzzle, seeded so both platforms get the same one |
| `Palette.kt`, `Argb.kt` | Every colour, carried as a value rather than as UI |
| `Rank.kt`, `Quips.kt` | Unearned titles and running commentary |
| `Standings.kt` | Your best days, and the save format |
| `FlushEngine.kt`, `FlushState.kt` | The state machine, as a `StateFlow` |
| `FlushSynth.kt` | The flush noise, synthesised rather than recorded |
| `HapticPattern.kt` | The rumble, as rungs Android can play |
| `Platform.kt` | The seams: storage, audio, haptics, clock |

`app/` — the screen. The room, the fixture, the water and the confetti are drawn
rather than assembled from views: the Swift already laid the toilet out at absolute
positions in a fixed 320x470 space, so nothing needs laying out, and one `Canvas`
sidesteps the three things Compose has no cheap answer for inside a view stack —
continuous corner radii, `.blur`, and coloured offset shadows. The toilet stands in a
wider stage (`BathroomStage`) with the paper roll hung to its left and the plunger
leaning to its right, each its own `Canvas` with its own drag: pull the sheet down a
square at a time and swipe across to tear it; drag the plunger onto the bowl and push
down a stroke at a time. The chrome around it — header, fixture bar, upkeep bar, stats
card, toast, and the run, daily and payout sheets — is ordinary Compose. `device/` is
the thin part: samples into an `AudioTrack`, rungs into a `Vibrator`.

`board/` — the global leaderboard, a Cloudflare Worker over D1, serving both platforms.
Written and tested, **not deployed yet**; see [board/README.md](board/README.md) for
the deploy steps and for what its plausibility caps do and don't stop.

## What's next

- **Play the expansion.** The handle, the sound and the rumble have been tried on a
  Galaxy A15; the roll, the plunger, the tank and the daily have only been driven by
  tests.
- **Deploy `board/`**, then put its URL in `BOARD_URL` (app/…/board/BoardClient.kt).
  Until then the Global tab says so and your own days keep being recorded.
- **A release keystore**, before any of this can go to Play. The build shrinks to
  about 1.4MB with R8; it just is not signed by anything yet.

## Running the tests

```sh
./gradlew :core:test
```

129 tests, no emulator, about a second. That is the entire point of `core` being a
plain JVM module: the flush maths, the grading, the upkeep loop, the tank, the daily's
generator (checked bit-for-bit against the Swift's SplitMix64), the save format and
the whole engine are covered by ordinary unit tests. The app's own 54 run in the
screenshot task below.

## Looking at the drawing

```sh
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```

Writes thirty-four PNGs to `app/build/screenshots` — every fixture, the four moments
of a flush, a neglected bowl, the gold, the assembled screen in nine states (including
a daily in progress, paper pulled and torn, and a blocked bowl before and after the
sheet is cut free), the run and daily sheets and the payout card, and the leaderboard
in three — rendered through Robolectric on the JVM. No emulator and no device,
because the only useful question about a drawing is what it looks like, and that
needs a picture rather than an assertion.

The same run also drives real touches at the handle, the roll and the plunger and
checks the engine on the other side, which is the part a picture cannot answer: that
a 700ms hold grades perfect, a tap grades weak, a cancelled gesture is not a flush at
all, the tally only moves once the water settles, a drag down the sheet pulls three
squares and a swipe across tears them, and five strokes with the plunger seated on the
bowl clear a blockage.

Nothing is asserted about *how* it looks. Golden-image comparison is worth turning on
once the drawing settles; while it is still being tuned it would only cry wolf.

## Listening to the flush

`./gradlew :core:test` also writes `core/build/audio/` — every fixture, the golden
take, and the three ordinary takes of the standard toilet, as wavs. Same bargain as
the screenshots: the only useful question about a noise is what it sounds like, and no
assertion can answer that.

The synthesis itself is pure arithmetic and lives in `core`, so it renders on a plain
JVM with no Android anywhere. `device/AndroidFlushAudio` only turns floats into 16-bit
PCM and feeds them through a streaming `AudioTrack` — one track for the life of the
app, rather than a static buffer that has to be rewound between plays.

Android Studio ships its own JDK, so nothing needs installing first — and on a machine
without one, the build fetches a matching toolchain rather than failing. Robolectric
needs a Java 21 runtime for its SDK 36 sandbox, so the test tasks ask for one
specifically while the app itself still compiles to 17.

## The icon

```sh
pip install pillow
python3 tools/make_icon.py
```

Generated rather than hand-drawn, the same as the iOS one, so it can be reviewed as
code and redrawn after a palette change. It writes the adaptive icon's foreground and
monochrome layers at every density, the background as a vector, and
`tools/icon-preview.png` — the icon as a launcher will actually show it, beside the
same thing under a circular mask, which is the harshest of the common ones.

## How the port was done

The Swift is unusually well separated — one flush is already a set of pure functions
of elapsed time, and `FlushEngine` already took its storage by injection — so most of
this is a transcription rather than a redesign. Swift structs became data classes,
enums with payloads became sealed hierarchies, `@Published` became one `StateFlow`,
and `Task { try await Task.sleep }` became `viewModelScope.launch { delay() }`.

Every constant was checked back against the Swift source mechanically rather than by
eye: a script parses the fixture catalogue, the upkeep numbers, the grade windows and
the rank table out of the `.swift` files and diffs them against the Kotlin, and a
second transcription of `FlushTimeline` samples all five fixtures at 100 Hz and
compares the curves. 15,149 values, no disagreement beyond 4.44e-16.

The synthesiser got the same treatment: a third transcription, of `FlushAudio.swift`
this time, rendered against the Kotlin sample for sample. 5,066 samples across ten
voices, nothing off by more than 2.8e-08 — which is float32 rounding and not a
difference.

### Where this deliberately differs from the iOS app

Ten things changed on purpose. Everything else is meant to be the same app.

1. **`core` has no Android dependency.** Colours are packed ARGB ints (`Argb`) rather
   than Compose `Color`, so the rules can be tested on a plain JVM. The Compose layer
   converts at the point of drawing: `Color(palette.waterDark.value)`.

2. **One `FlushState` instead of a dozen `@Published` properties.** A single immutable
   snapshot in a `StateFlow` is the Compose idiom, and it makes a test one assertion
   rather than twelve.

3. **The engine is handed its dependencies.** Storage, audio, haptics, the clock and
   the dice all arrive through the constructor (`Platform.kt`), where the Swift reached
   for `UserDefaults.standard`, `FlushAudio.shared` and `Haptics.shared`. This is what
   makes the message cascade, the streak rules and the reset-mid-flush race testable.

4. **`Standings` is immutable.** `record(...)` returns a new value instead of mutating
   in place.

5. **The save is five numbers a row, not JSON.** `Codable` has no free equivalent
   here: kotlinx-serialization needs a compiler plugin and `org.json` is Android-only,
   which would put the save format out of reach of a JVM test. The payload is a list
   of integers, so it is stored as one, and a row that does not parse is dropped
   rather than throwing. There is no Android install base to stay compatible with.

6. **`Quips` is an instance, not a global.** It remembers the last line it gave out;
   here that state belongs to an object and takes a seedable `Random`.

7. **The daily ends when it ends.** The Swift never calls `endDaily()`, so after the
   fifth flush the handle stays refused ("Today's daily is done") until the app is
   relaunched, the "Daily done — N points" line is overwritten at once by the ordinary
   cascade, and a blockage's cost from before the daily lingers in the message. Here
   the engine ends the daily as the fifth flush settles, shows the score line last,
   and clears the cost.

8. **The plunger snaps home** when released off the bowl, where the Swift springs it
   back. A spring on a `Modifier.offset` that a drag is also writing to is a fight
   between two owners; the snap is the honest version until it is worth animating.

9. **Payout chips show the value trimmed** — ×1.25, ×1.6, ×1 — where the Swift's
   `%.2g` shows ×1.2 for the Victorian, which is not what it pays.

10. **The stage is laid out by hand in dp**, one sized slot per object, rather than
    scaling a layout. Each object's gestures then work in its own coordinates with no
    transform to undo, and the floor line behind the stage is measured off where the
    toilet's feet actually land rather than tuned as a fraction of the screen.

Not a divergence so much as a contract worth stating: the Swift's `FlushEngine` was
`@MainActor`, and Kotlin has no equivalent, so `FlushEngine` documents that it expects
a main-confined scope rather than taking locks it does not need.

### Two things the port cannot bring across

Worth knowing before Tier 3:

- **Haptic sharpness.** CoreHaptics events carry intensity *and* sharpness, laid over
  a parameter curve that reshapes them as they run. Android takes amplitudes and how
  long to hold each, so the curve is sampled into rungs — that part survives — but
  sharpness has nowhere to go. The crisp tick and the dull thud are told apart by
  their length instead, which is a poorer distinction than the iOS one. Hardware
  without `hasAmplitudeControl` loses the swell entirely and gets a rhythm.

- **The global leaderboard.** `GlobalBoard.swift` is Game Center, which has no drop-in
  Android equivalent — and does not currently work on iOS either, since the Xcode
  project has no Game Center entitlement. Replaced rather than ported: see `board/`.
