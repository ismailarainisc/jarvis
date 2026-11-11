package com.raaz.assistant

import com.raaz.assistant.gemini.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiProtocolTest {
 @Test fun setupUsesGeminiNativeAudioAndVad(){val setup=JSONObject(geminiSetup(DEFAULT_GEMINI_LIVE_MODEL,"Kore")).getJSONObject("setup");assertEquals("models/$DEFAULT_GEMINI_LIVE_MODEL",setup.getString("model"));assertEquals("AUDIO",setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0));assertFalse(setup.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection").getBoolean("disabled"));assertTrue(setup.has("inputAudioTranscription"));assertTrue(setup.has("outputAudioTranscription"))}
 @Test fun modelPrefixIsNotDuplicated(){assertEquals("models/gemini-example-live",geminiModelName("models/gemini-example-live"))}
 @Test(expected=IllegalArgumentException::class) fun malformedModelIsRejected(){geminiModelName("model?key=bad")}
 @Test fun microphoneFrameHasCorrectRate(){val audio=JSONObject(geminiAudioMessage("AAAA")).getJSONObject("realtimeInput").getJSONObject("audio");assertEquals("audio/pcm;rate=16000",audio.getString("mimeType"));assertEquals("AAAA",audio.getString("data"))}
 @Test fun serverReadinessIsActualSetupComplete(){assertTrue(decodeGeminiEvent("{\"setupComplete\":{}}").ready);assertFalse(decodeGeminiEvent("{}").ready);assertFalse(decodeGeminiEvent("{\"setupComplete\":null}").ready)}
 @Test fun multipartAudioAndCaptionsAreAllHandled(){val e=decodeGeminiEvent("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAAA"}},{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"BBBB"}}]},"inputTranscription":{"text":"hello"},"outputTranscription":{"text":"Hi"},"turnComplete":true}}""");assertEquals(2,e.audio.size);assertEquals("hello",e.inputText);assertEquals("Hi",e.outputText);assertTrue(e.turnComplete)}
 @Test fun interruptionAndGoAwayAreProviderSignals(){assertTrue(decodeGeminiEvent("{\"serverContent\":{\"interrupted\":true}}").interrupted);assertTrue(decodeGeminiEvent("{\"goAway\":{}}").goAway)}
 @Test fun backgroundProcessingIsNotFalselyIdle(){val event=decodeGeminiEvent("{\"serverContent\":{\"turnComplete\":true,\"interactionStatus\":\"IN_PROGRESS\"}}");assertTrue(event.processing)}
 @Test(expected=IllegalArgumentException::class) fun unsupportedOutputRateFails(){decodeGeminiEvent("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=16000","data":"AAAA"}}]}}}""")}
 @Test fun thoughtContentIsNeverExposedOrPlayed(){assertTrue(decodeGeminiEvent("""{"serverContent":{"modelTurn":{"parts":[{"thought":true,"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAAA"}}]}}}""").audio.isEmpty())}
 @Test fun directApiKeyIsHeaderOnly(){val auth=geminiSocketAuth("test-fixture-key");assertEquals("x-goog-api-key",auth.header);assertFalse(auth.url.contains("test-fixture-key"));assertFalse(auth.url.contains("?"));assertFalse(auth.toString().contains("test-fixture-key"))}
 @Test fun ephemeralTokenUsesDocumentedConstrainedMethod(){val auth=geminiSocketAuth("auth_tokens/fixture");assertEquals("Authorization",auth.header);assertEquals("Token auth_tokens/fixture",auth.secret);assertTrue(auth.url.endsWith("BidiGenerateContentConstrained"));assertFalse(auth.url.contains("?"))}
 @Test fun mutedInputFlushUsesAudioStreamEnd(){assertTrue(JSONObject(geminiStreamEnd()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd"))}
}
