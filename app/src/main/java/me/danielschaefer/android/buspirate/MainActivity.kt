package me.danielschaefer.android.buspirate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import me.danielschaefer.android.buspirate.settings.PinSettings
import me.danielschaefer.android.buspirate.ui.MainScreen
import me.danielschaefer.android.buspirate.ui.theme.BusPirateBPIOTheme
import me.danielschaefer.android.buspirate.usb.UsbSerialManager
import me.danielschaefer.android.buspirate.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        usbSerialManager = UsbSerialManager(this)
        viewModel.setUsbManager(usbSerialManager)
        viewModel.setPinSettings(PinSettings(this))
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
