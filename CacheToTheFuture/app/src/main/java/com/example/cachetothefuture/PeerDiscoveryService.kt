package com.example.cachetothefuture

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.cachetothefuture.data.BluetoothEmit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method


class PeerDiscoveryService : Service() {
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        val method: Method = wifiP2pManager::class.java.getMethod(
            "setDeviceName",
            WifiP2pManager.Channel::class.java,
            String::class.java,
            ActionListener::class.java
        )
        val sharedPref = getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
        val bluetoothMac = sharedPref.getString("mac", "not_set") ?: "not_set"
        Log.d("Ross", "Setting wifi direct name")
        method.invoke(wifiP2pManager, channel, "CttF-${bluetoothMac}", null)
        Log.d("Ross", "done setting wifi direct name")
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }
        Log.d("ross", "Running")
        val notificationChannel = NotificationChannel(
            "WifiDirectChannel",
            "CacheToTheFutureChannel",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationChannel.description = "Channel for foreground service notification"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)
        val notification = NotificationCompat.Builder(this, "WifiDirectChannel")
            .setContentTitle("Wi-Fi Direct Service")
            .setContentText("Discovering peers...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            100,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )

        wifiP2pManager.discoverPeers(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d("ross", "Peer Discovery started")
            }

            override fun onFailure(reason: Int) {
                Log.d("ross", "Peer discovery failed: $reason")
            }
        })

        val app = application as MyApplication
        val bluetoothRepository = app.container.bluetoothRepository
        val storedFilesRepository = app.container.storedFilesRepository
        val requestRepository = app.container.requestRepository
        val allUrls = storedFilesRepository.getFilesAsUrls()
        val context = this
        val sharedPref = getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
        val myMac = sharedPref.getString("mac", "") ?: ""

        handler.post(object : Runnable {
            override fun run() {
                wifiP2pManager.requestPeers(channel) { peerList ->
                    if (peerList.deviceList.isEmpty()) {
                        Log.d("ross", "No peers found")
                    } else {
                        val peer =
                            peerList.deviceList.find { device -> device.deviceName.contains("CttF") }
                        if (peer != null) {
                            val otherMacAddress = peer.deviceName.substring(5)
                            Log.d("Ross", otherMacAddress)

                            CoroutineScope(Dispatchers.IO).launch {
                                bluetoothRepository.connectAndExchange(
                                    allUrls,
                                    requestRepository.requests.value,
                                    myMac,
                                    otherMacAddress
                                )
                                    .collect {
                                        when (it) {
                                            is BluetoothEmit.OtherSaved -> storedFilesRepository.addOtherCachedUrl(
                                                it.storedUrl,
                                                context
                                            )

                                            is BluetoothEmit.UnsatisfiedRequest -> requestRepository.storeRequest(
                                                it.request
                                            )

                                            is BluetoothEmit.Time -> Unit
                                        }
                                    }
                            }
                        }
                    }
                }

                // Schedule next execution
                handler.postDelayed(this, 15 * 1000)
            }
        })

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ross", "destroyed")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}