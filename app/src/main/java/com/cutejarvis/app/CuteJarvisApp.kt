package com.cutejarvis.app

import android.app.Application
import com.cutejarvis.app.voice.VoiceHub

/** No account or third-party auth SDK is initialised: the app works as a guest on this device. */
class CuteJarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VoiceHub.init(this) // only stores settings; the TTS/recognizer are created lazily on first voice use
    }
}
