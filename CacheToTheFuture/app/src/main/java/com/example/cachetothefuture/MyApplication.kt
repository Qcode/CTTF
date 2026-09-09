package com.example.cachetothefuture

import android.app.Application
import android.app.DownloadManager
import android.bluetooth.BluetoothManager
import com.example.cachetothefuture.data.AppContainer


class MyApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager: BluetoothManager =
            applicationContext.getSystemService(BluetoothManager::class.java)
        val downloadManager: DownloadManager =
            applicationContext.getSystemService(DownloadManager::class.java)
        container = AppContainer(bluetoothManager, downloadManager)
    }
}