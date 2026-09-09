package com.example.cachetothefuture.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Environment
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration

sealed class BluetoothEmit {
    data class OtherSaved(val storedUrl: String) : BluetoothEmit()
    data class UnsatisfiedRequest(val request: Request) : BluetoothEmit()
    data class Time(val time: Duration) : BluetoothEmit()
}

class CountingOutputStream(private val outputStream: OutputStream) : OutputStream() {
    var byteCount: Long = 0
        private set

    override fun write(b: Int) {
        outputStream.write(b)
        byteCount++
    }

    override fun write(b: ByteArray) {
        outputStream.write(b)
        byteCount += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        outputStream.write(b, off, len)
        byteCount += len
    }

    override fun close() {
        outputStream.close()
    }

    override fun flush() {
        outputStream.flush()
    }
}

class BluetoothRepository(
    private val bluetoothManager: BluetoothManager,
) {
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    val bluetoothEnabledFlow = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val networkUpdates = MutableStateFlow<List<String>>(emptyList())

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): StateFlow<Boolean> {
        return bluetoothEnabledFlow
    }

    fun getNetworkUpdates(): StateFlow<List<String>> {
        return networkUpdates
    }

    private fun pushToUpdates(theString: String) {
        val newUpdates = networkUpdates.value.toMutableList()
        newUpdates.add(theString)
        networkUpdates.value = newUpdates
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun testWifiAware(context: Context) {
        val name = Build.MODEL
        Log.d("Ross", name)
        if ("S7" in name) {
            Log.d("Ross", "server starting")
            startWifiAwareServer(context)
        }
        if ("A53" in name || "ZTE" in name) {
            val packageManager = context.packageManager
            val hasWifiAware =
                packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
            Log.d("Ross", "WiFi Aware hardware support: $hasWifiAware")

            val wifiAwareManager =
                context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
            Log.d("Ross", "WiFi Aware service exists: ${wifiAwareManager != null}")

            if (wifiAwareManager != null) {
                Log.d("Ross", "WiFi Aware available: ${wifiAwareManager.isAvailable}")

                val characteristics = wifiAwareManager.characteristics
                Log.d("Ross", "WiFi Aware characteristics: $characteristics")
            }
            Log.d("Ross", "client starting")
            startWifiAwareClient(context)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startWifiAwareServer(context: Context) {
        val wifiAwareManager =
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager

        if (wifiAwareManager == null || !wifiAwareManager.isAvailable) {
            Log.e("Ross", "WiFi Aware not available")
            return
        }

        wifiAwareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.d("Ross", "Server: WiFi Aware attached")

                val config = PublishConfig.Builder()
                    .setServiceName("PrivateTransfer")
                    .build()

                var serverSession: PublishDiscoverySession? = null

                session.publish(config, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(publishSession: PublishDiscoverySession) {
                        Log.d("Ross", "Server: Publishing started")
                        serverSession = publishSession
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        if (String(message) == "CONNECT") {
                            Log.d("Ross", "Server: Connection request received")

                            serverSession?.let { session ->
                                val networkSpecifier = WifiAwareNetworkSpecifier.Builder(
                                    session,
                                    peerHandle
                                )
                                    .setPskPassphrase("SecurePass123")
                                    .setPort(9999)
                                    .build()

                                setupServerNetwork(context, networkSpecifier)
                            }
                        }
                    }
                }, null)
            }

            override fun onAttachFailed() {
                Log.e("Ross", "Server: Attach failed")
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupServerNetwork(context: Context, networkSpecifier: WifiAwareNetworkSpecifier) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        connectivityManager.requestNetwork(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("Ross", "Server: Network available, starting TCP server")

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val serverSocket = ServerSocket(9999)

                            while (true) {
                                val socket = serverSocket.accept()
                                Log.d("Ross", "Server: Client connected!")

                                val output = socket.outputStream
                                val data = ByteArray(1_000_000)
                                Random.Default.nextBytes(data)

                                val startTime = System.currentTimeMillis()

                                // Send in 64KB chunks
                                val chunkSize = 65536
                                var offset = 0
                                while (offset < data.size) {
                                    val length = minOf(chunkSize, data.size - offset)
                                    output.write(data, offset, length)
                                    offset += length
                                }
                                output.flush()

                                val timeTaken = System.currentTimeMillis() - startTime
                                val speedKbps = (data.size * 8.0 / timeTaken)
                                Log.d(
                                    "Ross",
                                    "Server: Transfer complete in ${timeTaken}ms = ${
                                        "%.2f".format(speedKbps)
                                    } Kbps"
                                )

                                socket.close()
                            }
                        } catch (e: Exception) {
                            Log.e("Ross", "Server error", e)
                        }
                    }
                }

                override fun onUnavailable() {
                    Log.e("Ross", "Server: Network unavailable")
                }
            })
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startWifiAwareClient(context: Context) {
        val wifiAwareManager =
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager

        if (wifiAwareManager == null || !wifiAwareManager.isAvailable) {
            Log.e("Ross", "WiFi Aware not available")
            return
        }

        wifiAwareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.d("Ross", "Client: WiFi Aware attached")

                val config = SubscribeConfig.Builder()
                    .setServiceName("PrivateTransfer")
                    .build()

                var clientSession: SubscribeDiscoverySession? = null

                session.subscribe(config, object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(subscribeSession: SubscribeDiscoverySession) {
                        Log.d("Ross", "Client: Subscribing started")
                        clientSession = subscribeSession
                    }

                    override fun onServiceDiscovered(
                        peerHandle: PeerHandle,
                        serviceSpecificInfo: ByteArray,
                        matchFilter: MutableList<ByteArray>
                    ) {
                        Log.d("Ross", "Client: Service discovered!")

                        clientSession?.let { session ->
                            // Send connection request
                            session.sendMessage(peerHandle, 0, "CONNECT".toByteArray())

                            val networkSpecifier = WifiAwareNetworkSpecifier.Builder(
                                session,
                                peerHandle
                            )
                                .setPskPassphrase("SecurePass123")
                                .setPort(9999)
                                .build()

                            setupClientNetwork(context, networkSpecifier)
                        }
                    }
                }, null)
            }

            override fun onAttachFailed() {
                Log.e("Ross", "Client: Attach failed")
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupClientNetwork(context: Context, networkSpecifier: WifiAwareNetworkSpecifier) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        connectivityManager.requestNetwork(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("Ross", "Client: Network available, connecting to server")

                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500) // Give server time to start listening

                        try {
                            // Connect using the WiFi Aware network
                            val socket = network.socketFactory.createSocket()

                            // WiFi Aware uses IPv6 link-local addresses
                            val serverAddress = java.net.Inet6Address.getByName("fe80::1")
                            socket.connect(java.net.InetSocketAddress(serverAddress, 9999), 5000)

                            Log.d("Ross", "Client: Connected to server!")

                            val input = socket.inputStream
                            val buffer = ByteArray(65536)
                            val data = ByteArray(1_000_000)

                            val startTime = System.currentTimeMillis()
                            var bytesReadTotal = 0

                            // Measure in chunks
                            val chunkSize = 100_000
                            var chunkStartTime = startTime

                            while (bytesReadTotal < data.size) {
                                val bytesRead = input.read(
                                    buffer,
                                    0,
                                    minOf(buffer.size, data.size - bytesReadTotal)
                                )
                                if (bytesRead == -1) throw IOException("Stream closed early")

                                System.arraycopy(buffer, 0, data, bytesReadTotal, bytesRead)
                                bytesReadTotal += bytesRead

                                // Report every 100KB
                                if (bytesReadTotal % chunkSize < buffer.size && bytesReadTotal > 0) {
                                    val now = System.currentTimeMillis()
                                    val chunkTime = now - chunkStartTime
                                    val chunkSpeed = (chunkSize * 8.0 / chunkTime)
                                    Log.d(
                                        "Ross",
                                        "${bytesReadTotal / 1000}KB: ${chunkTime}ms = ${
                                            "%.2f".format(chunkSpeed)
                                        } Kbps"
                                    )
                                    chunkStartTime = now
                                }
                            }

                            val totalTime = System.currentTimeMillis() - startTime
                            val avgSpeed = (bytesReadTotal * 8.0 / totalTime)

                            Log.d(
                                "Ross",
                                "Client: Total: ${bytesReadTotal} bytes in ${totalTime}ms = ${
                                    "%.2f".format(avgSpeed)
                                } Kbps"
                            )

                            socket.close()
                        } catch (e: Exception) {
                            Log.e("Ross", "Client connection error", e)
                        }
                    }
                }

                override fun onUnavailable() {
                    Log.e("Ross", "Client: Network unavailable")
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    // Log the actual IPv6 address for debugging
                    linkProperties.linkAddresses.forEach { addr ->
                        Log.d("Ross", "Client: Link address: ${addr.address}")
                    }
                }
            })
    }
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun testL2capThroughput() {
        Log.d("Ross", "2M PHY supported: ${bluetoothAdapter?.isLe2MPhySupported}")

        val name = Build.MODEL
        if ("ZTE" in name) {
            Log.d("Ross", "server starting")
            startL2capServer()
        }
        if ("A53" in name) {
            Log.d("Ross", "scan starting")
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val serviceData =
                        result.scanRecord?.getServiceData(ParcelUuid(UUID.fromString("0b5c98e5-6deb-4970-88cd-5241067ed52f")))
                    if (serviceData != null) {
                        val psm =
                            ((serviceData[0].toInt() and 0xFF) shl 8) or (serviceData[1].toInt() and 0xFF)
                        Log.d("Ross", "Found device ${device.address} with PSM $psm")
                        connectL2cap(device, psm)
                        scanner?.stopScan(this)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("Ross", "Scan failed with error: $errorCode")
                }
            }
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner?.startScan(null, scanSettings, scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startL2capServer() {
        val serverSocket =
            requireNotNull(bluetoothAdapter?.listenUsingInsecureL2capChannel()) { "Bluetooth adapter not available" }
        val psm = serverSocket.psm

        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(
                ParcelUuid(UUID.fromString("0b5c98e5-6deb-4970-88cd-5241067ed52f")),
                psm.toByteArray()
            )
            .build()

        val advCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("Ross", "Advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("Ross", "Advertising failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, advData, advCallback)
        Log.d("Ross", "Server listening on PSM $psm")
        pushToUpdates("L2CAP Server ready - PSM: $psm")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = serverSocket.accept()
                Log.d("Ross", "Client connected!")
                delay(200)

                val output = socket.outputStream
                val iterations = 100
                val dataSize = 1_000_000
                val chunkSize = 65535

                for (i in 1..iterations) {
                    val randomByteArray = ByteArray(dataSize)
                    Random.Default.nextBytes(randomByteArray)

                    var offset = 0
                    while (offset < randomByteArray.size) {
                        val length = minOf(chunkSize, randomByteArray.size - offset)
                        output.write(randomByteArray, offset, length)
                        offset += length
                    }
                    output.flush()
                    Log.d("Ross", "Server: Transfer $i/$iterations complete")
                }

                Log.d("Ross", "Server: All $iterations transfers complete")
                socket.close()
                serverSocket.close()
                advertiser?.stopAdvertising(advCallback)
            } catch (e: Exception) {
                Log.e("Ross", "Server error", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectL2cap(device: BluetoothDevice, psm: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var bluetoothGatt: BluetoothGatt? = null
                val connectionReady = CountDownLatch(1)

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt?,
                        status: Int,
                        newState: Int
                    ) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d("Ross", "GATT connected")
                            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                gatt?.setPreferredPhy(
                                    BluetoothDevice.PHY_LE_2M_MASK,
                                    BluetoothDevice.PHY_LE_2M_MASK,
                                    BluetoothDevice.PHY_OPTION_NO_PREFERRED
                                )
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                delay(500)
                                connectionReady.countDown()
                            }
                        }
                    }

                    override fun onPhyUpdate(
                        gatt: BluetoothGatt?,
                        txPhy: Int,
                        rxPhy: Int,
                        status: Int
                    ) {
                        Log.d("Ross", "PHY updated - TX: $txPhy, RX: $rxPhy, Status: $status")
                    }
                }

                bluetoothGatt = device.connectGatt(
                    null,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

                if (!connectionReady.await(3, TimeUnit.SECONDS)) {
                    Log.w("Ross", "Connection optimization timeout")
                }

                val socket = device.createInsecureL2capChannel(psm)
                socket.connect()

                val mtu = socket.maxReceivePacketSize
                Log.d("Ross", "L2CAP connected - MTU: $mtu")

                val input = socket.inputStream
                val buffer = ByteArray(mtu.coerceAtLeast(512))

                val iterations = 100
                val totalBytesPerIteration = 1_000_000
                val iterationTimes = mutableListOf<Long>()
                val overallStartTime = System.currentTimeMillis()

                for (i in 1..iterations) {
                    var bytesReadTotal = 0
                    val iterStartTime = System.currentTimeMillis()

                    while (bytesReadTotal < totalBytesPerIteration) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) throw IOException("Stream closed early on iteration $i")
                        bytesReadTotal += bytesRead
                    }

                    val iterTime = System.currentTimeMillis() - iterStartTime
                    val iterSpeed = (bytesReadTotal * 8.0 / iterTime)
                    iterationTimes.add(iterTime)

                    Log.d("Ross", "Iteration $i/$iterations: ${bytesReadTotal} bytes in ${iterTime}ms = ${"%.2f".format(iterSpeed)} Kbps")
                }

                val totalTime = System.currentTimeMillis() - overallStartTime
                val totalTransferred = totalBytesPerIteration.toLong() * iterations
                val avgSpeed = (totalTransferred * 8.0 / totalTime)

                val iterSpeeds = iterationTimes.map { totalBytesPerIteration * 8.0 / it }
                val meanSpeed = iterSpeeds.average()
                val stdDevSpeed = sqrt(iterSpeeds.map { (it - meanSpeed) * (it - meanSpeed) }.average())

                Log.d("Ross", "=== L2CAP Throughput Results ===")
                Log.d("Ross", "Iterations: $iterations")
                Log.d("Ross", "Total: $totalTransferred bytes in ${totalTime}ms")
                Log.d("Ross", "Average speed: ${"%.2f".format(avgSpeed)} Kbps")
                Log.d("Ross", "Std dev: ${"%.2f".format(stdDevSpeed)} Kbps")
                Log.d("Ross", "Min iteration: ${iterationTimes.min()}ms")
                Log.d("Ross", "Max iteration: ${iterationTimes.max()}ms")

                pushToUpdates("L2CAP: ${iterations}x1MB in ${totalTime}ms, avg ${"%.2f".format(avgSpeed)} +/- ${"%.2f".format(stdDevSpeed)} Kbps")

                socket.close()
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()

            } catch (e: Exception) {
                Log.e("Ross", "L2CAP connection failed", e)
            }
        }
    }

    fun Int.toByteArray(): ByteArray {
        return byteArrayOf((this shr 8).toByte(), (this and 0xFF).toByte())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    fun connectAndExchange(
        urlsToExchange: List<CachedPage>,
        requests: List<Request>,
        myMac: String,
        otherMac: String
    ): Flow<BluetoothEmit> {
        Log.d("Ross", bluetoothAdapter?.isLe2MPhySupported.toString())
        val otherStrings: Flow<BluetoothEmit> = flow {
            val uuid = UUID.fromString("0b5c98e5-6deb-4970-88cd-5241067ed52f")
            var server = false
            if (myMac > otherMac) {
                server = true
            }
            if (server) {
                pushToUpdates("Starting Server!")
                val serverSocket =
                    bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                        "CacheToTheFuture",
                        uuid
                    )
                val socket: BluetoothSocket? = serverSocket?.accept()
                pushToUpdates("Accepted")
                socket?.also { realSocket ->
                    communicate(realSocket, urlsToExchange, requests, server)
                }
                serverSocket?.close()
            } else {
                pushToUpdates("Starting client!")
                val serverDevice =
                    bluetoothAdapter?.getRemoteDevice(otherMac)
                val socket = serverDevice?.createInsecureRfcommSocketToServiceRecord(uuid)
                socket?.let { realSocket ->
                    pushToUpdates("Connecting to server")
                    while (true) {
                        try {
                            realSocket.connect()
                            pushToUpdates("Connected to server!")
                            communicate(realSocket, urlsToExchange, requests, server)
                            break
                        } catch (e: IOException) {
                            pushToUpdates("Failed to connect, retrying...")
                            Thread.sleep(2000) // wait 2 seconds before retry
                        }
                    }
                }
            }
        }
        return otherStrings
    }

    @SuppressLint("MissingPermission")
    private suspend fun FlowCollector<BluetoothEmit>.communicate(
        realSocket: BluetoothSocket,
        urlsToExchange: List<CachedPage>,
        requests: List<Request>,
        server: Boolean
    ) {
        pushToUpdates("Connected! to ${realSocket.remoteDevice.name}")
        /*
        val randomByteArray = ByteArray(1_000_000)
        val readByteArray = ByteArray(1_000_000)
        Random.Default.nextBytes(randomByteArray)
        val timeTaken = measureTime {
            if (server) {
                val outputStream = realSocket.outputStream

                var offset = 0
                val chunkSize = 1024
                while (offset < randomByteArray.size) {
                    val length = minOf(chunkSize, randomByteArray.size - offset)
                    outputStream.write(randomByteArray, offset, length)
                    outputStream.flush() // Ensure it's sent promptly
                    offset += length
                }

                val inputStream = realSocket.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                bufferedReader.readLine()
            } else {
                val inputStream = realSocket.inputStream

                // ✅ Read incrementally until buffer is full
                var bytesRead = 0
                while (bytesRead < readByteArray.size) {
                    val read =
                        inputStream.read(readByteArray, bytesRead, readByteArray.size - bytesRead)
                    if (read == -1) throw IOException("Stream closed early")
                    bytesRead += read
                }

                val outputStream = realSocket.outputStream
                outputStream.write("ACK\n".toByteArray())
                outputStream.flush()
            }*/

        val outputStream = CountingOutputStream(realSocket.outputStream)
        for (url in urlsToExchange.map { page -> page.url }) {
            outputStream.write("${url}\n".toByteArray())
        }
        outputStream.write("0\n".toByteArray())

        for (url in requests) {
            outputStream.write((Json.encodeToString<Request>(url) + "\n").toByteArray())
        }
        outputStream.write("0\n".toByteArray())

        val inputStream = realSocket.inputStream
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        while (true) {
            val line = bufferedReader.readLine()
            if (line == "0") break
            pushToUpdates("Network stores ${line}")
            Log.d("Ross", "Other saved")
            emit(BluetoothEmit.OtherSaved(line))
            Log.d("Ross", "Done")
        }

        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        while (true) {
            val line = bufferedReader.readLine()
            if (line == "0") break
            var request = Json.decodeFromString<Request>(line)
            pushToUpdates("Other wants ${line}")
            if (urlsToExchange.any { page -> page.url == request.url && page.cachedByMe }) {
                pushToUpdates("Writing ${line}")
                outputStream.write("1\n".toByteArray())


                val filteredUrl = request.url.replace("https://", "").replace("/", "_")
                val f: File = File(downloadsDir, "cachetothefuture/${filteredUrl}.html")

                Log.d("Ross", "writing")
                Log.d("Ross", (f.readText().replace("\n", "") + "\n"))
                outputStream.write((f.readText().replace("\n", "") + "\n").toByteArray())
            } else {
                outputStream.write("0\n".toByteArray())
                request.hops += 1
                if (request.hops <= 20) {
                    emit(BluetoothEmit.UnsatisfiedRequest(request))
                }
            }
        }
        pushToUpdates("Writing ACK")
        outputStream.write("ACK\n".toByteArray())
        var whichRequest = -1
        while (true) {
            val line = bufferedReader.readLine()
            pushToUpdates(line)
            if (line == "ACK") break
            whichRequest += 1
            if (line == "0") continue
            pushToUpdates("Reading File")
            val fileText = bufferedReader.readLine()
            val receivedRequest = requests[whichRequest]
            val filteredUrl = receivedRequest.url.replace("https://", "").replace("/", "_")
            val f: File = File(downloadsDir, "cachetothefuture/${filteredUrl}.html")
            f.writeText(fileText)
            val metadataFile = File(downloadsDir, "cachetothefuture/${filteredUrl}.metadata")
            metadataFile.writeText(
                Json.encodeToString<FileMetadata>(
                    FileMetadata(
                        receivedRequest.url,
                        filteredUrl + ".html",
                        generateSHA256Checksum(fileText)
                    )
                )
            )
        }

        pushToUpdates("Finished reading request responses, writing ACK")

        outputStream.write("ACK\n".toByteArray())

        val ackLine = bufferedReader.readLine()
        if (ackLine == "ACK") {
            pushToUpdates("Acknowledgement from other to close")
        } else {
            pushToUpdates("Did not get acknowledgement from other to close")
        }
        Log.d("Ross", outputStream.byteCount.toString())

        //pushToUpdates("Time: $timeTaken")

        //emit(BluetoothEmit.Time(timeTaken))

        realSocket.close()
        pushToUpdates("Closing socket")
    }
}