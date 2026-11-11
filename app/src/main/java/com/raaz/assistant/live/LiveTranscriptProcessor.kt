package com.raaz.assistant.live

import org.json.JSONObject

/** GPT-Live fragments have timing but no completed-turn marker. No legacy Realtime VAD events. */
class LiveTranscriptProcessor {
 private val seen=LinkedHashSet<String>()
 fun decode(event:JSONObject):CaptionFragment? {
  val type=event.optString("type")
  val speaker=when(type){"session.input_transcript.delta"->"user";"session.output_transcript.delta"->"assistant";else->return null}
  val id=event.optString("event_id");if(id.isNotEmpty()&&!seen.add(id))return null
  if(seen.size>1024){val i=seen.iterator();i.next();i.remove()}
  val delta=event.opt("delta") as? String?:return null
  val start=(event.opt("start_ms") as? Number)?.toLong()?:return null;val end=(event.opt("end_ms") as? Number)?.toLong()?:return null
  if(start<0||end<start)return null
  return CaptionFragment(speaker,delta,start,end,id)
 }
 fun clear(){seen.clear()}
}
