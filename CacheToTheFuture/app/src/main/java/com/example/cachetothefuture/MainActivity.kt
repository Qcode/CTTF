package com.example.cachetothefuture

import android.bluetooth.BluetoothAdapter
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.cachetothefuture.ui.HomeScreen
import com.example.cachetothefuture.ui.NetworkScreen
import com.example.cachetothefuture.ui.theme.CacheToTheFutureTheme


class MainActivity : ComponentActivity() {
    lateinit var bluetoothBroadcastReceiver: BluetoothBroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothBroadcastReceiver =
            BluetoothBroadcastReceiver((application as MyApplication).container.bluetoothRepository)

        ActivityCompat.requestPermissions(
            this, arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ), 0
        )

        setContent {
            CacheToTheFutureTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MyApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothBroadcastReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothBroadcastReceiver)
    }
}


@Preview(widthDp = 320, showBackground = true)
@Composable
fun MyAppPreview() {
    CacheToTheFutureTheme {
        MyApp()
    }
}

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    HOME("Home", Icons.Default.Home, "Home"),
    NETWORK("Network", Icons.Default.CellTower, "Network")
}

@Composable
fun MyApp(
    modifier: Modifier = Modifier
) {
    val topPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach {
                item(
                    icon = {
                        Icon(it.icon, contentDescription = it.contentDescription)
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Surface(modifier
            .padding(top = topPadding, start = 24.dp, end = 24.dp)
            .fillMaxSize()) {
            when (currentDestination) {
                AppDestination.HOME -> HomeScreen()
                AppDestination.NETWORK -> NetworkScreen()
            }
        }
    }
}