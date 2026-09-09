package com.example.cachetothefuture.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cachetothefuture.MainActivity
import com.example.cachetothefuture.PeerDiscoveryService

@Composable
fun NetworkScreen(
    isSupported: Boolean,
    isEnabled: Boolean,
    networkUpdates: List<String>,
    onClick: () -> Unit,
    onL2capTest: () -> Unit = {}
) {
    val sharedPref = LocalContext.current.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)
    val sharedPrefVal = sharedPref.getString("mac", "") ?: ""
    var mac by remember { mutableStateOf(sharedPrefVal) }
    var serviceRunning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(networkUpdates.size) {
        if (networkUpdates.isNotEmpty()) {
            listState.animateScrollToItem(networkUpdates.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Network Screen", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                TextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("Enter Bluetooth MAC") },
                    maxLines = 1,
                    textStyle = TextStyle(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        with(sharedPref.edit()) {
                            putString("mac", mac)
                            apply()
                        }
                    })
                )
            }
            item {
                Button(onClick = {
                    val intent = Intent(
                        context,
                        PeerDiscoveryService::class.java
                    )
                    intent.action = if (serviceRunning) "stop" else "start"
                    startForegroundService(context, intent)
                    serviceRunning = !serviceRunning
                }) {
                    Text(if (serviceRunning) "End Service" else "Start Service")
                }
            }
            item {
                Button(onClick = onClick) { Text("Search for peers") }
            }
            item {
                Button(onClick = onL2capTest) { Text("L2CAP Throughput Test") }
            }
            item {
                Text("Bluetooth is ${if (isSupported) "" else "not "}supported")
            }
            item {
                Text("Bluetooth is ${if (isEnabled) "" else "not "}enabled")
            }
            items(networkUpdates) { text ->
                Text(text)
            }
        }
    }
}

@Composable
fun NetworkScreen(viewModel: NetworkViewModel = viewModel(factory = NetworkViewModel.Factory)) {
    LocalContext.current as MainActivity
    val isEnabled = viewModel.isBluetoothEnabled().collectAsState()
    val networkUpdates = viewModel.getNetworkUpdates().collectAsState()
    val context = LocalContext.current
    NetworkScreen(
        isSupported = viewModel.isBluetoothSupported(),
        isEnabled = isEnabled.value,
        networkUpdates = networkUpdates.value,
        onClick = { viewModel.connectAndExchange(context) },
        onL2capTest = { viewModel.testL2capThroughput() })
}

@Preview(showBackground = true, widthDp = 320)
@Composable
fun NetworkScreenPreview() {
    NetworkScreen(isSupported = true, isEnabled = true, listOf("Update 1", "Update 2"), {})
}