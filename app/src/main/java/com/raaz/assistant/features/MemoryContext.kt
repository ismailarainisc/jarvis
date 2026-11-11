package com.raaz.assistant.features

/** Contact aliases never enter implicit cloud context, even when memory sharing is enabled. */
fun memoryContext(items:List<MemoryItem>,enabled:Boolean,share:Boolean,query:String):String {
 if(!enabled||!share)return ""
 val words=Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase()).map{it.value}.filter{it.length>2}.take(100).toSet()
 return items.filter{it.kind=="preference"||(it.kind=="note"&&words.any{word->it.label.lowercase().contains(word)})}.take(4).joinToString("\n"){"${it.label.take(120)}: ${it.value.take(300)}"}.take(1200)
}
