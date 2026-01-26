package com.example.cachetothefuture

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cachetothefuture.data.BluetoothRepository

class BluetoothBroadcastReceiver(private val bluetoothRepository: BluetoothRepository) :
    BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> bluetoothRepository.bluetoothEnabledFlow.value =
                        true

                    BluetoothAdapter.STATE_OFF -> bluetoothRepository.bluetoothEnabledFlow.value =
                        false
                }
            }
        }
    }
}