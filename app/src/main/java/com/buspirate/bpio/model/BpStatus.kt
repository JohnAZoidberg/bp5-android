package com.buspirate.bpio.model

data class BpStatus(
    val firmwareVersion: String,
    val hardwareVersion: String,
    val currentMode: String,
)
