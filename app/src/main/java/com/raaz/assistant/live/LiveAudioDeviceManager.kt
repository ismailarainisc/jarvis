package com.raaz.assistant.live

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Uses supported communication-device APIs; never opens a second audio capture/playback stream. */
@Suppress("DEPRECATION") // Legacy routing is required only below Android 12.
class LiveAudioDeviceManager(context:Context,private val changed:(List<LiveAudioRoute>,Int?,String?)->Unit,private val lost:()->Unit) {
 private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private val main=Handler(Looper.getMainLooper());private var running=false;private var prior:AudioDeviceInfo?=null;private var oldSpeaker=false
 private var listener:AudioManager.OnCommunicationDeviceChangedListener?=null;private var lastSelected:Int?=null
 private val callback=object:AudioDeviceCallback(){
  override fun onAudioDevicesAdded(addedDevices:Array<out AudioDeviceInfo>){if(running)refresh()}
  override fun onAudioDevicesRemoved(removedDevices:Array<out AudioDeviceInfo>){if(!running)return;val selected=lastSelected;val selectedLost=if(Build.VERSION.SDK_INT>=31)selectedRouteWasRemoved(selected,removedDevices.map{it.id})else removedDevices.any{it.isSink&&legacyHeadset(it.type)};if(selectedLost)lost() else refresh()}
 }
 fun start(){if(running)return;running=true;oldSpeaker=audio.isSpeakerphoneOn
  try{audio.registerAudioDeviceCallback(callback,main);if(Build.VERSION.SDK_INT>=31){prior=audio.communicationDevice;listener=AudioManager.OnCommunicationDeviceChangedListener{if(running)refresh()}.also{audio.addOnCommunicationDeviceChangedListener(contextExecutor(),it)};val devices=audio.availableCommunicationDevices;val external=devices.firstOrNull{it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET)};val target=external?:devices.firstOrNull{it.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER};if(target!=null)audio.setCommunicationDevice(target)};refresh()}catch(e:Exception){changed(emptyList(),null,"Route selection unavailable; Android controls the output.")}
 }
 private fun legacyHeadset(type:Int):Boolean=type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_BLUETOOTH_SCO)||(Build.VERSION.SDK_INT>=26&&type==AudioDeviceInfo.TYPE_USB_HEADSET)
 private fun contextExecutor()=java.util.concurrent.Executor{command->main.post(command)}
 fun refresh(){if(!running)return;try{if(Build.VERSION.SDK_INT>=31){val devices=audio.availableCommunicationDevices;lastSelected=audio.communicationDevice?.id;changed(devices.map{LiveAudioRoute(it.id,label(it))},lastSelected,null)}else{lastSelected=if(audio.isSpeakerphoneOn)-1 else -2;changed(listOf(LiveAudioRoute(-1,"Speaker"),LiveAudioRoute(-2,"System / headset")),lastSelected,"On Android 6–11, headset and Bluetooth routing is managed by Android.")}}catch(e:Exception){changed(emptyList(),null,"Unable to query audio devices; Android manages the route.")}}
 fun select(id:Int){if(!running)return;try{if(Build.VERSION.SDK_INT>=31){val device=audio.availableCommunicationDevices.firstOrNull{it.id==id};if(device==null||!audio.setCommunicationDevice(device)){changed(audio.availableCommunicationDevices.map{LiveAudioRoute(it.id,label(it))},audio.communicationDevice?.id,"This route could not be selected.");return}}else{audio.isSpeakerphoneOn=id==-1};refresh()}catch(e:Exception){changed(emptyList(),null,"Output route could not be changed. Check headset permissions in Android settings.")}}
 fun stop():Boolean {if(!running)return true;running=false;val cleanup=AudioResourceCleanup();return cleanup.close(listOf(
  {audio.unregisterAudioDeviceCallback(callback)},
  {if(Build.VERSION.SDK_INT>=31){listener?.let{audio.removeOnCommunicationDeviceChangedListener(it)};listener=null}},
  {if(Build.VERSION.SDK_INT>=31){val previous=prior;if(previous!=null&&audio.availableCommunicationDevices.any{it.id==previous.id})audio.setCommunicationDevice(previous)else audio.clearCommunicationDevice()}else{audio.isSpeakerphoneOn=oldSpeaker}},
  {lastSelected=null}
 ))}
 private fun label(d:AudioDeviceInfo):String=when(d.type){AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Speaker";AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Earpiece";AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"Bluetooth headset";AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES->"Wired headset";AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_DEVICE->"USB audio";else->d.productName.toString()}
}
