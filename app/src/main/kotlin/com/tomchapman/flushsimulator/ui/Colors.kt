package com.tomchapman.flushsimulator.ui

import androidx.compose.ui.graphics.Color
import com.tomchapman.flushsimulator.core.Argb

/**
 * `core` carries colour as packed ARGB so the rules can be tested on a plain JVM.
 * This is the one place that turns it back into something Compose can draw with.
 */
fun Argb.toColor(): Color = Color(value)
