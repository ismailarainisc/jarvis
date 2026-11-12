package com.raaz.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.raaz.assistant.data.AssistantState
import java.util.Locale
import java.util.UUID
import com.raaz.assistant.gemini.AudioOwnership
import com.raaz.assistant.live.AudioResourceCleanup

/** Foreground-only, turn-based conversation. Recognition service owns VAD and endpointing. */
class VoiceSessionManager(private val context:Context, private val events:Events) {
 interface Events { fun status(state:AssistantState); fun transcript(text:String); fun level(value:Float); fun turn(text:String); fun error(message:String) }
 private var closed=false;private val audioOwner="fallback-${UUID.randomUUID()}";private var focusSequence=0L
 private val handler=Handler(Looper.getMainLooper()); private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private var recognizer:SpeechRecognizer?=null;private var tts:TextToSpeech?=null;private var ttsReady=false;private var ttsInitialized=false;private var waitingForTts=false; private var active=false; private var language="System"; private var generation=0;private val turnGate=RecognitionTurnGate();private var focusRequest:AudioFocusRequest?=null;private var utteranceId:String?=null
 private var singleSpeechWaiting=false
 private val singleSpeech=SpeechReadyGate()
 private var focusListener:AudioManager.OnAudioFocusChangeListener?=null
 init { tts=TextToSpeech(context.applicationContext){ result->handler.post { if(closed)return@post;ttsInitialized=true;ttsReady=result==TextToSpeech.SUCCESS;val pending=singleSpeech.initialized(ttsReady);if(pending!=null)speakOnce(pending);else if(!ttsReady&&singleSpeechWaiting){singleSpeechWaiting=false;events.error("Text-to-speech initialization failed. Text remains available.")};if(waitingForTts){waitingForTts=false;if(ttsReady)beginListening() else finish("Text-to-speech is unavailable. Check your phone's speech settings.")} }} }
 fun start(lang:String){if(active||closed)return;stopOutput();if(!AudioOwnership.process.acquire(audioOwner)){events.error("Another voice operation is active or still closing. Wait for cleanup, or restart if cleanup failed.");return};if(!SpeechRecognizer.isRecognitionAvailable(context)){AudioOwnership.process.release(audioOwner);events.error("Speech recognition is unavailable. Install or enable a speech service.");return}; active=true;language=lang;generation++;events.transcript("");events.status(AssistantState.CONNECTING);if(ttsReady)beginListening() else if(ttsInitialized)finish("Text-to-speech is unavailable. Check your phone’s speech settings.") else{waitingForTts=true;val token=generation;handler.postDelayed({if(active&&token==generation&&waitingForTts)finish("Text-to-speech initialization timed out. Check Android speech settings.")},8000)}}
 private fun beginListening(){if(!active)return;events.status(AssistantState.CONNECTING);events.level(0f);events.transcript("");recognizer?.destroy();val turn=turnGate.begin();val component=com.raaz.assistant.background.externalRecognizer(context)?:run{finish("No external Android speech engine is available. Install or enable one.");return};recognizer=SpeechRecognizer.createSpeechRecognizer(context,component).also{it.setRecognitionListener(listener(generation,turn))};val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_LANGUAGE,when(language){"Urdu"->"ur-PK";"English"->"en-US";else->Locale.getDefault().toLanguageTag()});try{recognizer?.startListening(i)}catch(e:Exception){finish("Could not start speech recognition. Check microphone permission and speech service.")}}
 private fun listener(token:Int,turn:Int)=object:RecognitionListener {
  private fun current()=active&&token==generation&&turnGate.matches(turn)
  override fun onReadyForSpeech(params:Bundle?){if(current())events.status(AssistantState.LISTENING)}
  override fun onBeginningOfSpeech(){if(current())events.status(AssistantState.LISTENING)}
  override fun onRmsChanged(rmsdB:Float){if(current())events.level(((rmsdB+2f)/12f).coerceIn(0f,1f))}
  override fun onBufferReceived(buffer:ByteArray?){}; override fun onEndOfSpeech(){if(current()){events.level(0f);events.status(AssistantState.PROCESSING)}}
  override fun onPartialResults(results:Bundle?){if(current())events.transcript(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())}
  override fun onResults(results:Bundle?){if(!current()||!turnGate.consume(turn))return;val result=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty();recognizer?.destroy();recognizer=null;events.level(0f);if(result.isEmpty()){scheduleRetry(token);return};events.transcript(result);events.status(AssistantState.PROCESSING);events.turn(result)}
  override fun onError(error:Int){if(!current()||!turnGate.consume(turn))return;recognizer?.destroy();recognizer=null;events.level(0f);when(error){SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->scheduleRetry(token);SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->finish("Microphone permission denied. Allow microphone access in Settings.");SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->finish("Speech service lost its network connection. Check the network and tap Start again.");else->finish("Speech recognition stopped (code $error). Check the speech service and tap Start again.")}}
  override fun onEvent(eventType:Int,params:Bundle?){}
 }
 private fun scheduleRetry(token:Int){if(!active)return;events.status(AssistantState.RECONNECTING);handler.postDelayed({if(active&&token==generation)beginListening()},400)}
 fun speak(answer:String){if(!active)return;recognizer?.cancel();recognizer?.destroy();recognizer=null;events.level(0f);if(!requestFocus()){finish("Audio output is unavailable because another app holds audio focus.");return};val id=UUID.randomUUID().toString();utteranceId=id;val engine=tts?:run{finish("Text-to-speech is unavailable.");return};engine.language=when(language){"Urdu"->Locale("ur","PK");"English"->Locale.US;else->Locale.getDefault()};engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(u:String){handler.post{if(active&&u==utteranceId)events.status(AssistantState.SPEAKING)}};override fun onDone(u:String){handler.post{if(active&&u==utteranceId){utteranceId=null;abandonFocus();beginListening()}}};@Deprecated("Deprecated in Java")override fun onError(u:String){handler.post{if(active&&u==utteranceId)finish("Speech output failed. Check your device's text-to-speech settings.")}}});if(engine.speak(answer,TextToSpeech.QUEUE_FLUSH,null,id)==TextToSpeech.ERROR)finish("Speech output failed. Check text-to-speech settings.")}
 private fun requestFocus():Boolean {
  val token=++focusSequence
  val listener=AudioManager.OnAudioFocusChangeListener{change->if(token==focusSequence&&change<0){if(active)finish("Audio focus was interrupted. Start again after the other audio activity.")else{stopOutput();events.status(AssistantState.STOPPED)}}};focusListener=listener
  val result=if(android.os.Build.VERSION.SDK_INT>=26){val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(listener,handler).build();focusRequest=request;audio.requestAudioFocus(request)}else @Suppress("DEPRECATION") audio.requestAudioFocus(listener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
  return result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
 }
 private fun abandonFocus(){focusSequence++;if(Build.VERSION.SDK_INT>=26)focusRequest?.let{audio.abandonAudioFocusRequest(it)}else @Suppress("DEPRECATION") focusListener?.let{audio.abandonAudioFocus(it)};focusRequest=null;focusListener=null}
 fun speakOnce(answer:String){if(active||closed)return
  when(val ready=singleSpeech.submit(answer)){
   SpeechReadyGate.Request.Queued->{singleSpeechWaiting=true;events.status(AssistantState.CONNECTING);handler.postDelayed({if(singleSpeech.expire()){singleSpeechWaiting=false;events.error("Text-to-speech initialization timed out. Text remains available; restart fallback voice to retry.")}},8000);return}
   SpeechReadyGate.Request.Unavailable->{events.error("Speech engine is unavailable or reply exceeds the 16000-character speech limit. Read the text reply.");return}
   is SpeechReadyGate.Request.Play->Unit
  }
  singleSpeechWaiting=false
  if(!ttsReady||tts==null){events.error("Text-to-speech is unavailable. Check Android speech settings.");return};stopOutput();if(!AudioOwnership.process.acquire(audioOwner)){events.error("Another voice operation is active or still closing.");return};if(!requestFocus()){abandonFocus();AudioOwnership.process.release(audioOwner);events.error("Audio output is unavailable.");return};val engine=tts?:return;val id=UUID.randomUUID().toString();utteranceId=id;engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(u:String){handler.post{if(!active&&u==utteranceId)events.status(AssistantState.SPEAKING)}};override fun onDone(u:String){handler.post{if(!active&&u==utteranceId){utteranceId=null;abandonFocus();AudioOwnership.process.release(audioOwner);events.status(AssistantState.IDLE)}}};@Deprecated("Deprecated in Java") override fun onError(u:String){handler.post{if(u==utteranceId){stopOutput();events.error("Speech output failed.")}}}});if(engine.speak(answer,TextToSpeech.QUEUE_FLUSH,null,id)==TextToSpeech.ERROR){stopOutput();events.error("Speech output failed.")}}
 fun stopOutput(){singleSpeech.cancel();singleSpeechWaiting=false;utteranceId=null;val clean=AudioResourceCleanup().close(listOf({tts?.stop();Unit},{abandonFocus()}));if(!clean){AudioOwnership.process.quarantine(audioOwner);events.error("Speech output cleanup failed. Restart the app before recording again.")}else if(!active)AudioOwnership.process.release(audioOwner)}
 fun sessionGeneration()=generation
 fun isSession(value:Int)=active&&!closed&&generation==value
 fun isActive()=active
 fun isBusy()=active||utteranceId!=null||singleSpeech.waiting()
 fun stop(){singleSpeech.cancel();singleSpeechWaiting=false;active=false;waitingForTts=false;generation++;turnGate.cancel();utteranceId=null;handler.removeCallbacksAndMessages(null);val old=recognizer;recognizer=null;val clean=AudioResourceCleanup().close(listOf({old?.cancel();Unit},{old?.destroy();Unit},{tts?.stop();Unit},{abandonFocus()}));if(clean)AudioOwnership.process.release(audioOwner)else AudioOwnership.process.quarantine(audioOwner);events.level(0f);events.transcript("");events.status(AssistantState.STOPPED);if(!clean)events.error("Voice cleanup failed. Restart the app before recording again.")}
 fun finish(message:String){stop();events.error(message)}
 fun close(){singleSpeech.close();closed=true;ttsReady=false;stop();tts?.shutdown();tts=null}
}
