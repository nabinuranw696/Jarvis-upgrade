package com.cutejarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cutejarvis.app.ui.App
import com.cutejarvis.app.ui.CuteJarvisTheme
import com.cutejarvis.app.voice.VoiceHub

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CuteJarvisTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        VoiceHub.syncWake(this) // Android 14+: the microphone service may only be (re)started while the app is visible
    }

    override fun onStop() {
        if (!isChangingConfigurations) VoiceHub.onAppBackgrounded()
        super.onStop()
    }
}
