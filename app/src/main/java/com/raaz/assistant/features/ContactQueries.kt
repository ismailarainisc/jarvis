package com.raaz.assistant.features

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import kotlinx.coroutines.*
import java.io.IOException

/** Cancelled/expired reads signal the real ContentProvider; null cursor is failure, not zero contacts. */
internal suspend fun <T> contactQuery(resolver:ContentResolver,uri:Uri,projection:Array<String>,selection:String?=null,args:Array<String>?=null,read:(Cursor)->T):T=withTimeout(10000){withContext(Dispatchers.IO){
 suspendCancellableCoroutine { result ->
  val signal=CancellationSignal();result.invokeOnCancellation{signal.cancel()}
  try{val value=resolver.query(uri,projection,selection,args,null,signal)?.use{cursor->if(!result.isActive)throw CancellationException();read(cursor)}?:throw IOException("Contacts provider unavailable")
   if(result.isActive)result.resumeWith(Result.success(value))
  }catch(e:Exception){if(result.isActive)result.resumeWith(Result.failure(e))}
 }
}}
