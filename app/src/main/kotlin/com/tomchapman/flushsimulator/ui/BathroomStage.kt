package com.tomchapman.flushsimulator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.tomchapman.flushsimulator.core.FlushGrade
import com.tomchapman.flushsimulator.core.FlushState
import com.tomchapman.flushsimulator.core.Palette
import kotlin.math.min

/**
 * The room and everything in it you can touch.
 *
 * The toilet is still drawn at its own fixed size and knows nothing about any of
 * this; the stage is a wider canvas that places it, hangs the roll on the wall to its
 * left and stands the plunger on the floor to its right. Keeping the toilet's own
 * coordinate space untouched is what makes that cheap — every position in `Toilet`
 * still means what it always did.
 *
 * Laid out by hand in dp rather than by scaling a layout: each object gets a slot of
 * exactly its design size times [scale], so its gestures need no transform and its
 * own canvas fills its own box.
 */
object BathroomStage {
    const val WIDTH = 470f
    const val HEIGHT = 470f

    /** Where the toilet's own 320-wide canvas begins inside this one. */
    const val TOILET_X = (WIDTH - Toilet.WIDTH) / 2

    /** The bowl, in stage coordinates. `Toilet` draws its water at (160, 218). */
    val bowl = Offset(TOILET_X + 160f, 216f)

    /** Where the plunger leans when nothing is blocked, as the centre of its box. */
    val plungerHome = Offset(410f, 346f)

    /** Where the roll hangs, as the centre of its box. */
    val rollAt = Offset(54f, 122f)

    /**
     * Where the toilet's foot meets the ground, in stage units. `Toilet` draws the
     * foot as a 28-tall bar centred on 398, so it ends here.
     */
    const val FLOOR_LINE = 412f
}

/**
 * @param onFloorLine reports where the toilet's feet land, in root pixels, so the room
 * behind can draw its floor to meet them.
 */
@Composable
fun BathroomStage(
    state: FlushState,
    palette: Palette,
    onPull: (FlushGrade) -> Unit,
    onPress: () -> Unit,
    onPullPaper: (Int) -> Unit,
    onCutPaper: () -> Unit,
    onPump: () -> Unit,
    onFloorLine: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var plungerOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / BathroomStage.WIDTH, maxHeight.value / BathroomStage.HEIGHT)

        Box(
            Modifier
                .size((BathroomStage.WIDTH * scale).dp, (BathroomStage.HEIGHT * scale).dp)
                .onGloballyPositioned { coords ->
                    val top = coords.positionInRoot().y
                    val perUnit = coords.size.height / BathroomStage.HEIGHT
                    onFloorLine(top + BathroomStage.FLOOR_LINE * perUnit)
                },
        ) {
            Toilet(
                palette = palette,
                profile = state.activeProfile,
                grime = state.grime,
                flushStartMillis = state.flushStartMillis,
                onPull = onPull,
                onPress = onPress,
                modifier = Modifier
                    .offset((BathroomStage.TOILET_X * scale).dp, 0.dp)
                    .size((Toilet.WIDTH * scale).dp, (Toilet.HEIGHT * scale).dp),
            )

            PaperRoll(
                pulled = state.paperPulled,
                isCut = state.isPaperCut,
                isTrailing = state.isPaperTrailing,
                isCash = state.isCashRoll,
                palette = palette,
                scale = scale,
                onPull = onPullPaper,
                onCut = onCutPaper,
                modifier = Modifier
                    .offset(
                        ((BathroomStage.rollAt.x - PaperRoll.WIDTH / 2) * scale).dp,
                        ((BathroomStage.rollAt.y - PaperRoll.HEIGHT / 2) * scale).dp,
                    )
                    .size((PaperRoll.WIDTH * scale).dp, (PaperRoll.HEIGHT * scale).dp),
            )

            Plunger(
                isClogged = state.isClogged,
                plunges = state.plunges,
                isBlockedByPaper = state.isPaperTrailing,
                bowl = BathroomStage.bowl,
                home = BathroomStage.plungerHome,
                offset = plungerOffset,
                onOffset = { plungerOffset = it },
                scale = scale,
                onPump = onPump,
                modifier = Modifier
                    .offset(
                        ((BathroomStage.plungerHome.x - Plunger.WIDTH / 2 + plungerOffset.x) * scale).dp,
                        ((BathroomStage.plungerHome.y - Plunger.HEIGHT / 2 + plungerOffset.y) * scale).dp,
                    )
                    .size((Plunger.WIDTH * scale).dp, (Plunger.HEIGHT * scale).dp),
            )
        }
    }
}
