package com.example.ebaylister

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ebaylister.ui.EbayListerApp
import com.example.ebaylister.ui.theme.EbayListerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EbayListerTheme {
                EbayListerApp()
            }
        }
    }
}
