package com.example.cachetothefuture.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cachetothefuture.MyApplication
import com.example.cachetothefuture.data.BluetoothEmit
import com.example.cachetothefuture.data.BluetoothRepository
import com.example.cachetothefuture.data.RequestRepository
import com.example.cachetothefuture.data.StoredFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkViewModel(
    private val bluetoothRepository: BluetoothRepository,
    private val storedFilesRepository: StoredFilesRepository,
    private val requestRepository: RequestRepository
) : ViewModel() {

    fun isBluetoothSupported(): Boolean {
        return bluetoothRepository.isBluetoothSupported()
    }

    fun isBluetoothEnabled(): StateFlow<Boolean> {
        return bluetoothRepository.isBluetoothEnabled()
    }

    fun getNetworkUpdates(): StateFlow<List<String>> {
        return bluetoothRepository.networkUpdates
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun testL2capThroughput(isServer: Boolean) {
        bluetoothRepository.testL2capThroughput(isServer)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun testWifiAware(context: Context, isServer: Boolean) {
        bluetoothRepository.testWifiAware(context, isServer)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    fun testRfcommThroughput(context: Context) {
        val sharedPref = context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
        val myMac = sharedPref.getString("mac", "") ?: ""
        val peerMac = sharedPref.getString("peer_mac", "") ?: ""
        if (myMac.isBlank() || peerMac.isBlank()) {
            Log.e("CTTF", "MAC addresses not configured")
            return
        }
        bluetoothRepository.testRfcommThroughput(myMac, peerMac)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun connectAndExchange(context: Context) {
        viewModelScope.launch {
            val allUrls = storedFilesRepository.getFilesAsUrls()
            Log.d("CTTF", allUrls.toString())

            withContext(Dispatchers.IO) {
                val sharedPref = context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
                val myMac = sharedPref.getString("mac", "") ?: ""
                val peerMac = sharedPref.getString("peer_mac", "") ?: ""
                if (myMac.isBlank() || peerMac.isBlank()) {
                    Log.e("CTTF", "MAC addresses not configured")
                    return@withContext
                }
                Log.d("CTTF", myMac)
                Log.d("CTTF", peerMac)
                bluetoothRepository.connectAndExchange(
                    allUrls,
                    requestRepository.requests.value,
                    myMac,
                    peerMac
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

                            is BluetoothEmit.Time -> {}
                        }
                    }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as MyApplication)
                val bluetoothRepository = application.container.bluetoothRepository
                val storedFilesRepository = application.container.storedFilesRepository
                NetworkViewModel(
                    bluetoothRepository,
                    storedFilesRepository,
                    application.container.requestRepository
                )
            }
        }
    }
}
