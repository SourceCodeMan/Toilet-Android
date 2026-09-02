package com.tomchapman.flushsimulator.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one piece of this file that is arithmetic rather than plumbing.
 *
 * Everything else — the track, the ring, the write loop — needs real hardware to say
 * anything about. Turning floats into 16-bit is where a wrap or an off-by-one would
 * actually hide, so that part gets pinned down.
 */
class AndroidFlushAudioTest {

    @Test
    fun `full scale maps to full scale`() {
        val pcm = AndroidFlushAudio.pcm(floatArrayOf(1f, -1f, 0f))
        assertEquals(Short.MAX_VALUE, pcm[0])
        assertEquals((-Short.MAX_VALUE).toShort(), pcm[1])
        assertEquals(0.toShort(), pcm[2])
    }

    @Test
    fun `anything past full scale clamps rather than wrapping`() {
        // The synthesiser soft-clips well inside this, but a wrap here would be a
        // loud crack rather than a quiet mistake.
        val pcm = AndroidFlushAudio.pcm(floatArrayOf(2f, -2f, 99f, -99f))
        assertTrue("wrapped to ${pcm.toList()}", pcm.all { it == Short.MAX_VALUE || it == (-Short.MAX_VALUE).toShort() })
        assertEquals(Short.MAX_VALUE, pcm[0])
        assertEquals((-Short.MAX_VALUE).toShort(), pcm[1])
    }

    @Test
    fun `the middle of the range survives the trip`() {
        val pcm = AndroidFlushAudio.pcm(floatArrayOf(0.5f, -0.25f))
        // Rounded, not truncated: half a bit of bias across a whole take is a quiet
        // DC offset, and rounding is what the conversion actually does.
        assertEquals(16_384.toShort(), pcm[0])      // 0.5 * 32767 = 16383.5
        assertEquals((-8_192).toShort(), pcm[1])    // -0.25 * 32767 = -8191.75
    }

    @Test
    fun `a take keeps its length`() {
        assertEquals(1_000, AndroidFlushAudio.pcm(FloatArray(1_000)).size)
        assertEquals(0, AndroidFlushAudio.pcm(FloatArray(0)).size)
    }
}
