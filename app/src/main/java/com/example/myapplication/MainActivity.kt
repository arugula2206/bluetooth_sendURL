/**
 * Bluetoothデバイス(PC)に接続し、chromeからURLを受信して送信する
 */
package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        private const val REQUEST_BLUETOOTH_CONNECT = 100 //複数のパーミッションをリクエストするときに、判別するために設定
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_ADDRESS = "xx:xx:xx:xx:xx:xx"  // Python側(PC)のMACアドレス
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected: Boolean = false
    private lateinit var urlReceiver: BroadcastReceiver
    private var receivedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetoothが利用できません", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        requestBluetoothPermissions() //Bluetoothパーミッションをリクエスト
        handleIncomingIntent(intent) //インテントの処理

        // URLを受け取るためのBroadcastReceiverの設定
        urlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val url = intent?.getStringExtra("url")
                Log.d("MainActivity", "Received URL: $url")
                receivedUrl = url
            }
        }

        // インテントフィルタを設定してURLを受け取るレシーバーを登録
        val filter = IntentFilter("com.example.myapplication.SEND_URL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(urlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(urlReceiver, filter)
        }

        // Sendボタンの設定
        val sendUrlButton: Button = findViewById(R.id.sendUrlButton)
        sendUrlButton.setOnClickListener {
            receivedUrl?.let { url ->
                showConfirmationDialog(url)
            } ?: run {
                Toast.makeText(this, "URLが受信されていません", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Bluetoothのパーミッションをリクエスト
    // パーミッションが許可されている場合はBluetooth接続のセットアップを行う
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

    // Bluetooth接続をセットアップ
    // Bluetoothのパーミッションが許可されている場合は指定されたデバイスに接続
    private fun setupBluetoothConnection() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(DEVICE_ADDRESS)
                if (device != null) {
                    connectToDevice(device)
                } else {
                    Toast.makeText(this, "デバイスが見つかりません", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this, "Bluetoothの権限が必要です", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Bluetoothの権限が必要です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //指定されたBluetoothデバイスに接続
    // 接続に成功した場合はBluetoothが接続されたことをユーザーに通知
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                isConnected = true
                runOnUiThread {
                    Toast.makeText(this, "Bluetoothに接続しました", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Bluetoothの権限が必要です", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this, "デバイスへの接続に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //受信したIntentを処理
    //受信したIntentがテキストの場合はURLを取得し、受信したURLを保持
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT)
            url?.let {
                Log.d("MainActivity", "Received URL: $it")
                receivedUrl = it
            }
        }
    }

    //URLの確認ダイアログを表示
    //ユーザーがURLの送信を許可した場合はURLを送信
    private fun showConfirmationDialog(url: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("URLの確認")
        builder.setMessage("このURLを送信しますか？\n$url")
        builder.setPositiveButton("送信") { _, _ ->
            sendUrl(url)
        }
        builder.setNegativeButton("キャンセル") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    //指定されたURLをBluetooth経由で送信
    //送信に成功した場合はユーザーに通知
    private fun sendUrl(url: String) {
        if (isConnected && bluetoothSocket != null) {
            try {
                Log.d("MainActivity", "Sending URL: $url")
                bluetoothSocket?.outputStream?.write(url.toByteArray())
                runOnUiThread {
                    Toast.makeText(this, "URLを送信しました: $url", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "URLの送信に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Bluetoothが接続されていません", Toast.LENGTH_SHORT).show()
        }
    }

    // バックグラウンド上でもURLを受け取れるようにonNewIntentをoverride
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    //アクティビティが破棄されたとき、ソケットやレシーバーが残らないようにonDestroyをoverride
    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Failed to close Bluetooth socket", e) //Bluetooth socketを閉じられなかったとき（エラー）
        }
        try {
            unregisterReceiver(urlReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Receiver not registered", e) //URLを受け取るレシーバーの解放の失敗したとき（警告）
        }
    }
}
