package com.example.cachetothefuture.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cachetothefuture.MyApplication
import com.example.cachetothefuture.data.RequestRepository
import com.example.cachetothefuture.data.StoredFilesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.pow
import kotlin.system.measureTimeMillis

class HomeViewModel(
    private val storedFilesRepository: StoredFilesRepository,
    private val requestRepository: RequestRepository
) : ViewModel() {
    var url = mutableStateOf("")
    var listOfUrls = storedFilesRepository.filesStored
    val otherCachedUrls = storedFilesRepository.othersHaveCached
    val requests = requestRepository.requests
    val powUpdates = MutableStateFlow<List<String>>(emptyList())

    private fun pushToUpdates(theString: String) {
        val newUpdates = powUpdates.value.toMutableList()
        newUpdates.add(theString)
        powUpdates.value = newUpdates
    }

    fun setURL(newUrl: String) {
        url.value = newUrl
    }

    fun addUrl(url: String, context: Context) {
        viewModelScope.launch {
            storedFilesRepository.downloadFile(url, context)
        }
    }

    fun openUrl(url: String, context: Context) {
        storedFilesRepository.openFile(url, context)
    }

    fun requestUrl(url: String) {
        requestRepository.storeRequest(url)
    }

    fun bruteForceHash() {
        viewModelScope.launch(Dispatchers.Default) {
            val startingVal = 14
            val endingVal = 19
            val runs = 50
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val maxNonce = 200_000_000L

            val timingValues =
                Array<Array<Double>>(endingVal - startingVal + 1) { Array<Double>(runs) { 0.0 } }
            // Number of parallel workers (e.g., number of CPU cores)
            val nWorkers = Runtime.getRuntime().availableProcessors()

            for (difficulty in startingVal..endingVal) {
                val requiredZeroBits = difficulty

                repeat(runs) {
                    // Create a random input base string once per run
                    val input = (1..80_000).map { chars.random() }.joinToString("")

                    // Shared atomic flag to signal when a solution is found
                    val foundNonce = CompletableDeferred<Pair<Long, ByteArray>?>()

                    val timeMillis = measureTimeMillis {
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
                                            val leadingZeros = Integer.numberOfLeadingZeros(b) - 24
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

                    timingValues[difficulty - startingVal][it] += timeMillis

                    pushToUpdates("Took $timeMillis ms (difficulty $difficulty run ${it + 1})")
                    Log.d("ross", "Took $timeMillis ms (difficulty $difficulty run ${it + 1})")
                }
                var sum: Double = 0.0
                for (sample in timingValues[difficulty - startingVal]) {
                    sum += sample
                }
                val avg = sum / runs
                var stddev: Double = 0.0
                for (sample in timingValues[difficulty - startingVal]) {
                    val diff: Double = (sample - avg)
                    val powed: Double = diff.pow(2)
                    stddev += powed
                }
                stddev *= (1.0 / (runs - 1))
                stddev = kotlin.math.sqrt(stddev)
                val newUpdates = powUpdates.value.toMutableList()
                newUpdates.subList(difficulty - startingVal, newUpdates.size).clear()
                powUpdates.value = newUpdates
                pushToUpdates("Difficulty $difficulty average time: $avg ms, stddev: $stddev")
                Log.d("ross", "Difficulty $difficulty average time: $avg ms, stddev: $stddev")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as MyApplication)
                val storedFilesRepository = application.container.storedFilesRepository
                val requestRepository = application.container.requestRepository
                HomeViewModel(storedFilesRepository, requestRepository)
            }
        }
    }
}