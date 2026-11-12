package com.raaz.assistant.features

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raaz.assistant.background.*

data class CapabilitySnapshot(val microphone:Boolean=false,val contacts:Boolean=false,val notifications:Boolean=false,val assistant:Boolean?=null,val accessibility:Boolean=false,val network:String="Not checked",val battery:Int?=null,val charging:Boolean?=null,val sdk:Int=Build.VERSION.SDK_INT,val wakeNotificationVisible:Boolean=false)
fun capabilitySnapshot(context:Context):CapabilitySnapshot {
 fun granted(p:String)=ContextCompat.checkSelfPermission(context,p)==PackageManager.PERMISSION_GRANTED
 val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
 val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1;val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
 val percent=if(level>=0&&scale>0)(level*100/scale).coerceIn(0,100)else null
 val charge=battery?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)?.takeIf{it>=0}?.let{it==BatteryManager.BATTERY_STATUS_CHARGING||it==BatteryManager.BATTERY_STATUS_FULL}
 val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
 val caps=cm.getNetworkCapabilities(cm.activeNetwork)
 val net=when{caps==null->"No active network";caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)->"Android reports validated internet";caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)->"Network present; internet not validated";else->"Local network; internet not reported"}
 val component=ComponentName(context,ScopedAutomationService::class.java)
 val accessibility=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any{ComponentName.unflattenFromString(it)==component}
 val assistant=if(Build.VERSION.SDK_INT>=29){val role=context.getSystemService(RoleManager::class.java);role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&role.isRoleHeld(RoleManager.ROLE_ASSISTANT)}else null
 return CapabilitySnapshot(granted(Manifest.permission.RECORD_AUDIO),granted(Manifest.permission.READ_CONTACTS),NotificationManagerCompat.from(context).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||granted(Manifest.permission.POST_NOTIFICATIONS)),assistant,accessibility,net,percent,charge,wakeNotificationVisible=backgroundNotificationVisible(context))
}
fun assistantSetupIntent(activity:Activity):Intent {
 if(Build.VERSION.SDK_INT>=29){val role=activity.getSystemService(RoleManager::class.java);if(role.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&!role.isRoleHeld(RoleManager.ROLE_ASSISTANT))return role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)}
 return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
}

/** Includes the channel's actual visibility; runtime POST_NOTIFICATIONS alone is insufficient. */
fun backgroundNotificationVisible(context:Context):Boolean {
 if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return false
 if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
 return Build.VERSION.SDK_INT<26||(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(BackgroundAssistantService.CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
}
