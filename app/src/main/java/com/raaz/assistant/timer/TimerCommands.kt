package com.raaz.assistant.timer

import com.raaz.assistant.features.ToolAction
import org.json.JSONObject
import java.util.Locale
/** Deterministic text shortcuts; Live speech understanding uses the real declared Gemini tools. */
fun parseTimerCommand(input:String):ToolAction? {
 val t=input.trim().trimEnd('.','?','۔','؟').lowercase(Locale.ROOT).removePrefix("hey jarvis, ").removePrefix("jarvis, ")
 if(t in setOf("how much time is left","timer status","کتنا وقت باقی ہے","कितना समय बाकी है"))return ToolAction("timer_status",JSONObject())
 if(t in setOf("cancel my timer","cancel timer","ٹائمر منسوخ کرو","टाइमर रद्द करो"))return ToolAction("cancel_timer",JSONObject())
 if(t in setOf("stop my timer","stop timer","ٹائمر بند کرو","टाइमर बंद करो"))return ToolAction("stop_timer",JSONObject())
 val m=Regex("^(?:start|set)(?: a)? timer for (.+) (seconds?|minutes?)$").matchEntire(t)
 val local=Regex("^(.+) (منٹ|سیکنڈ|मिनट|सेकंड) (?:کا ٹائمر لگاؤ|کا ٹائمر شروع کرو|का टाइमर लगाओ|का टाइमर शुरू करो)$").matchEntire(t)
 val match=m?:local?:return null
 val raw=match.groupValues[1].map{c->if(c.isDigit())Character.getNumericValue(c).digitToChar()else c}.joinToString("")
 val words=mapOf("one" to 1,"two" to 2,"three" to 3,"five" to 5,"ten" to 10,"thirty" to 30,"ایک" to 1,"پانچ" to 5,"تیس" to 30,"एक" to 1,"पाँच" to 5,"पांच" to 5,"तीस" to 30)
 val n=raw.toLongOrNull()?:words[raw]?.toLong()?:return null
 val seconds=try{Math.multiplyExact(n,if(match.groupValues[2] in setOf("minute","minutes","منٹ","मिनट"))60L else 1L)}catch(e:ArithmeticException){return null}
 return ToolAction("set_timer",JSONObject().put("seconds",seconds))
}
