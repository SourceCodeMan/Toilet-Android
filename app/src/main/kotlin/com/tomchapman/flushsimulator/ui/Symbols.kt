package com.tomchapman.flushsimulator.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SF Symbol names, translated.
 *
 * `core` carries the original Apple symbol names because they are stable identifiers
 * for "the crown one", "the tornado one" — not because anything Android can read
 * them. This is the one place that knows what they should look like here, so a fixture
 * or a rank stays a plain data row on the other side of the fence.
 *
 * A few have no real counterpart: Material has no crown, so a premium badge stands in,
 * and `tornado` becomes a cyclone.
 */
fun symbolFor(name: String): ImageVector = when (name) {
    // Fixtures
    "house.fill" -> Icons.Filled.Home
    "tree.fill" -> Icons.Filled.Park
    "crown.fill" -> Icons.Filled.WorkspacePremium
    "bolt.fill" -> Icons.Filled.Bolt
    "moon.stars.fill" -> Icons.Filled.NightsStay

    // Ranks
    "figure.walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "hand.point.up.left.fill" -> Icons.Filled.TouchApp
    "checkmark.seal.fill" -> Icons.Filled.Verified
    "drop.fill" -> Icons.Filled.WaterDrop
    "link" -> Icons.Filled.Link
    "sparkles" -> Icons.Filled.AutoAwesome
    "shield.lefthalf.filled" -> Icons.Filled.Shield
    "tornado" -> Icons.Filled.Cyclone
    "wrench.and.screwdriver.fill" -> Icons.Filled.Build

    // Chrome
    "lock.fill" -> Icons.Filled.Lock
    "lock.open.fill" -> Icons.Filled.LockOpen
    "list.number" -> Icons.Filled.FormatListNumbered
    "speaker.wave.2.fill" -> Icons.AutoMirrored.Filled.VolumeUp
    "speaker.slash.fill" -> Icons.AutoMirrored.Filled.VolumeOff
    "flag.checkered" -> Icons.Filled.Flag
    "hourglass" -> Icons.Filled.HourglassTop
    "arrow.down.circle.fill" -> Icons.Filled.ArrowCircleDown
    "calendar" -> Icons.Filled.CalendarToday
    "scissors" -> Icons.Filled.ContentCut
    "square.and.arrow.up" -> Icons.Filled.Share
    "toilet.fill" -> Icons.Filled.Wc
    "square.stack.3d.up.fill" -> Icons.Filled.Layers
    "drop.triangle.fill" -> Icons.Filled.WaterDrop
    "arrow.triangle.2.circlepath" -> Icons.Filled.Autorenew

    // A name that reaches here is a bug in the table, not in the data, and a missing
    // glyph is not worth crashing over.
    else -> Icons.Filled.AutoAwesome
}
