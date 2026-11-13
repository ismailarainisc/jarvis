package com.raaz.assistant.features

import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID

enum class TaskStatus { PROPOSED, VERIFIED, UNKNOWN_OUTCOME, NEEDS_APPROVAL, EXECUTING, LAUNCHED, COMPLETED, CANCELLED, FAILED, UNSUPPORTED, NEEDS_PERMISSION }
data class ToolAction(val name:String,val args:JSONObject) {
 override fun toString()="ToolAction($name, arguments=PRIVATE)"
 fun encode()=JSONObject().put("name",name).put("args",args).toString()
 companion object {fun decode(raw:String):ToolAction{require(raw.length<=32768);val j=JSONObject(raw);return ToolAction(j.getString("name"),j.getJSONObject("args"))}}
}
data class AgentTask(val id:String=UUID.randomUUID().toString(),val action:ToolAction,val source:String,val status:TaskStatus=TaskStatus.NEEDS_APPROVAL,val detail:String="Review the exact action before approval.",val created:Long=System.currentTimeMillis())
data class ToolOutcome(val status:TaskStatus,val detail:String)
data class MemoryItem(val id:String,val kind:String,val label:String,val value:String,val contactLookup:String="",val provenance:String="User saved",val updated:Long=System.currentTimeMillis())
data class DeviceContact(val label:String,val phone:String,val lookup:String)
data class InstalledApp(val label:String,val packageName:String)
fun normalizeAlias(value:String)=value.trim().lowercase(Locale.ROOT)
fun validPhone(value:String)=value.matches(Regex("\\+?[0-9][0-9 ()-]{4,25}"))
fun whatsappNumber(value:String):String {val n=value.replace(Regex("[ ()-]"),"");require(n.matches(Regex("\\+[1-9][0-9]{6,14}"))){"WhatsApp requires an international number beginning with + and country code."};return n.drop(1)}
fun publicHttps(value:String):Boolean=try{val u=URI(value);u.scheme.equals("https",true)&&!u.host.isNullOrBlank()&&u.userInfo==null&&u.rawQuery==null&&u.fragment==null&&u.port in listOf(-1,443)&&!u.host.equals("localhost",true)}catch(e:Exception){false}
fun validBrowserUrl(value:String):Boolean=try{val u=URI(value);u.scheme.equals("https",true)&&!u.host.isNullOrBlank()&&u.userInfo==null&&u.port in listOf(-1,443)&&value.length<=4096&&value.none{it.code<32||it.code==127}}catch(e:Exception){false}
fun safeMemory(label:String,value:String):Boolean {
 val all="$label $value"
 return label.isNotBlank()&&label.length<=120&&value.isNotBlank()&&value.length<=2000&&!Regex("(?i)(api[ _-]?key|password|secret|bearer |auth_tokens/|AIza[\\w-]{20}|sk-(?:proj-)?[\\w-]{20})").containsMatchIn(all)
}
object ToolRegistry {
 val names=setOf("search_apps","find_contacts","device_status","maps_search","navigate_maps","read_memory","delete_memory","open_app","youtube_search","youtube_open","web_search","open_url","open_settings","set_alarm","set_timer","set_clock_timer","timer_status","cancel_timer","stop_timer","dial","direct_call","sms_compose","whatsapp_compose","save_memory","mcp_call","scroll_app","back_app")
 fun validate(action:ToolAction):String?=try{
  require(action.name in names){"Unsupported tool."};val a=action.args;require(a.toString().length<=16000){"Arguments are too large."}
  fun text(key:String,max:Int=500):String{val v=a.opt(key) as? String?:error("Missing $key.");require(v.isNotBlank()&&v.length<=max){"Invalid $key."};return v}
  fun integer(key:String,min:Int,max:Int){
   val value=a.opt(key) as? Number?:error("Missing numeric $key.")
   val exact=try{java.math.BigDecimal(value.toString()).longValueExact()}catch(e:Exception){error("Enter a whole $key within $min..$max.")}
   require(exact in min.toLong()..max.toLong()){"$key must be within $min..$max."}
  }
  when(action.name){
   "search_apps","find_contacts","maps_search","navigate_maps"->text("query",200)
   "device_status","timer_status","cancel_timer","stop_timer"->require(a.length()==0){"Device status accepts no arguments."}
   "read_memory"->text("query",120)
   "delete_memory"->require(text("id",100).matches(Regex("[A-Za-z0-9-]{1,100}"))){"Invalid memory ID."}
   "open_app"->text("app",150)
   "youtube_search","web_search"->text("query")
   "youtube_open"->{val uri=URI(text("url",4096));require(validBrowserUrl(uri.toString())&&uri.host.lowercase(Locale.ROOT) in setOf("youtube.com","www.youtube.com","m.youtube.com","youtu.be")){"Enter a valid HTTPS YouTube link."}}
   "open_url"->require(validBrowserUrl(text("url",4096))){"Enter a valid HTTPS URL without embedded credentials."}
   "open_settings"->require(text("page") in setOf("app","wifi","bluetooth","accessibility","assistant","network","sound","location","notifications","battery")){"Unsupported settings page."}
   "set_alarm"->{integer("hour",0,23);integer("minute",0,59)}
   "set_timer","set_clock_timer"->integer("seconds",1,86400)
   "dial","direct_call","sms_compose","whatsapp_compose"->{text("recipient",150);if(a.has("phone"))require(validPhone(text("phone",30))){"Invalid phone number."};if(action.name.endsWith("compose"))text("message",2000)}
   "save_memory"->require(safeMemory(text("label",120),text("value",2000))){"Memory must be nonempty and cannot contain credentials."}
   "mcp_call"->{text("server",100);text("tool",128);require(a.opt("arguments") is JSONObject){"MCP arguments must be a JSON object."}}
   "scroll_app","back_app"->{require(text("package") in setOf("com.google.android.youtube","com.android.chrome")){"Only explicitly enabled YouTube/Chrome scrolling is supported."};if(action.name=="scroll_app")require(text("direction") in setOf("forward","backward"))}
  };null
 }catch(e:Exception){e.message?.takeIf{it.length<200}?:"Invalid tool arguments."}
 fun validateProvider(action:ToolAction):String? {
  val declarations=declarations();val schema=(0 until declarations.length()).map{declarations.getJSONObject(it)}.find{it.getString("name")==action.name}?:return "Tool is not available to the provider."
  val allowed=schema.getJSONObject("parameters").getJSONObject("properties")
  if(action.args.keys().asSequence().any{!allowed.has(it)})return "Unexpected or device-internal argument. Prepare the action on device instead."
  return validate(action)
 }
 fun declarations():org.json.JSONArray {
  val definitions=listOf(
   Triple("search_apps","Propose a local installed launcher-app search. Results stay on device.",mapOf("query" to "string")),
   Triple("find_contacts","Propose an approved local contact lookup. Requires optional Contacts access; private results stay on device.",mapOf("query" to "string")),
   Triple("device_status","Propose a battery/network snapshot. Requires device approval.",emptyMap<String,String>()),
   Triple("maps_search","Propose a maps search through an Android geo intent with HTTPS fallback.",mapOf("query" to "string")),
   Triple("navigate_maps","Propose directions to a destination. Launch does not prove navigation began.",mapOf("query" to "string")),
   Triple("read_memory","Propose a local memory search. Results stay on device.",mapOf("query" to "string")),
   Triple("delete_memory","Propose deleting one exact local memory ID after explicit device approval.",mapOf("id" to "string")),
   Triple("open_app","Propose opening an installed app by name.",mapOf("app" to "string")),
   Triple("youtube_search","Propose a YouTube search. Launching does not prove video playback.",mapOf("query" to "string")),
   Triple("web_search","Propose a browser search.",mapOf("query" to "string")),
   Triple("youtube_open","Propose opening a valid HTTPS YouTube link.",mapOf("url" to "string")),
   Triple("open_settings","Propose Android settings. page is app, wifi, bluetooth, accessibility, assistant, network, sound, location, notifications or battery.",mapOf("page" to "string")),
   Triple("scroll_app","Propose one scroll in explicitly enabled com.google.android.youtube or com.android.chrome. direction is forward or backward. Device approval and optional accessibility are required.",mapOf("package" to "string","direction" to "string")),
   Triple("back_app","Propose one Back action in explicitly enabled com.google.android.youtube or com.android.chrome. Device approval is required.",mapOf("package" to "string")),
   Triple("open_url","Propose opening an HTTPS link.",mapOf("url" to "string")),
   Triple("dial","Propose dialer composition for a contact alias, phone or reserved recipient 'selected contact'. No call is placed until the user acts.",mapOf("recipient" to "string")),
   Triple("sms_compose","Propose an SMS composer; recipient may be 'selected contact' (resolved only on device). Does not send automatically.",mapOf("recipient" to "string","message" to "string")),
   Triple("whatsapp_compose","Propose WhatsApp composition (recipient may be 'selected contact'), not sending or calling.",mapOf("recipient" to "string","message" to "string")),
   Triple("set_timer","Start a real JARVIS timer directly. Convert minutes to seconds (five minutes = 300). Urdu/Hindi/English durations supported via numeric seconds. One active timer; never claim success until the result.",mapOf("seconds" to "integer")),
   Triple("timer_status","Read the remaining seconds of the JARVIS timer.",emptyMap<String,String>()),
   Triple("cancel_timer","Cancel the JARVIS timer directly when the user asks.",emptyMap<String,String>()),
   Triple("stop_timer","Stop the running or finished JARVIS timer and dismiss its alert.",emptyMap<String,String>()),
   Triple("set_clock_timer","Request a timer in external Clock; launch is not verified creation.",mapOf("seconds" to "integer")),
   Triple("set_alarm","Propose an Android alarm.",mapOf("hour" to "integer","minute" to "integer")),
   Triple("save_memory","Propose a non-secret memory. Explicit device approval is required.",mapOf("label" to "string","value" to "string")),
   Triple("mcp_call","Propose a tool on a user-configured MCP server. Always requires device review.",mapOf("server" to "string","tool" to "string","arguments" to "object"))
  )
  return org.json.JSONArray().also{out->definitions.forEach{(name,description,fields)->val properties=JSONObject();fields.forEach{(k,t)->properties.put(k,JSONObject().put("type",t))};out.put(JSONObject().put("name",name).put("description",description).put("parameters",JSONObject().put("type","object").put("properties",properties).put("required",org.json.JSONArray(fields.keys.toList()))))}}
 }
}
/** Deterministic user-command shortcuts only; ordinary text stays in the existing AI chat path. */
fun parseUserCommand(text:String):ToolAction? {
 val t=text.trim();com.raaz.assistant.timer.parseTimerCommand(t)?.let{return it};val rules=listOf(
  Triple(Regex("(?i)^(?:search youtube for|youtube search|یوٹیوب پر تلاش کریں|यूट्यूब पर खोजो)\\s+(.+)$"),"youtube_search","query"),
  Triple(Regex("(?i)^(?:open app|ایپ کھولو|ऐप खोलो)\\s+(.+)$"),"open_app","app"),
  Triple(Regex("(?i)^(?:search web for|web search)\\s+(.+)$"),"web_search","query"),
  Triple(Regex("(?i)^(?:call|dial|کال کرو|कॉल करो)\\s+(.+)$"),"dial","recipient"))
 for((pattern,name,key) in rules){val m=pattern.matchEntire(t)?:continue;return ToolAction(name,JSONObject().put(key,m.groupValues[1]))};return null
}

fun resolveInstalledApp(apps:List<InstalledApp>,input:String):InstalledApp {
 val query=input.trim()
 val trusted=mapOf("youtube" to "com.google.android.youtube","chrome" to "com.android.chrome","google chrome" to "com.android.chrome","maps" to "com.google.android.apps.maps","google maps" to "com.google.android.apps.maps")
 trusted[query.lowercase(Locale.ROOT)]?.let{pkg->apps.find{it.packageName==pkg}?.let{return it}}
 val matches=apps.filter{it.label.equals(query,true)||it.packageName==query}.distinctBy{it.packageName}
 require(matches.size==1){if(matches.isEmpty())"No exact app match. Choose an installed app from Tools." else "Several apps match. Choose one in Tools."}
 return matches.single()
}
fun resolveContactNumber(contacts:List<DeviceContact>,input:String):DeviceContact {
 val matches=contacts.filter{it.label.equals(input.trim(),true)}.distinctBy{it.lookup+"|"+canonicalPhone(it.phone)}
 require(matches.size==1){"Contact is missing or ambiguous. Select its number in Contacts and save an alias."}
 return matches.single()
}
