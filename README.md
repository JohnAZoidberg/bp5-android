# BusPirate BPIO2 Android App

This is an Android App that connects to the BusPirate (5 or 6) over USB-C and uses the BPIO2 protocol to control it.
The BPIO2 protocol is documented and has libraries, which are added as a submodule in @BusPirate-BPIO2-flatbuffer-interface

The App expects the user to have manually configured the BusPirate in BPIO2 binmode.

The BusPirate firmware is also added as a submodule for reference: @BusPirate5-firmware

When the app launches it connects to the BusPirate and shows current status (firmware version, current mode) in a single line or two.

## Features

Below the status information it has two buttons to enable or disable UART.
When UART is enabled, it sends commands to the BP to enable UART and to receive and display everything in a scrolling (readonly) textbox.
At the very bottom there's a text input field where the user can type commands and submit them with a button next to it.

## Source Code

The app is written in Kotlin and conforms to the regular Android coding practices. ktlint should be followed.

### Building

```
# Requires Android SDK and JDK 17
./gradlew assembleDebug
```

### Building on NixOS

Building is done using Nix flakes to get an environment:

```
# Enter development shell
nix develop

# Build (Debug or Release)
build-apk assembleDebug
build-apk assembleRelease

# Check device is connected
nix develop --command adb devices

# Install on connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
