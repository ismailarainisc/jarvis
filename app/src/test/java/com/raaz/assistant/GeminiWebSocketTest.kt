package com.raaz.assistant

import com.raaz.assistant.gemini.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real OkHttp socket transport against local fixtures; these are NOT Google connectivity tests. */
class GeminiWebSocketTest {
 private class Listener:GeminiConnection.Listener {
  val events=LinkedBlockingQueue<String>();val failures=LinkedBlockingQueue<GeminiFailure>()
  override fun opened(){events.add("open")}
  override fun message(text:String){events.add(text)}
  override fun failed(failure:GeminiFailure){failures.add(failure)}
  override fun closed(code:Int){events.add("closed:$code")}
 }
 @Test fun actualSocketSendsSetupAndReceivesTextAndBinaryEvents(){val server=MockWebServer();val received=LinkedBlockingQueue<String>();server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onOpen(webSocket:WebSocket,response:Response){webSocket.send("{\"setupComplete\":{}}");webSocket.send("{\"serverContent\":{\"turnComplete\":true}}".encodeUtf8())};override fun onMessage(webSocket:WebSocket,text:String){received.add(text)}}));server.start();val factory=OkHttpGeminiConnections(testEndpoint={server.url("/live").toString()});val listener=Listener();val socket=factory.create(listener)
  try{socket.open(geminiSocketAuth("fixture-only"));assertEquals("open",listener.events.poll(5,TimeUnit.SECONDS));val setup=geminiSetup(DEFAULT_GEMINI_LIVE_MODEL,"Kore");assertTrue(socket.send(setup));assertEquals(setup,received.poll(5,TimeUnit.SECONDS));assertTrue(decodeGeminiEvent(listener.events.poll(5,TimeUnit.SECONDS)!!).ready);assertTrue(decodeGeminiEvent(listener.events.poll(5,TimeUnit.SECONDS)!!).turnComplete);val request=server.takeRequest(5,TimeUnit.SECONDS)!!;assertEquals("fixture-only",request.getHeader("x-goog-api-key"));assertFalse(request.path!!.contains("fixture-only"));socket.cancel();assertFalse(socket.send("obsolete"))}finally{socket.cancel();factory.close();server.shutdown()}}
 @Test fun authenticationFailureIsSanitized(){val server=MockWebServer();server.enqueue(MockResponse().setResponseCode(401).setBody("sensitive-provider-body"));server.start();val factory=OkHttpGeminiConnections(testEndpoint={server.url("/live").toString()});val listener=Listener();val socket=factory.create(listener);try{socket.open(geminiSocketAuth("fixture-only"));val failure=listener.failures.poll(5,TimeUnit.SECONDS)!!;assertEquals(GeminiErrorCode.AUTHENTICATION,failure.code);assertFalse(failure.message!!.contains("sensitive-provider-body"));assertFalse(failure.message!!.contains("fixture-only"))}finally{socket.cancel();factory.close();server.shutdown()}}
 @Test fun arbitraryDestinationsAreRejectedBeforeAnyNetworkCall(){val f=OkHttpGeminiConnections();val c=f.create(Listener());try{assertThrows(IllegalArgumentException::class.java){c.open(GeminiSocketAuth("wss://untrusted.example","x-goog-api-key","fixture-only"))}}finally{c.cancel();f.close()}}
 @Test fun cancelBeforeOpenCannotActivateTransport(){val server=MockWebServer();server.start();val f=OkHttpGeminiConnections(testEndpoint={server.url("/live").toString()});val c=f.create(Listener());try{c.cancel();c.open(geminiSocketAuth("fixture-only"));assertFalse(c.send("obsolete"));assertEquals(0,server.requestCount)}finally{f.close();server.shutdown()}}
}
