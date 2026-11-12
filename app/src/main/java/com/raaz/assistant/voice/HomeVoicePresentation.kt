package com.raaz.assistant.voice

import com.raaz.assistant.data.AssistantState
import com.raaz.assistant.gemini.*

enum class UnifiedVoicePhase { IDLE, CONNECTING, SETTING_UP, READY, LISTENING, USER_SPEAKING, PROCESSING, ASSISTANT_SPEAKING, INTERRUPTED, MUTED, RECONNECTING, DISCONNECTING, ERROR, STOPPED }
data class VoicePresentation(val phase:UnifiedVoicePhase,val visual:AssistantState,val active:Boolean,val summary:String)
fun nativePresentation(native:GeminiLiveUi)=VoicePresentation(UnifiedVoicePhase.valueOf(native.phase.name),native.phase.visualState(),native.active,when(native.phase){
 GeminiPhase.IDLE->"Ready when you are"
 GeminiPhase.CONNECTING->"Connecting securely to Google"
 GeminiPhase.SETTING_UP->"Waiting for Gemini setup confirmation"
 GeminiPhase.READY->"Session ready; preparing device audio"
 GeminiPhase.LISTENING->"Listening · native Gemini audio"
 GeminiPhase.USER_SPEAKING->"Microphone audio activity"
 GeminiPhase.PROCESSING->"Provider reported processing"
 GeminiPhase.ASSISTANT_SPEAKING->"Gemini output audio activity"
 GeminiPhase.INTERRUPTED->"Provider interrupted the previous output"
 GeminiPhase.MUTED->"Microphone transmission muted"
 GeminiPhase.DISCONNECTING->"Releasing microphone, playback and connection"
 GeminiPhase.ERROR->"Voice needs attention"
 GeminiPhase.STOPPED->"Conversation stopped"
})
fun fallbackPresentation(state:AssistantState,active:Boolean):VoicePresentation {
 val phase=when(state){
  AssistantState.CONNECTING->UnifiedVoicePhase.CONNECTING
  AssistantState.LISTENING->UnifiedVoicePhase.LISTENING
  AssistantState.PROCESSING,AssistantState.THINKING->UnifiedVoicePhase.PROCESSING
  AssistantState.SPEAKING->UnifiedVoicePhase.ASSISTANT_SPEAKING
  AssistantState.INTERRUPTED->UnifiedVoicePhase.INTERRUPTED
  AssistantState.RECONNECTING->UnifiedVoicePhase.RECONNECTING
  AssistantState.ERROR->UnifiedVoicePhase.ERROR
  AssistantState.STOPPED->UnifiedVoicePhase.STOPPED
  else->UnifiedVoicePhase.IDLE
 }
 return VoicePresentation(phase,state,active||state==AssistantState.SPEAKING,if(active)"Android recognition + device TTS · turn-based" else  "Secondary mode · device voice, no barge-in")
}
