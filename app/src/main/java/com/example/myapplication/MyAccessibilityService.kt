package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == "com.android.chrome") {
            val text = event.text.toString()
            if (text.contains("http")) {
                val url = text.substring(text.indexOf("http"))
                Log.d("MyAccessibilityService", "URL: $url")
                sendUrl(url)
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    private fun sendUrl(url: String) {
        val intent = Intent("com.example.myapplication.SEND_URL")
        intent.putExtra("url", url)
        sendBroadcast(intent)
    }
}
