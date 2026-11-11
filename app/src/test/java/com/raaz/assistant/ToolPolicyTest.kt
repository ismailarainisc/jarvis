package com.raaz.assistant

import com.raaz.assistant.features.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ToolPolicyTest {
 private fun tool(name:String,vararg args:Pair<String,Any>)=ToolAction(name,JSONObject().also{o->args.forEach{(k,v)->o.put(k,v)}})
 @Test fun dangerousSchemesAndEmbeddedCredentialsAreRejected(){for(url in listOf("javascript:alert(1)","file:///etc/passwd","http://example.com","https://secret@example.com")){assertNotNull(ToolRegistry.validate(tool("open_url","url" to url)))}}
 @Test fun youtubeHostCannotBeSpoofed(){assertNotNull(ToolRegistry.validate(tool("youtube_open","url" to "https://youtube.com.evil.example/watch?v=123")));assertNull(ToolRegistry.validate(tool("youtube_open","url" to "https://youtu.be/123")))}
 @Test fun ussdAndControlCharactersAreNotPhoneNumbers(){for(phone in listOf("*123#","+12025550123\n","tel:12345","123;456")){assertFalse(validPhone(phone))};assertTrue(validPhone("+1 202-555-0123"))}
 @Test fun whatsappRequiresExplicitCountryCode(){assertThrows(IllegalArgumentException::class.java){whatsappNumber("03001234567")};assertEquals("923001234567",whatsappNumber("+92 300 1234567"))}
 @Test fun timeArgumentsCannotOverflowOrBeFractional(){for(value in listOf(-1,0,86401,1.5)){assertNotNull(ToolRegistry.validate(tool("set_timer","seconds" to value)))};assertNull(ToolRegistry.validate(tool("set_alarm","hour" to 23,"minute" to 59)));assertNotNull(ToolRegistry.validate(tool("set_alarm","hour" to 23.5,"minute" to 59)))}
 @Test fun accessibilityScopeExcludesSystemPermissionsAndPayments(){for(pkg in listOf("com.android.settings","com.android.permissioncontroller","com.bank.app")){assertNotNull(ToolRegistry.validate(tool("scroll_app","package" to pkg,"direction" to "forward")))};assertFalse("click_node" in ToolRegistry.names)}
 @Test fun credentialsCannotBeSavedAsMemory(){assertFalse(safeMemory("API key","example"));assertFalse(safeMemory("note","my password is example"));assertTrue(safeMemory("Reply language","Urdu"))}
 @Test fun naturalChatIsNotSilentlyConvertedIntoActions(){assertNull(parseUserCommand("What happens if someone says call Baba?"));assertEquals("youtube_search",parseUserCommand("Search YouTube for Kotlin tutorials")!!.name);assertEquals("dial",parseUserCommand("کال کرو بابا")!!.name)}
 @Test fun callerCannotRewriteSerializedProposal(){val a=tool("youtube_search","query" to "Kotlin");val frozen=ToolAction.decode(a.encode());a.args.put("query","different");assertEquals("Kotlin",frozen.args.getString("query"));assertFalse(a.toString().contains("different"))}
 @Test fun mcpConfigurationRejectsCredentialForwardingUrls(){for(url in listOf("https://token@example.com/mcp","https://example.com/mcp?key=value","http://example.com/mcp","https://example.com:444/mcp")){assertFalse(publicHttps(url))};assertTrue(publicHttps("https://example.com/mcp"))}
 @Test fun implicitMemoryRequiresBothConsentsAndNeverIncludesContacts(){val items=listOf(MemoryItem("1","alias","Baba","+12025550123"),MemoryItem("2","preference","Language","Urdu"),MemoryItem("3","note","Tea","No sugar"));assertEquals("",memoryContext(items,false,true,"Tea Baba"));assertEquals("",memoryContext(items,true,false,"Tea Baba"));val context=memoryContext(items,true,true,"Tea Baba");assertTrue(context.contains("Urdu"));assertTrue(context.contains("No sugar"));assertFalse(context.contains("1202555"))}
 @Test fun memoryContextIsRelevantBoundedAndSupportsUrdu(){val items=(1..20).map{MemoryItem(it.toString(),"note","چائے", "x".repeat(1000))};val result=memoryContext(items,true,true,"چائے کی پسند");assertTrue(result.isNotBlank());assertTrue(result.length<=1200);assertEquals("",memoryContext(items,true,true,"unrelated"))}
 @Test fun arbitraryToolsAndRawOversizedArgumentsAreRejected(){assertNotNull(ToolRegistry.validate(tool("send_anything","data" to "x")));assertNotNull(ToolRegistry.validate(tool("youtube_search","query" to "x".repeat(17000))))}
 @Test fun duplicateAppNamesRequireExplicitPackageChoice(){val apps=listOf(InstalledApp("Browser","first.browser"),InstalledApp("Browser","second.browser"));assertThrows(IllegalArgumentException::class.java){resolveInstalledApp(apps,"Browser")};assertEquals("first.browser",resolveInstalledApp(apps,"first.browser").packageName);assertThrows(IllegalArgumentException::class.java){resolveInstalledApp(apps,"missing")}}
 @Test fun duplicateContactNamesOrMultipleNumbersRequireSelection(){val contacts=listOf(DeviceContact("Ahmed","+12025550123","first"),DeviceContact("Ahmed","+12025550124","second"));assertThrows(IllegalArgumentException::class.java){resolveContactNumber(contacts,"Ahmed")};assertEquals("+12025550123",resolveContactNumber(listOf(contacts[0],contacts[0]),"Ahmed").phone);assertThrows(IllegalArgumentException::class.java){resolveContactNumber(contacts,"other")}}

 @Test fun nativeDeclarationsOnlyExposeImplementedApprovedTools(){val declarations=ToolRegistry.declarations();val names=(0 until declarations.length()).map{declarations.getJSONObject(it).getString("name")};assertTrue(names.all{it in ToolRegistry.names});assertTrue("open_settings" in names);assertTrue("scroll_app" in names);assertFalse("direct_call" in names);assertFalse("click_permission_allow" in names)}

}
