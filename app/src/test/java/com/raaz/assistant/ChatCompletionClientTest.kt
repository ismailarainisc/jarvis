package com.raaz.assistant

import com.raaz.assistant.data.ChatCompletionClient
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Real HTTPS/SSE locally, test-only CA; no live-provider or production TLS bypass. */
class ChatCompletionClientTest {
 private fun fixture(block:suspend CoroutineScope.(MockWebServer,ChatCompletionClient)->Unit)=runBlocking {
  val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
  val serverCert=HandshakeCertificates.Builder().heldCertificate(cert).build()
  val clientCert=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
  val server=MockWebServer();server.useHttps(serverCert.sslSocketFactory(),false);server.start()
  val client=OkHttpClient.Builder().sslSocketFactory(clientCert.sslSocketFactory(),clientCert.trustManager).followRedirects(false).retryOnConnectionFailure(false).build()
  try{block(server,ChatCompletionClient(client))}finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();server.shutdown()}
 }
 @Test fun authenticSseIsDeliveredIncrementally()=fixture{s,c->s.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\ndata: [DONE]\n\n"));val chunks=mutableListOf<String>();val text=c.complete(s.url("/chat").toString(),"fixture","{}",true){chunks.add(it)};assertEquals("Hello world",text);assertEquals(listOf("Hello"," world"),chunks)}
 @Test fun interruptedStreamIsNotSuccessful()=fixture{s,c->s.enqueue(MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"));try{c.complete(s.url("/chat").toString(),"fixture","{}",true){};fail("truncated stream accepted")}catch(e:IllegalStateException){assertTrue(e.message!!.contains("before completion"))}}
 @Test fun cancellationClosesDelayedCallAndAllowsNextRequest()=fixture{s,c->s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE).setBody("{}"));val job=launch(Dispatchers.Default){c.complete(s.url("/chat").toString(),"fixture","{}",false){}};withContext(Dispatchers.IO){assertNotNull(s.takeRequest(5,TimeUnit.SECONDS))};job.cancelAndJoin();s.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"next\"}}]}"));var next:String?=null;repeat(50){if(next==null){try{next=c.complete(s.url("/chat").toString(),"fixture","{}",false){}}catch(e:IllegalStateException){delay(10)}}};assertEquals("next",next)}
 @Test fun errorsDoNotExposeProviderResponseBody()=fixture{s,c->s.enqueue(MockResponse().setResponseCode(401).setBody("secret-provider-body"));try{c.complete(s.url("/chat").toString(),"fixture","{}",false){};fail()}catch(e:IllegalStateException){assertFalse(e.message!!.contains("secret-provider-body"));assertTrue(e.message!!.contains("401"))}}
 @Test fun redirectsAreNotFollowedWithCredentials()=fixture{s,c->s.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://untrusted.example/chat"));try{c.complete(s.url("/chat").toString(),"fixture","{}",false){};fail()}catch(e:IllegalStateException){assertTrue(e.message!!.contains("302"))};assertEquals(1,s.requestCount)}
}
