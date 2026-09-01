# Flush Simulator — Android

An Android port of [Flush Simulator](https://github.com/SourceCodeMan/Toilet-iOS), which
is a picture of a toilet you push the handle on.

**Status: Tier 1 of 3.** The rules of the game are ported and tested. The drawing, the
sound and the buzz are not here yet.

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
| `Platform.kt` | The seams: storage, audio, haptics, clock |

`board/` — the global leaderboard, a Cloudflare Worker over D1, serving both platforms.
Written and tested, **not deployed yet**; see [board/README.md](board/README.md) for
the deploy steps and for what its plausibility caps do and don't stop.

## What's next

- **Tier 2 — the drawing.** An `:app` module: the fixture, the water, the vortex and
  the confetti, redrawn in Compose. Needs the Android SDK, which is why it is not in
  this build yet. This is also where the board gets a client and a Global tab.
- **Tier 3 — the device.** The synthesised flush against `AudioTrack`, and the rumble
  against `Vibrator`.

## Running the tests

```sh
./gradlew :core:test
```

94 tests, no emulator, about a second. That is the entire point of `core` being a
plain JVM module: the flush maths, the grading, the upkeep loop, the save format and
the whole engine are covered by ordinary unit tests.

Android Studio ships its own JDK, so nothing needs installing first — and on a machine
without one, the build fetches a matching toolchain rather than failing.

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

- **Haptic sharpness.** CoreHaptics events carry intensity *and* sharpness, plus
  parameter curves. Android has amplitude waveforms (API 26+) and composition
  primitives (API 30+, device-dependent) but no sharpness at all, so the crisp lever
  tick and the dull thud will be less distinct than on iOS. The swelling rumble can be
  approximated by quantising the intensity curve into amplitude steps.

- **The global leaderboard.** `GlobalBoard.swift` is Game Center, which has no drop-in
  Android equivalent — and does not currently work on iOS either, since the Xcode
  project has no Game Center entitlement. Replaced rather than ported: see `board/`.
