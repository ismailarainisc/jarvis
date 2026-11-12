package com.raaz.assistant.voice

import com.raaz.assistant.gemini.GeminiLiveController

/** Shared Home/settings adapter delegates to the existing engine, never another audio implementation. */
class GeminiHomeAdapter(private val controller:GeminiLiveController):HomeNativeVoice {
 override val state=controller.state
 override fun configured()=controller.configuration.value.configured
 override fun setForeground(value:Boolean)=controller.setForeground(value)
 override fun setVisible(value:Boolean)=controller.setScreenVisible(value)
 override fun start()=controller.start()
 override fun stop()=controller.stop()
 override suspend fun stopAndAwait()=controller.stopAndAwait()
 override fun mute()=controller.mute()
 override fun permissionDenied()=controller.permissionDenied()
}
