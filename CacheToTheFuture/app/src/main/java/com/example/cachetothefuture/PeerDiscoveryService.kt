package com.example.cachetothefuture

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.cachetothefuture.data.BluetoothEmit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.UUID


class PeerDiscoveryService : Service() {
    private lateinit var notificationManager: NotificationManager
    private val serviceUUID = ParcelUuid(UUID.fromString("a1f5b23a-5e28-4272-adc8-20718e5d5509"))

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.d(TAG, "Start Failure $errorCode")
            super.onStartFailure(errorCode)
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Starting Advertising")
            super.onStartSuccess(settingsInEffect)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "Scan Failure $errorCode")
            super.onScanFailed(errorCode)
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.d(TAG, "Scan result $callbackType")
            Log.d(TAG, "$result")
            notificationManager.notify(
                101, // different ID
                NotificationCompat.Builder(this@PeerDiscoveryService, "CTTFNotification")
                    .setContentText("Device $result")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setAutoCancel(true)
                    .build()
            )
            super.onScanResult(callbackType, result)
        }

    }

    override fun onCreate() {
        notificationManager = getSystemService(NotificationManager::class.java)
        super.onCreate()
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        val advertiseData = AdvertiseData.Builder().addServiceUuid(serviceUUID).build()
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        if (intent?.action == "stop") {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            bluetoothLeScanner.stopScan(scanCallback)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d("ross", "Running")
        val notificationChannel = NotificationChannel(
            "CTTFNotification",
            "CacheToTheFutureChannel",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationChannel.description = "Channel for foreground service notification"

        notificationManager.createNotificationChannel(notificationChannel)
        val notification = NotificationCompat.Builder(this, "CTTFNotification")
            .setContentTitle("BLE Service")
            .setContentText("Discovering peers...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            100,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )

        bluetoothLeAdvertiser.startAdvertising(AdvertiseSettings.Builder().build(), advertiseData, advertiseCallback)

        bluetoothLeScanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(serviceUUID).build()),
            ScanSettings.Builder().build(), scanCallback)

        val app = application as MyApplication
        val bluetoothRepository = app.container.bluetoothRepository
        val storedFilesRepository = app.container.storedFilesRepository
        val requestRepository = app.container.requestRepository
        val allUrls = storedFilesRepository.getFilesAsUrls()
        val context = this
        val sharedPref = getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
        val myMac = sharedPref.getString("mac", "") ?: ""

        return START_STICKY
    }

    override fun onDestroy() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        bluetoothLeScanner.stopScan(scanCallback)
        super.onDestroy()
        Log.d("ross", "destroyed")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}