package me.danielschaefer.android.buspirate.model

data class BpStatus(
    val firmwareVersion: String,
    val hardwareVersion: String,
    val currentMode: String,
    val psuEnabled: Boolean,
)
