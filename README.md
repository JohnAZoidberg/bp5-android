# BusPirate BPIO2 Android App

This is an Android App that connects to the BusPirate (5 or 6) over USB-C and uses the BPIO2 protocol to control it.
The BPIO2 protocol is documented and has libraries, which are added as a submodule in @BusPirate-BPIO2-flatbuffer-interface

The App expects the user to have manually configured the BusPirate in BPIO2 binmode.

The BusPirate firmware is also added as a submodule for reference: @BusPirate5-firmware

When the app launches it connects to the BusPirate and shows current status (firmware version, current mode) in a single line or two.

## Features

Below the status information there are two toggle buttons:
- **UART** - Enable or disable UART mode on the BusPirate.
- **PSU** - Enable or disable the onboard power supply (3.3V/300mA). This is independent of UART mode so you can use an external VREF instead.

When UART is enabled, every received character is displayed in a scrolling (readonly) textbox.
At the very bottom there's a text input field where the user can type commands and submit them with a button next to it.

### Detailed scenarios

- The app needs to handle and correctly show current connection status of the buspirate
    - The buspirate can be plugged in and authenticated (by Android) at any time - both before the app launched and while it's running.
    - The buspirate can be unplugged at any time as well
    - On connect the connection text, UART, and PSU buttons should show the correct status

## Source Code

The app is written in Kotlin and conforms to the regular Android coding practices. ktlint should be followed.
The target is Android 15 and up.

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

### CI

Github actions checks all the lints and builds the application and uploads the APK for easy access.

### More Android debugging

```
# To wirelessly debug (Enable in the Android settings first and get the one time code there)
adb pair 192.168.1.185:44845

# View live logs of this app
adb logcat -e com.buspirate.bpio
```

## Firmware

Testing that firwmare works with loopback UART:

```
> cd BusPirate-BPIO2-flatbuffer-interface/
> nix-shell ../python-shell.nix --run "python python/uart_example.py -p /dev/ttyACM1 --mode buffered"
```
