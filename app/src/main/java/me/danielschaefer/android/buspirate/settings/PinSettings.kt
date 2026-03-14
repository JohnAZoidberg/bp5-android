package me.danielschaefer.android.buspirate.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.danielschaefer.android.buspirate.flash.EC_BOOT_PIN
import me.danielschaefer.android.buspirate.flash.EC_RST_PIN

class PinSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pin_settings", Context.MODE_PRIVATE)

    private val _rstPin = MutableStateFlow(prefs.getInt(KEY_RST_PIN, EC_RST_PIN))
    val rstPin: StateFlow<Int> = _rstPin.asStateFlow()

    private val _bootPin = MutableStateFlow(prefs.getInt(KEY_BOOT_PIN, EC_BOOT_PIN))
    val bootPin: StateFlow<Int> = _bootPin.asStateFlow()

    fun save(rstPin: Int, bootPin: Int) {
        prefs.edit()
            .putInt(KEY_RST_PIN, rstPin)
            .putInt(KEY_BOOT_PIN, bootPin)
            .apply()
        _rstPin.value = rstPin
        _bootPin.value = bootPin
    }

    companion object {
        private const val KEY_RST_PIN = "rst_pin"
        private const val KEY_BOOT_PIN = "boot_pin"
    }
}
