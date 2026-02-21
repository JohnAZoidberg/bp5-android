package com.buspirate.bpio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.buspirate.bpio.ui.MainScreen
import com.buspirate.bpio.ui.theme.BusPirateBPIOTheme
import com.buspirate.bpio.usb.UsbSerialManager
import com.buspirate.bpio.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbSerialManager = UsbSerialManager(this)
        viewModel.setUsbManager(usbSerialManager)
        viewModel.connect()
        setContent {
            BusPirateBPIOTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.retryIfPending()
    }
}
