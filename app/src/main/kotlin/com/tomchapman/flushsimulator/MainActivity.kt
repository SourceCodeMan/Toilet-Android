package com.tomchapman.flushsimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tomchapman.flushsimulator.device.AndroidFlushAudio
import com.tomchapman.flushsimulator.device.AndroidHaptics
import com.tomchapman.flushsimulator.ui.FlushScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AndroidSettings(this)
        val audio = AndroidFlushAudio(settings)
        val haptics = AndroidHaptics(this)
        setContent { FlushScreen(settings, audio = audio, haptics = haptics) }
    }
}
