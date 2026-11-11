package com.raaz.assistant.background

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.raaz.assistant.MainActivity

/** Assistant role invokes visible JARVIS. It does not imply hardware hotword or lock bypass. */
class JarvisVoiceInteractionService:VoiceInteractionService()
class JarvisVoiceSessionService:VoiceInteractionSessionService(){override fun onNewSession(args:Bundle?)=JarvisVoiceSession(this)}
class JarvisVoiceSession(context:android.content.Context):VoiceInteractionSession(context){
 override fun onShow(args:Bundle?,showFlags:Int){super.onShow(args,showFlags);try{val intent=Intent(context,MainActivity::class.java).putExtra("assistant_hub","Tools");if(android.os.Build.VERSION.SDK_INT>=26)startAssistantActivity(intent)else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));hide()}catch(e:Exception){showWindow()} }
 private fun showWindow(){setContentView(android.widget.TextView(context).apply{setText(com.raaz.assistant.R.string.assistant_launch_failure);setPadding(32,32,32,32)})}
}
