package com.example.cachetothefuture.data

import android.app.DownloadManager
import android.bluetooth.BluetoothManager

class AppContainer(
    private val bluetoothManager: BluetoothManager,
    private val downloadManager: DownloadManager
) {
    val bluetoothRepository = BluetoothRepository(bluetoothManager)
    val storedFilesRepository = StoredFilesRepository(downloadManager)
    val requestRepository = RequestRepository()
}