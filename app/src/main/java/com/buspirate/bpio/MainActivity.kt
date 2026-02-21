package com.buspirate.bpio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.buspirate.bpio.ui.theme.BusPirateBPIOTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BusPirateBPIOTheme {
            }
        }
    }
}
