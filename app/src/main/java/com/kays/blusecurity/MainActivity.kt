package com.kays.blusecurity

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
 private lateinit var events: TextView
 private lateinit var state: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,40,36,30); setBackgroundColor(Color.rgb(9,17,31)) }
  fun label(s:String, size:Float):TextView = TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(0,12,0,12) }
  layout.addView(label("KAY’S BLU SECURITY",26f))
  layout.addView(label("Bluetooth awareness • Local device monitoring",14f))
  state=label("Monitoring is off",18f); layout.addView(state)
  val start=Button(this).apply { text="START MONITORING" }; layout.addView(start)
  val stop=Button(this).apply { text="STOP MONITORING" }; layout.addView(stop)
  val settings=Button(this).apply { text="OPEN BLUETOOTH SETTINGS" }; layout.addView(settings)
  layout.addView(label("Nearby BLE advertisements trigger one beep. A connection event reported by Android triggers two beeps. Distance is a rough RSSI estimate, not GPS. Some devices hide their identity or cannot be detected.",13f))
  layout.addView(label("Recent detections and connection events",18f))
  val scroll=ScrollView(this); events=label("No events yet",13f); scroll.addView(events); layout.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
  setContentView(layout)
  start.setOnClickListener { if (hasPermissions()) begin() else requestPermissions(permissions(),11) }
  stop.setOnClickListener { stopService(Intent(this,MonitorService::class.java)); state.text="Monitoring is off"; refresh() }
  settings.setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
  refresh()
 }
 private fun permissions():Array<String> = if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS) else if(Build.VERSION.SDK_INT>=31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
 private fun hasPermissions()=permissions().all { checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED }
 private fun begin(){ val adapter=getSystemService(BluetoothManager::class.java)?.adapter; if(adapter?.isEnabled!=true){ Toast.makeText(this,"Turn Bluetooth on in settings first",Toast.LENGTH_LONG).show();return }; startForegroundService(Intent(this,MonitorService::class.java)); state.text="Monitoring requested"; refresh() }
 private fun refresh(){ events.text=getSharedPreferences("events",MODE_PRIVATE).getString("log","No events yet") }
 override fun onResume(){ super.onResume(); if(::events.isInitialized) refresh() }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){ super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode==11){ if(hasPermissions()) begin() else Toast.makeText(this,"Permissions are needed to scan and notify",Toast.LENGTH_LONG).show() } }
}