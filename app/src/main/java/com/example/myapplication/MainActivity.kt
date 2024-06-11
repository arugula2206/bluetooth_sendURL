package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.*
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 100
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_ADDRESS = "38:D5:7A:02:47:C2"  // Python側(PC)のMACアドレスを記入しましょう！
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected: Boolean = false
    private lateinit var urlReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        requestBluetoothPermissions()
        handleIncomingIntent(intent)

        // BroadcastReceiverの設定
        urlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val url = intent?.getStringExtra("url")
                Log.d("MainActivity", "Received URL: $url")
                url?.let {
                    sendUrl(it)
                }
            }
        }

        // インテントフィルタを設定してレシーバーを登録
        val filter = IntentFilter("com.example.myapplication.SEND_URL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(urlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(urlReceiver, filter)
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT
            )
        } else {
            setupBluetoothConnection()
        }
    }

    private fun setupBluetoothConnection() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                val device = pairedDevices?.firstOrNull { it.address == DEVICE_ADDRESS }
                if (device != null) {
                    connectToDevice(device)
                } else {
                    startDiscovery()
                }
            } catch (e: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                isConnected = true
                runOnUiThread {
                    Toast.makeText(this, "Bluetooth connected", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDiscovery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(receiver, filter)
        } else {
            runOnUiThread {
                Toast.makeText(this, "Bluetooth scan permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    if (it.address == DEVICE_ADDRESS) {
                        connectToDevice(it)
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothAdapter?.cancelDiscovery()
                        }
                        unregisterReceiver(this)
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT)
            url?.let {
                Log.d("MainActivity", "Received URL: $it")
                sendUrl(it)
            }
        }
    }

    private fun sendUrl(url: String) {
        if (isConnected && bluetoothSocket != null) {
            try {
                Log.d("MainActivity", "Sending URL: $url")
                bluetoothSocket?.outputStream?.write(url.toByteArray())
                runOnUiThread {
                    Toast.makeText(this, "URL sent: $url", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to send URL", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Bluetooth is not connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            //
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        try {
            unregisterReceiver(urlReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    setupBluetoothConnection()
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth permission is required",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
