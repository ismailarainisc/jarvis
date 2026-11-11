package com.raaz.assistant.gemini

import okio.ByteString.Companion.toByteString

/** A frame's ticket is taken BEFORE a blocking read. Mute/unmute/Stop invalidate old tickets. */
class MicrophoneGate(private val connection:GeminiConnection) {
 enum class Result { SENT, DROPPED, CLOSED }
 private var open=true;private var muted=false;private var generation=0L
 private var total=0L
 @Synchronized fun ticket():Long?=if(open&&!muted)generation else null
 @Synchronized fun send(ticket:Long,pcm:ByteArray):Result {
  if(!open)return Result.CLOSED
  if(muted||ticket!=generation)return Result.DROPPED
  if(pcm.isEmpty()||pcm.size%2!=0||pcm.size>640)throw GeminiFailure(GeminiErrorCode.CAPTURE,"Microphone returned an invalid PCM16 frame.")
  if(connection.queuedBytes()>64_000)throw GeminiFailure(GeminiErrorCode.BACKPRESSURE,"The network is too slow for live microphone audio. The session was stopped instead of accumulating input.")
  if(!connection.send(geminiAudioMessage(pcm.toByteString().base64())))throw GeminiFailure(GeminiErrorCode.NETWORK,"Microphone transmission failed. Check the network and restart manually.")
  total+=pcm.size;return Result.SENT
 }
 @Synchronized fun setMuted(value:Boolean){
  if(!open||muted==value)return
  // Already-enqueued network packets cannot be recalled. Stop on a large backlog instead of pretending to mute it.
  if(value&&connection.queuedBytes()>2048)throw GeminiFailure(GeminiErrorCode.BACKPRESSURE,"Input was buffered on the network. The session was stopped to prevent delayed microphone transmission.")
  generation++;muted=value
  if(value&&!connection.send(geminiStreamEnd()))throw GeminiFailure(GeminiErrorCode.NETWORK,"Could not pause Gemini input safely. The session was stopped.")
 }
 @Synchronized fun stop(){open=false;muted=true;generation++}
 @Synchronized fun bytesQueued()=total
}
