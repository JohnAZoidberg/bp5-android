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
- **EC Flash** - Flash NPCX EC firmware using the UUT (UART Update Tool) protocol. Select an ec.bin firmware file, and the app handles the full flash sequence: entering flash mode via GPIO, syncing with the EC, detecting the chip, uploading the embedded `npcx_monitor.bin`, flashing firmware in 4KB segments, and rebooting. Progress is shown throughout.

#### EC Flash Wiring

```
BP5 IO0      --> EC VCC1_RST              (reset, active low)
BP5 IO1      --[4.7-10K R]--> EC CR_SOUT1/FLPRG1  (flash mode strap)
BP5 IO4 (TX) --> EC CR_SIN1               (UART RX)
BP5 IO5 (RX) <-- EC CR_SOUT1             (UART TX)
BP5 GND      --- EC GND
```

IO1 and IO5 connect to the same EC pin (CR_SOUT1/FLPRG1). IO1 goes through a 4.7-10K resistor for the FLPRG1 strap pulldown.

#### EC Flash UI

The flash section sits between the UART/PSU buttons and the log area. It has the following states:

- **Idle** -- Shows a "Select ec.bin" button and the selected filename. Below that is a "Flash EC" button (enabled only when connected and a file is selected). The UART and PSU buttons are disabled while a flash is in progress.
- **In progress** -- Displays the current step (Entering flash mode, Syncing, Detected chip, Uploading monitor, Flashing with percentage, Rebooting) with a progress bar. Flashing shows a determinate progress bar; other steps show an indeterminate one. A Cancel button aborts the operation.
- **Done** -- Shows "Flash complete!" with a Done button to return to idle.
- **Error** -- Shows the error message with a Dismiss button.

When UART is enabled, every received character is displayed in a scrolling (readonly) log.
The log has share and clear buttons — share opens the Android share sheet to send the full log to another app, and clear empties it.
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
