package com.buspirate.bpio.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbSerialManager(private val context: Context) {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.buspirate.bpio.USB_PERMISSION"
        private const val VENDOR_ID = 0x1209
        private const val PRODUCT_ID = 0x7331
        private const val BPIO_PORT_INDEX = 1
        private const val BAUD_RATE = 115200
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val prober =
        UsbSerialProber(
            ProbeTable().addProduct(VENDOR_ID, PRODUCT_ID, CdcAcmSerialDriver::class.java),
        )

    private val permissionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    permissionCallback?.invoke(granted)
                    permissionCallback = null
                }
            }
        }

    fun connect(onResult: (Result<Unit>) -> Unit) {
        val drivers = prober.findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            onResult(Result.failure(Exception("BusPirate not found")))
            return
        }

        val driver = drivers[0]
        if (driver.ports.size <= BPIO_PORT_INDEX) {
            onResult(Result.failure(Exception("BPIO interface not available")))
            return
        }

        val device = driver.device
        if (usbManager.hasPermission(device)) {
            openPort(driver.ports[BPIO_PORT_INDEX], device, onResult)
        } else {
            requestPermission(device) { granted ->
                if (granted) {
                    openPort(driver.ports[BPIO_PORT_INDEX], device, onResult)
                } else {
                    onResult(Result.failure(Exception("USB permission denied")))
                }
            }
        }
    }

    private fun requestPermission(
        device: UsbDevice,
        callback: (Boolean) -> Unit,
    ) {
        permissionCallback = callback
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun openPort(
        serialPort: UsbSerialPort,
        device: UsbDevice,
        onResult: (Result<Unit>) -> Unit,
    ) {
        try {
            val connection =
                usbManager.openDevice(device)
                    ?: throw Exception("Failed to open USB device")
            serialPort.open(connection)
            serialPort.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = serialPort
            onResult(Result.success(Unit))
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun write(data: ByteArray) {
        port?.write(data, 1000)
    }

    fun read(buffer: ByteArray): Int = port?.read(buffer, 100) ?: 0

    fun disconnect() {
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: Exception) {
        }
    }

    val isConnected: Boolean get() = port != null
}
