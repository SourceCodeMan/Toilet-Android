# Flush Simulator — Android

An Android port of [Flush Simulator](https://github.com/SourceCodeMan/Toilet-iOS), which
is a picture of a toilet you push the handle on.

**Status: all three tiers ported.** The rules, the drawing, the screen, the
leaderboard, the synthesised flush and the rumble. Untested on real hardware —
nobody involved has an Android device yet.

## What's done

`:core` — everything the app *decides*, as a plain Kotlin/JVM module with no Android
dependency at all.

| | |
|---|---|
| `FlushTimeline.kt` | One flush, as pure functions of time |
| `FlushProfile.kt` | The numbers that give a flush its character |
| `FlushGrade.kt` | The hold window, and what each pull does to the flush |
| `Upkeep.kt` | Grime, paper, gold and the odds of blocking it |
| `Fixture.kt` | The five toilets, and what each is worth |
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
continuous corner radii, `.blur`, and coloured offset shadows. The chrome around it —
header, fixture bar, upkeep bar, stats card, toast — is ordinary Compose. `device/`
is the thin part: samples into an `AudioTrack`, rungs into a `Vibrator`.

`board/` — the global leaderboard, a Cloudflare Worker over D1, serving both platforms.
Written and tested, **not deployed yet**; see [board/README.md](board/README.md) for
the deploy steps and for what its plausibility caps do and don't stop.

## What's next

- **Run it on something.** Every layer is covered by tests that do not need a device,
  which is not the same as having run on one.
- **Deploy `board/`**, then put its URL in `BOARD_URL` (app/…/board/BoardClient.kt).
  Until then the Global tab says so and your own days keep being recorded.

## Running the tests

```sh
./gradlew :core:test
```

88 tests, no emulator, about a second. That is the entire point of `core` being a
plain JVM module: the flush maths, the grading, the upkeep loop, the save format and
the whole engine are covered by ordinary unit tests.

## Looking at the drawing

```sh
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```

Writes twenty-three PNGs to `app/build/screenshots` — every fixture, the four moments of a
flush, a neglected bowl, the gold, the assembled screen in four states, and the
leaderboard in three — rendered
through Robolectric on the JVM. No emulator and no device, because the only useful
question about a drawing is what it looks like, and that needs a picture rather than
an assertion.

The same run also drives real touches at the handle and checks the engine on the other
side, which is the part a picture cannot answer: that a 700ms hold grades perfect, a
tap grades weak, a cancelled gesture is not a flush at all, and the tally only moves
once the water settles.

Nothing is asserted about *how* it looks. Golden-image comparison is worth turning on
once the drawing settles; while it is still being tuned it would only cry wolf.

## Listening to the flush

`./gradlew :core:test` also writes `core/build/audio/` — every fixture, the golden
take, and the three ordinary takes of the standard toilet, as wavs. Same bargain as
the screenshots: the only useful question about a noise is what it sounds like, and no
assertion can answer that.

The synthesis itself is pure arithmetic and lives in `core`, so it renders on a plain
JVM with no Android anywhere. `device/AndroidFlushAudio` only turns floats into 16-bit
PCM and hands them to an `AudioTrack`.

Android Studio ships its own JDK, so nothing needs installing first — and on a machine
without one, the build fetches a matching toolchain rather than failing. Robolectric
needs a Java 21 runtime for its SDK 36 sandbox, so the test tasks ask for one
specifically while the app itself still compiles to 17.

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

Six things changed on purpose. Everything else is meant to be the same app.

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
