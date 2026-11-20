package com.kays.blusecurity

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class MonitorService : Service() {
 private val channel="blu_alerts"
 private val seen=ConcurrentHashMap<String,Long>()
 private val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,75)
 private var scanner:BluetoothLeScanner?=null
 private val handler=Handler(Looper.getMainLooper())
 private val callback=object:ScanCallback(){ override fun onScanResult(callbackType:Int,result:ScanResult){ handleResult(result) } }
 private val connectionReceiver=object:BroadcastReceiver(){ override fun onReceive(context:Context,intent:Intent){
  if(intent.action!=BluetoothDevice.ACTION_ACL_CONNECTED) return
  val device=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
  val name=try { device?.name ?: "Unknown device" } catch(_:SecurityException){"Unknown device"}
  record("CONNECTED: $name (Android ACL event)"); alert("Bluetooth connection",name+" connected to this phone",true)
 } }
 override fun onCreate(){super.onCreate(); val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(channel,"Bluetooth security alerts",NotificationManager.IMPORTANCE_HIGH)); val ongoing=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Kay’s Blu Security").setContentText("Monitoring nearby Bluetooth LE devices").setOngoing(true).build(); startForeground(1,ongoing)
  val filter=IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED); if(Build.VERSION.SDK_INT>=33) registerReceiver(connectionReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(connectionReceiver,filter)
  startScan()
 }
 private fun startScan(){ if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)return
  val adapter=getSystemService(BluetoothManager::class.java)?.adapter ?:return
  if(!adapter.isEnabled)return
  scanner=adapter.bluetoothLeScanner
  try { scanner?.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),callback) }catch(_:Exception){ record("Bluetooth scan could not start") }
 }
 private fun handleResult(result:ScanResult){ val key=result.device.address; val now=System.currentTimeMillis(); val prev=seen[key]?:0L; if(now-prev<60000)return;seen[key]=now
  val name=result.scanRecord?.deviceName ?: try{result.device.name}catch(_:SecurityException){null} ?: "Unknown BLE device"
  val tx=result.txPower.takeIf{it!=127 && it!=0} ?: -59
  val meters=10.0.pow((tx-result.rssi)/20.0).coerceIn(0.1,999.0)
  val msg="$name • RSSI ${result.rssi} dBm • approx %.1f m (unreliable estimate)".format(meters)
  record("NEARBY: $msg");alert("Nearby Bluetooth device",msg,false)
 }
 private fun record(message:String){ val prefs=getSharedPreferences("events",MODE_PRIVATE);val previous=prefs.getString("log","")?:"";prefs.edit().putString("log",(java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.US).format(java.util.Date())+"  "+message+"\n"+previous).take(15000)).apply() }
 private fun alert(title:String,message:String,double:Boolean){tone.startTone(ToneGenerator.TONE_PROP_BEEP,140);if(double)handler.postDelayed({tone.startTone(ToneGenerator.TONE_PROP_BEEP,140)},270)
  val notification=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle(title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message)).setAutoCancel(true).build()
  if(Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt().coerceAtLeast(2),notification)
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
 override fun onDestroy(){try{scanner?.stopScan(callback)}catch(_:Exception){};unregisterReceiver(connectionReceiver);tone.release();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}