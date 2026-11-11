@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.raaz.assistant.mcp

import com.raaz.assistant.features.publicHttps
import com.raaz.assistant.data.SseEvents
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class McpException(message:String):Exception(message)
data class McpServer(val id:String,val name:String,val endpoint:String,val enabled:Boolean=false,val allowed:Set<String> = emptySet())
data class McpTool(val name:String,val description:String,val schema:JSONObject)
data class McpResult(val text:String,val isError:Boolean,val cleanupConfirmed:Boolean)

/** Streamable HTTP JSON/SSE. No redirects, automatic retries, server sampling, roots or elicitation. */
class McpClient internal constructor(private val http:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build(),private val testEndpoint:((String)->String)?=null) {
 private val sequence=AtomicLong()
 private suspend fun await(request:Request):Response=suspendCancellableCoroutine{c->val call=http.newCall(request);c.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWith(Result.failure(McpException("MCP network or TLS request failed. Check the server and restart manually.")))};override fun onResponse(call:Call,response:Response){c.resume(response){response.close()}}})}
 private inner class Session(val server:McpServer,val credential:String) {
  var id:String?=null;var version:String?=null
  fun request(method:String,body:JSONObject?=null):Request {
   require(publicHttps(server.endpoint)){"MCP endpoint must be HTTPS on port 443 without query, fragment or user information."}
   val b=Request.Builder().url(testEndpoint?.invoke(server.endpoint)?:server.endpoint).header("Accept","application/json, text/event-stream")
   if(credential.isNotEmpty()){require(credential.length<=8192&&credential.all{it.code in 33..126}){"Invalid MCP credential."};b.header("Authorization","Bearer $credential")}
   id?.let{b.header("MCP-Session-Id",it)};version?.let{b.header("MCP-Protocol-Version",it)}
   return if(method=="DELETE")b.delete().build()else b.post(body.toString().toRequestBody("application/json".toMediaType())).build()
  }
  suspend fun rpc(method:String,params:JSONObject=JSONObject(),notification:Boolean=false):JSONObject=withContext(Dispatchers.IO){
   val expected=sequence.incrementAndGet();val message=JSONObject().put("jsonrpc","2.0").put("method",method).put("params",params);if(!notification)message.put("id",expected)
   await(request("POST",message)).use{r->
    if(!r.isSuccessful)throw McpException(when(r.code){401,403->"MCP authentication or authorization rejected. Configure a valid token.";404->"MCP endpoint or session not found. Retry manually; no action was replayed.";429->"MCP server rate limit reached. Retry manually later.";else->"MCP server returned HTTP ${r.code}."})
    val responseSession=r.header("MCP-Session-Id")
    if(responseSession!=null){if(responseSession.length !in 1..256||!responseSession.all{it.code in 33..126}||(id!=null&&id!=responseSession))throw McpException("MCP returned an invalid or changed session identifier.");id=responseSession}
    if(notification){if(r.code!=202&&r.body?.contentLength()!=0L)throw McpException("MCP did not acknowledge the notification correctly.");return@withContext JSONObject()}
    val source=r.body?.source()?:throw McpException("MCP returned no JSON-RPC response.")
    val contentType=r.header("Content-Type","")!!.lowercase()
    fun response(j:JSONObject):JSONObject? {
     if(j.optString("jsonrpc")!="2.0")throw McpException("Invalid MCP JSON-RPC version.")
     if(j.has("method")){if(j.has("id"))throw McpException("This MCP server requires unsupported client requests. No action was authorized.");return null}
     if(j.opt("id") !is Number||j.getLong("id")!=expected)throw McpException("MCP response ID did not match the active request.")
     if(j.has("error"))throw McpException("MCP reported a JSON-RPC error. Check tool compatibility and authorization.")
     return j.optJSONObject("result")?:throw McpException("MCP response has no valid result object.")
    }
    try{
     if(contentType.startsWith("application/json")){
      if(source.request(1_048_577))throw McpException("MCP response exceeded 1 MiB.")
      return@withContext response(JSONObject(source.readUtf8()))?:throw McpException("MCP returned only a notification.")
     }
     if(!contentType.startsWith("text/event-stream"))throw McpException("MCP requires JSON or SSE responses.")
     val events=SseEvents();var total=0L
     while(!source.exhausted()){ensureActive();val line=source.readUtf8LineStrict(65536);total+=line.length+1;if(total>1_048_576)throw McpException("MCP SSE response exceeded 1 MiB.");val event=events.line(line)?:continue;response(JSONObject(event))?.let{return@withContext it}}
     throw McpException("MCP stream ended without the matching response.")
    }catch(e:CancellationException){throw e}catch(e:McpException){throw e}catch(e:Exception){throw McpException("MCP returned malformed or unsupported data.")}
   }
  }
  suspend fun initialize(){val result=rpc("initialize",JSONObject().put("protocolVersion","2025-11-25").put("capabilities",JSONObject()).put("clientInfo",JSONObject().put("name","JarvisAndroid").put("version","6.0")));val negotiated=result.optString("protocolVersion");if(negotiated !in setOf("2025-11-25","2025-06-18"))throw McpException("Unsupported MCP protocol version.");if(result.optJSONObject("capabilities")?.optJSONObject("tools")==null)throw McpException("This server does not advertise MCP tools.");version=negotiated;rpc("notifications/initialized",notification=true)}
  suspend fun tools():List<McpTool>{val result=mutableListOf<McpTool>();var cursor:String?=null;repeat(5){val params=JSONObject();cursor?.let{params.put("cursor",it)};val page=rpc("tools/list",params);val tools=page.optJSONArray("tools")?:throw McpException("MCP tools/list returned no tools array.");if(tools.length()>100||result.size+tools.length()>100)throw McpException("MCP tool count exceeds 100.");for(i in 0 until tools.length()){val t=tools.getJSONObject(i);val name=t.getString("name");if(!name.matches(Regex("[A-Za-z0-9_.-]{1,128}"))||result.any{it.name==name})throw McpException("Invalid or duplicate MCP tool name.");result.add(McpTool(name,t.optString("description").take(2000),t.optJSONObject("inputSchema")?:throw McpException("Tool schema is missing.")))};cursor=page.optString("nextCursor").takeIf{it.isNotBlank()};if(cursor==null)return result;if(cursor!!.length>2048)throw McpException("MCP pagination cursor is oversized.")};throw McpException("MCP pagination exceeded five pages.")}
  suspend fun close():Boolean {if(id==null)return true;return withContext(NonCancellable+Dispatchers.IO){withTimeoutOrNull(3000){try{await(request("DELETE")).use{it.isSuccessful}}catch(e:Exception){false}}?:false}}
 }
 suspend fun discover(server:McpServer,token:String):List<McpTool>{val s=Session(server,token);try{s.initialize();return s.tools()}finally{s.close()}}
 suspend fun invoke(server:McpServer,token:String,tool:String,args:JSONObject,authorized:()->Boolean={true}):McpResult {
  if(!server.enabled||tool !in server.allowed||!authorized())throw McpException("Enable this server and explicitly allow this tool before calling it.")
  val s=Session(server,token);var result:JSONObject
  try{s.initialize();val definition=s.tools().find{it.name==tool}?:throw McpException("The selected tool is no longer advertised by this server.");validateSchema(definition.schema,args)?.let{throw McpException(it)};if(!authorized())throw McpException("MCP authorization changed before execution. No tool call was issued.");result=s.rpc("tools/call",JSONObject().put("name",tool).put("arguments",args))}catch(e:Exception){s.close();throw e}
  val clean=s.close();val content=result.optJSONArray("content");if(content==null&&result.optJSONObject("structuredContent")==null)throw McpException("MCP returned an invalid tool result.");if(result.has("isError")&&result.opt("isError") !is Boolean)throw McpException("MCP returned an invalid error flag.");val text=buildString{if(content!=null)for(i in 0 until content.length()){val part=content.getJSONObject(i);if(part.optString("type")=="text")append(part.optString("text").take(8000)).append('\n')else append("[Non-text tool result available; not rendered]\n")};result.optJSONObject("structuredContent")?.let{append(it.toString().take(8000))}}.take(8000)
  return McpResult(text.ifBlank{"Tool returned no text content."},result.optBoolean("isError"),clean)
 }
 fun close(){http.dispatcher.cancelAll();http.connectionPool.evictAll();http.dispatcher.executorService.shutdown()}
}
/** Conservative validation: unsupported complex schemas are not silently claimed to be validated. */
fun validateSchema(schema:JSONObject,args:JSONObject):String? {
 fun validate(s:JSONObject,value:Any?,depth:Int):Boolean {
  if(depth>6)return false
  val supported=setOf("type","properties","required","additionalProperties","items","minItems","maxItems","minLength","maxLength","minimum","maximum","enum","description","title","default","$"+"schema","$"+"id")
  if(s.keys().asSequence().any{it !in supported})return false
  if(listOf("$"+"ref","oneOf","anyOf","allOf","not","patternProperties").any{s.has(it)})return false
  val type=s.optString("type")
  val valid=when(type){"object"->value is JSONObject;"string"->value is String;"integer"->value is Number&&value.toDouble()%1.0==0.0;"number"->value is Number;"boolean"->value is Boolean;"array"->value is org.json.JSONArray;"null"->value===JSONObject.NULL;else->false}
  if(!valid)return false
  s.optJSONArray("enum")?.let{allowed->if((0 until allowed.length()).none{allowed.opt(it)==value})return false}
  if(value is String&&(value.length<s.optInt("minLength",0)||value.length>s.optInt("maxLength",16000)))return false
  if(value is Number&&(value.toDouble()<s.optDouble("minimum",-Double.MAX_VALUE)||value.toDouble()>s.optDouble("maximum",Double.MAX_VALUE)))return false
  if(value is JSONObject){val properties=s.optJSONObject("properties")?:JSONObject();val required=s.optJSONArray("required");if(required!=null)for(i in 0 until required.length())if(!value.has(required.getString(i)))return false;for(key in value.keys()){val child=properties.optJSONObject(key);if(child==null){if(s.opt("additionalProperties")==false)return false;val extra=s.optJSONObject("additionalProperties");if(extra!=null&&!validate(extra,value.opt(key),depth+1))return false}else if(!validate(child,value.opt(key),depth+1))return false}}
  if(value is org.json.JSONArray){if(value.length()>s.optInt("maxItems",100)||value.length()<s.optInt("minItems",0))return false;val item=s.optJSONObject("items")?:return false;for(i in 0 until value.length())if(!validate(item,value.opt(i),depth+1))return false}
  return true
 }
 return try{if(args.toString().length<=16000&&validate(schema,args,0))null else "Tool arguments or schema are invalid or unsupported; review the server schema."}catch(e:Exception){"Invalid MCP schema."}
}
