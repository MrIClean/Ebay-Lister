package com.example.ebaylister

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.ebaylister.ui.EbayListerApp
import com.example.ebaylister.ui.theme.EbayListerTheme

class MainActivity : ComponentActivity() {
    private val connectionDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionDeepLink.value = intent?.data
        setContent {
            EbayListerTheme {
                EbayListerApp(connectionDeepLink = connectionDeepLink.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        connectionDeepLink.value = intent.data
    }
}
