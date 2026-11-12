package com.raaz.assistant.features

import com.raaz.assistant.data.SettingsStore
import org.json.JSONObject
import java.util.Locale

/** Shared by actual UI requests: stale completions cannot restore revoked private results. */
class LatestRequestGate {
 private var epoch=0L
 @Synchronized fun begin()=++epoch
 @Synchronized fun invalidate(){++epoch}
 @Synchronized fun current(ticket:Long)=ticket==epoch
}
fun canonicalPhone(phone:String)=phone.replace(Regex("[ ()-]"),"")
fun samePhone(first:String,second:String)=validPhone(first)&&validPhone(second)&&canonicalPhone(first)==canonicalPhone(second)
class SelectedRecipientPreferences(private val settings:SettingsStore) {
 fun read():DeviceContact? {
  val raw=settings.string("selected_recipient")?:return null
  val j=JSONObject(raw);val c=DeviceContact(j.getString("label"),j.getString("phone"),j.optString("lookup"))
  require(c.label.isNotBlank()&&c.label.length<=150&&validPhone(c.phone)&&c.lookup.length<=500)
  return c
 }
 fun save(contact:DeviceContact){require(contact.label.isNotBlank()&&contact.label.length<=150&&validPhone(contact.phone)&&contact.lookup.length<=500);settings.edit(mapOf("selected_recipient" to JSONObject().put("label",contact.label).put("phone",contact.phone).put("lookup",contact.lookup).toString()))}
 fun clear()=settings.edit(mapOf("selected_recipient" to null))
}
fun selectedRecipientReference(input:String)=input.trim().lowercase(Locale.ROOT) in setOf("selected contact","my selected contact","منتخب رابطہ","चुना हुआ संपर्क")
