package com.raaz.assistant

import com.raaz.assistant.live.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveAudioTest {
 @Test fun pcmMeasuresSilenceAndSignedFullScale(){assertEquals(0f,pcm16Rms(ByteArray(960)),0f);assertEquals(1f,pcm16Rms(byteArrayOf(0,-128)),.0001f);assertEquals(.5f,pcm16Rms(byteArrayOf(0,64)),.0001f)}
 @Test fun oddTrailingBytesAreIgnored(){assertEquals(.5f,pcm16Rms(byteArrayOf(0,64,127)),.0001f);assertEquals(0f,pcm16Rms(byteArrayOf(12)),0f)}
 @Test fun meterPublishesAtMostTenTimesPerSecond(){val m=PcmLevelMeter();assertNotNull(m.sample(byteArrayOf(0,64),0));assertNull(m.sample(byteArrayOf(0,64),50));assertNotNull(m.sample(byteArrayOf(0,64),100));assertEquals(0f,m.sample(ByteArray(8),200)!!.display,0f)}
 @Test fun activityRequiresMeasuredEnergyAndSilenceReleases(){val d=AudioActivityDetector();assertFalse(d.update(.001f,0));assertTrue(d.update(.03f,100));assertTrue(d.update(0f,200));assertFalse(d.update(0f,400));d.reset();assertFalse(d.update(0f,500))}
 @Test fun muteWinsButInputAndOutputRemainIndependent(){assertEquals(LiveVoiceActivity.MUTED,voiceActivity(LiveSessionState.SESSION_READY,true,true,false,true));assertEquals(LiveVoiceActivity.USER_SPEAKING,voiceActivity(LiveSessionState.SESSION_READY,false,true,true,true))}
 @Test fun rendererRunningAloneDoesNotClaimSpeech(){assertEquals(LiveVoiceActivity.LISTENING,voiceActivity(LiveSessionState.SESSION_READY,false,true,false,false))}
 @Test fun noListeningBeforeSessionReadiness(){assertEquals(LiveVoiceActivity.CONNECTING,voiceActivity(LiveSessionState.CONNECTED,false,true,true,true))}
 @Test fun stopAndErrorsOverrideMeasuredActivity(){assertEquals(LiveVoiceActivity.DISCONNECTING,voiceActivity(LiveSessionState.DISCONNECTING,false,true,true,true));assertEquals(LiveVoiceActivity.ERROR,voiceActivity(LiveSessionState.ERROR,false,true,true,true));assertEquals(LiveVoiceActivity.STOPPED,voiceActivity(LiveSessionState.DISCONNECTED,false,true,true,true))}
 @Test fun cleanupIsIdempotentAndContinuesAfterFailure(){val cleanup=AudioResourceCleanup();val steps=mutableListOf<Int>();val actions=listOf<()->Unit>({steps.add(1)},{throw IllegalStateException("isolated failure")},{steps.add(3)});assertFalse(cleanup.close(actions));assertFalse(cleanup.close(actions));assertEquals(listOf(1,3),steps)}
 @Test fun cleanupSupportsIndependentConsecutiveSessions(){var releases=0;repeat(3){val c=AudioResourceCleanup();assertTrue(c.close(listOf({releases++})));assertTrue(c.close(listOf({releases++})))};assertEquals(3,releases)}
 @Test fun onlySelectedRemovedRouteIsReportedLost(){assertTrue(selectedRouteWasRemoved(7,listOf(7,8)));assertFalse(selectedRouteWasRemoved(7,listOf(8)));assertFalse(selectedRouteWasRemoved(null,listOf(7)))}
 @Test fun captionsPreserveTextAndTimingWithoutInventingTurns(){val p=LiveTranscriptProcessor();val input=JSONObject().put("type","session.input_transcript.delta").put("event_id","one").put("delta","Hello ").put("start_ms",100).put("end_ms",300);val f=p.decode(input)!!;assertEquals("Hello ",f.delta);assertEquals(100L,f.startMs);assertNull(p.decode(input));assertEquals("Hello ",p.decode(JSONObject(input.toString()).put("event_id","two"))!!.delta)}
 @Test fun captionProcessorIgnoresLegacyAndUnknownEvents(){val p=LiveTranscriptProcessor();assertNull(p.decode(JSONObject().put("type","input_audio_buffer.speech_started")));assertNull(p.decode(JSONObject().put("type","future.event")))}
 @Test fun malformedCaptionIntervalsAreNotDisplayed(){assertNull(LiveTranscriptProcessor().decode(JSONObject().put("type","session.output_transcript.delta").put("delta","bad").put("start_ms",20).put("end_ms",10)))}
 @Test fun transcriptClearAllowsANewSession(){val p=LiveTranscriptProcessor();val e=JSONObject().put("type","session.output_transcript.delta").put("event_id","id").put("delta","Hi").put("start_ms",0).put("end_ms",10);assertNotNull(p.decode(e));assertNull(p.decode(e));p.clear();assertNotNull(p.decode(e))}
}
