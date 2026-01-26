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
import com.example.cachetothefuture.data.BluetoothRepository
import com.example.cachetothefuture.data.RequestRepository
import com.example.cachetothefuture.data.StoredFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

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
    fun connectAndExchange(context: Context) {
        viewModelScope.launch {
            val allUrls = storedFilesRepository.getFilesAsUrls()
            Log.d("Ross", allUrls.toString())

            // Hardcoded MACs for timing

            withContext(Dispatchers.IO) {
                mutableListOf<Duration>()
                val name = Build.MODEL
                Log.d("Ross", name)
                bluetoothRepository.testWifiAware(context)
                /*
                val myMac = if (name == "SM-A536U1") "9C:2E:7A:49:72:87" else "58:79:E0:D9:4F:53"
                val otherMac = if (name == "SM-A536U1") "58:79:E0:D9:4F:53" else "9C:2E:7A:49:72:87"

                //val myMac = if ("ZTE" in name) "50:AF:4D:43:15:AF" else "58:79:E0:D9:4F:53"
                //val otherMac = if ("ZTE" in name) "58:79:E0:D9:4F:53" else "50:AF:4D:43:15:AF"
                Log.d("ross", myMac)
                Log.d("ross", otherMac)
                for (i in 1..1) {
                    Log.d("ross", i.toString())
                    bluetoothRepository.connectAndExchange(
                        allUrls,
                        requestRepository.requests.value,
                        myMac,
                        otherMac
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

                                is BluetoothEmit.Time -> sampleTimes.add(it.time)
                            }
                        }
                    if (false) {
                        withContext(Dispatchers.Default) {
                            val chars =
                                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                            val maxNonce = 200_000_000L

                            // Number of parallel workers (e.g., number of CPU cores)
                            val nWorkers = Runtime.getRuntime().availableProcessors()
                            val requiredZeroBits = 16

                            // Create a random input base string once per run
                            val input = (1..80_000).map { chars.random() }.joinToString("")

                            // Shared atomic flag to signal when a solution is found
                            val foundNonce = CompletableDeferred<Pair<Long, ByteArray>?>()
                            coroutineScope {
                                // Launch parallel workers
                                val jobs = (0 until nWorkers).map { workerId ->
                                    launch {
                                        val digestThread =
                                            MessageDigest.getInstance("SHA-256") // digest is not thread-safe!

                                        // Partition the nonce space for each worker to avoid overlap
                                        var nonce = workerId.toLong()
                                        while (nonce < maxNonce && !foundNonce.isCompleted) {
                                            val inputStr = "$input$nonce"
                                            val hash =
                                                digestThread.digest(inputStr.toByteArray(Charsets.UTF_8))

                                            // Count leading zero bits in the hash
                                            var totalLeadingZeros = 0
                                            for (byte in hash) {
                                                val b = byte.toInt() and 0xFF
                                                val leadingZeros =
                                                    Integer.numberOfLeadingZeros(b) - 24
                                                totalLeadingZeros += leadingZeros
                                                if (leadingZeros < 8) break
                                            }

                                            if (totalLeadingZeros >= requiredZeroBits) {
                                                // Try to complete foundNonce; only first will succeed
                                                foundNonce.complete(Pair(nonce, hash))
                                                break
                                            }
                                            nonce += nWorkers // step by number of workers to avoid collision

                                            // Optional: periodically check cancellation to be cooperative
                                            if (nonce % 10_000_000 == 0L && foundNonce.isCompleted) break
                                        }
                                    }
                                }
                                // Wait until one worker finds a nonce or all finish
                                val result = foundNonce.await()

                                // Cancel all workers if one succeeded
                                jobs.forEach { it.cancelAndJoin() }

                                if (result != null) {
                                    val (nonce, hash) = result
                                    val hashHex = hash.joinToString("") { "%02x".format(it) }
                                    val bits = hash.joinToString("") {
                                        String.format("%8s", (it.toInt() and 0xFF).toString(2))
                                            .replace(' ', '0')
                                    }
                                    Log.d("ross", "Success! Nonce: $nonce")
                                    Log.d("ross", "Hash: $hashHex")
                                    Log.d("ross", "Bits: $bits")
                                } else {
                                    Log.d("ross", "Max nonce reached without success")
                                }
                            }

                        }
                    }
                }
                Log.d("Ross", sampleTimes.toString())*/
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