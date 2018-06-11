# Chorus RF Laptimer

<img src="docs/img/logo.png" align="left" alt="Logo" width="200"/>

***Chorus*** [ˈkɔr əs] - *1. (Music) a group of persons singing in unison.*

**NOTE: This project is currently in a "public beta" stage. Changes to schematic and software are very likely. Feel free to contribute and/or share your usage results to improve the project.**

This is a VTX Radio Frequency Lap Timing solution for drone racers - the evolution of a [Solo DIY RF Laptimer project](https://github.com/voroshkov/Solo-DIY-RF-Laptimer).
Several updated Solo Laptimer devices connected together make up a Chorus Laptimer device which is capable of tracking several drones at once.
This is a "lightweight" alternative to IR lap timing systems having the advantage that it does not require any additional equipment on drones except VTX.
## HOT NEWS !
------------------------------------------------
- 2017-11: iOS app is live on AppStore: https://itunes.apple.com/app/id1296647206. All credits go to Lazar Djordjevic!
- 2017-10: WiFi modules (DT-06) are now supported as an alternative to Bluetooth connection! (still not sure how stable the connection will be with these modules, so use at your own risk and report if you discover any odd behavior with them)
- 2017-10: Android app is now available on Google Play only. The source code remains here.
------------------------------------------------

## Support the project:
If you'd like to support the project, please contribute. This is the main benefit of the open-source ideology.

If you feel like spending some money for the purspose - I'd also be very grateful for that.

<img src="https://www.paypalobjects.com/webstatic/mktg/Logo/pp-logo-100px.png" alt="PayPal logo"/> - [PayPal Me](https://paypal.me/VOROSHKOV)

<img src="https://en.bitcoin.it/w/images/en/c/cb/BC_Logotype.png" alt="Bitcoin logo" width=100/> - 1KHfQpKfSFZtc9c7b9ZPSsX3uqRcXgJuow

Thanks for your support!

## Featured references:
- Chorus RF Laptimer Facebook group: https://www.facebook.com/groups/ChorusRFLaptimer/
- Chorus RF Laptimer discussion thread @ RC Groups: https://www.rcgroups.com/forums/showthread.php?2801815
- PC GUI Interface project for Chorus RF Laptimer: https://github.com/anunique/ChorusGUI
- Delta5 Race Timer Facebook group: https://www.facebook.com/groups/Delta5RaceTimer/

## Add-ins/Extensions:

- LED Finish Gate Module: https://github.com/voroshkov/Chorus-LED-Module
- Chorus RSSI Monitoring Android app (paid): https://play.google.com/store/apps/details?id=app.andrey_voroshkov.chorus_monitor

## Contents

<!-- MarkdownTOC depth=0 bracket="round" autolink="true" autoanchor="true" -->

- [Terminology](#terminology)
- [Features](#features)
- [Limitations](#limitations)
- [How it works](#how-it-works)
- [Hardware](#hardware)
    - [Used parts:](#used-parts)
    - [Bluetooth module setup](#bluetooth-module-setup)
    - [WiFi module setup](#wifi-module-setup)
    - [RX5808 SPI patch \(required\)](#rx5808-spi-patch-required)
    - [Wiring of a Solo device](#wiring-of-a-solo-device)
    - [Schematic and PCB](#schematic-and-pcb)
    - [Assembly of a Solo device](#assembly-of-a-solo-device)
    - [Assembly of a Chorus device](#assembly-of-a-chorus-device)
- [Software](#software)
    - [Arduino](#arduino)
    - [Android App](#android-app)
    - [iOS App](#ios-app)
        - [App User Guide](#app-user-guide)
- [Setup and Usage Guide](#setup-and-usage-guide)
- [Troubleshooting](#troubleshooting)
- [Contributors](#contributors)
- [Contacts](#contacts)

<!-- /MarkdownTOC -->

<a name="terminology"></a>
## Terminology

**Solo** or **Chorus node** - device for tracking a single drone. Parts cost about $12. Consists of Arduino Pro mini, RX5808 module, connectors, optional buzzer, optional resistors:

<img src="docs/img/Solo_device.png" alt="Solo device" height="400"/>

**Chorus** - several (2+) connected Solo devices (nodes):

<img src="docs/img/Chorus_device.png" alt="Chorus device" height="400"/>

<a name="features"></a>
## Features
- No additional equipment besides 5.8GHz Video Transmitter required on a drone.
- Measure lap times with 1ms resolution.
- Android application for controlling the device via Bluetooth or WiFi.
- 5V * 250 mA power consumption (per device)
- Low cost (around $16 per device, excluding power supply), compared to similar solutions available on market.
- Can be tuned to any RF band/channel (R, A, B, E, F, D and even a bit of Connex)
- Monitors several frequencies simultaneously (corresponding to a number of devices)
- Expandable: make one Solo device and track your solo flight times; make more devices, connect them into a Chorus and compete with teammates
- Automatic detection of a number of Solo devices in a Chorus
- Spoken notifications, including lap results
- Adjustable LiPo battery monitoring and spoken notifications of low battery

<a name="limitations"></a>
## Limitations
- Tracks up to 100 laps per heat.
- Limited support for digital VTx equipment (Connex)
- Although expandable, definitely has a physical limit on a number of stacked devices (depending on UART throughput of the last device in a chain)
- Software for iOS is on the way! (thanks to contributors)

<a name="how-it-works"></a>
## How it works
Each Solo device measures a VTx signal strength (RSSI value) and compares it with a threshold set up. If the RSSI value is above the threshold, the corresponding drone is considered passing a finish gate.

<a name="hardware"></a>
## Hardware

<a name="used-parts"></a>
### Used parts:
 - RX5808 (with SPI patch) (**N** items)
 - Arduino Pro Mini **5V 16MHz** or Nano v.3.0 (**N** items)
 - HC-06/HC-05 (HM-10 for iOS) Bluetooth module (**1** item)
 - DT-06 Geekcreit WiFi module as an alternative to Bluetooth module ([@Banggood](https://www.banggood.com/Geekcreit-DT-06-Wireless-WiFi-Serial-Port-Transparent-Transmission-Module-TTL-To-WiFi-p-1141047.html)) (**1** item)
 - 5V power supply (for example 2-4S LiPo with 5V BEC) (**1** item)
 - Piezo buzzer (5V, without built-in generator) - optional (**N** items)
 - 2 Resistors (1K and 10K) for LiPo Voltage monitoring - optional (**N** items)

<a name="bluetooth-module-setup"></a>
### Bluetooth module setup

Make sure your bluetooth module baud rate is set to 115200 (use any of numerous tutorials on internet).

Generalized steps:

1. Connect HC-06 -> USB-UART Adapter -> PC
2. Open Arduino IDE, set adapter's COM port, run Serial Monitor
3. Send command: "AT+BAUD8" (module replies "OK115200")

You might also like to change BT device name and default PIN (which is "1234") using commands "AT+NAMEdevicename" and "AT+PINxxxx" respectively.

<a name="wifi-module-setup"></a>
### WiFi module setup
Geekcreit DT-06 modules by default operate as WiFi access point with IP **192.168.4.1**. So open your browser, connect to http://192.168.4.1 and set up as follows:

**Don't forget to save each page after making the changes!**

Change Baud Rate to 115200, Serial Split Timeout(ms) to 10, leave other settings as shown:

<img src="docs/img/esp_Serial.png" alt="Serial page setup">

On the WiFi page you may specify the name for your WiFi network and set a password:

<img src="docs/img/esp_Wifi.png" alt="WiFi page setup">

On the Networks page you should select UDP Server and make sure the UDP port is set to 9000:

<img src="docs/img/esp_Networks.png" alt="Networks page setup">

Make sure to restart the module to apply the changes. They'll remain until you "Restore" the module to defaults.

<img src="docs/img/esp_Restart.png" alt="Restart module">


<a name="rx5808-spi-patch-required"></a>
### RX5808 SPI patch (required)
(copied from [sheaivey/rx5808-pro-diversity](https://github.com/sheaivey/rx5808-pro-diversity) repo)

In order to get the RX5808 to use SPI you will need to open it and remove a single SMD resistor.

<img src="docs/img/rx5808-new-top.jpg" alt="RX5808 spi patch" width="500" >

For older versions of RX5808 use [these instructions](https://github.com/markohoepken/rx5808_pro_osd/wiki/rs5808-spi-patch).

<a name="wiring-of-a-solo-device"></a>
### Wiring of a Solo device
Parts may be connected directly without using any additional components:

**UPDATE:** powering RX5808 from Arduino's VCC was a bad idea - Pro Mini's linear regulator might not be able to provide enough power for RX, so use raw 5V power instead.

**UPDATE #2:** powering Arduino via RAW pin is also a bad idea if you use external stabilized 5V power supply - Arduino's internal voltage regulator might significantly drop voltage thus making LiPo monitor calculations wrong. Supply 5V power to VCC pin instead (as shown on the updated wiring diagram).

Note the resistor divider for LiPo Battery monitoring. Although just one of the Solo devices in a Chorus should be connected to LiPo battery for monitoring, but make sure to have A0 Arduino pins on all other Solo devices connected to Ground (via 1K resistor) or just solder the resistors on each Solo device according to schematic.

<img src="docs/img/wiring_solo.png" alt="Wiring Solo schematic" width="400">

It seems to work fine being connected this way, however adding 100 Ω resistors in line on SPI wires (Arduino pins 10, 11, 12) is a good idea to prevent possible glitches with channel selection:

<img src="docs/img/wiring_resistors.png" alt="Wiring with resistors" width="">

<a name="schematic-and-pcb"></a>
### Schematic and PCB
Schematic and PCB design in DipTrace format are available in the **DipTrace** folder.

<img src="docs/img/Schematic.png" alt="Schematic" width="400">

<img src="docs/img/pcb_voroshkov.png" alt="Printed Circuit Board" width="200">

[**PCB by Joao Reis**](contributors/PCB/Joao_Reis_PCB_Project_v1.1.zip) (Gerber format, SeedStudio compatible):

<img src="docs/img/pcb_reis.png" alt="PCB by Joao Reis" width="200">

<a name="assembly-of-a-solo-device"></a>
### Assembly of a Solo device
Correct positioning of RX5808 module against the finish gate area is vital for correct measurements.

I tried using different types of antennas and shields with RX5808 to achieve the best possible accuracy, and finally found that the module itself works as a short-range directional antenna. The non-shielded side of the module is a surface that should face the gate, so consider this fact upon assembling.

<a name="assembly-of-a-chorus-device"></a>
### Assembly of a Chorus device

1. Make several Solo devices.
2. Connect them together.
3. Connect a Bluetooth module to the last Solo device in a chain.
4. Use a jumper on the first Solo device to shorten two upper pins.
5. Attach 5V power supply to one of the Solo devices (make sure to supply enough power - each Solo device consumes about 250mA).
6. Optionally attach LiPo battery to one of the solo devices that has a resistor divider for LiPo Monitoring feature

<img src="docs/img/chorus_assembly.png" alt="Assembly of a Chorus Device" width="900">

<a name="software"></a>
## Software
<a name="arduino"></a>
### Arduino
Download the project from Arduino folder, open **chorus-rf-laptimer.ino** file with Arduino IDE and upload to each Solo device.

<a name="android-app"></a>
### Android App
The app is now available on Google Play.

[![Get the Android app](docs/img/google-play-badge.png)](https://play.google.com/store/apps/details?id=app.andrey_voroshkov.chorus_laptimer&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

<a name="ios-app"></a>
### iOS App
Lazar Djordjevic is the creator of the iOS app. Thanks, Lazar!

The app is available on App Store.

[![Get the iOS app](docs/img/appstore_badge.png)](https://itunes.apple.com/app/id1296647206)

**Note for iOS users**: Apple devices don't work with HC-05/06 modules. You should use **HM-10** Bluetooth module instead. (Or wait for WiFi modules support in iOS version :))

<a name="app-user-guide"></a>
#### App User Guide
Android app is used as the illustration for the guide.

Application startup screen:

<img src="docs/img/androidAppStartup.png" alt="Application startup screen" width="350">

Use "⋮" menu to connect/disconnect to your Chorus device.

<img src="docs/img/androidAppConnect.png" alt="Application connect" width="350">

Connection is available via Bluetooth or WiFi, depending on the connectivity module you use for your Chorus.

**Bluetooth** connection assumes that Bluetooth is enabled and the Bluetooth module is paired with the phone.

**WiFi** connection assumes that the module operates in Access Point mode (AP), UDP server is up and running on port 9000, and you are connected directly to its WiFi network.

Once connected to Chorus device, the app automatically detects a number of stacked Solo devices and shows corresponding controls.

The app consists of 4 tabs:

- **SETUP** - race preconditions and device settings:
- **FREQ** - VTX channel/band for each Solo device
- **PILOTS** - Pilot name and RSSI threshold for each channel
- **RACE** - start/stop race and race results

Controls on the tabs are mostly self-explanatory. Still some clarifications might be useful:

- **Enable device sounds**: tick to enable device buzzers.
- **Minimal Lap Time**: use +/- to increase/decrease minimal lap time. Set enough time to let a drone leave the "above-threshold RSSI area" after lap time is captured.
- **Skip first lap**: tick if start table is located before the start/finish gate (first lap time will be skipped because it's not a full lap); untick if start table is located right behind the laptimer (first lap time will be tracked only if minimal lap time is passed after the race start).
- **RSSI Threshold**: use +/- to fine-tune RSSI threshold.
- **Set/Clear**: long tap to capture/clear currently measured RSSI value as a threshold.
- **Calibrate Timers**: different Arduino devices have different oscillators accuracy and it may slightly deviate from 16MHz. In order to make sure that same timespan is measured equally on different devices, you need to calibrate them before the race.
- **Start Race**: tap to start tracking laps. This same button is used to Stop the race.

When you stop the race, Chorus device immediately clears saved lap times, but they remain visible in the application until new race is started. Also the Race data is saved in CSV reports on your device (if you grant the Write to SD Card permissions)

**LiPo Monitoring** feature has a "hidden" possibility for adjustment. If voltage measured by LiPo Monitor in Android App doesn't correspond to real voltage of your battery, perform a long tap on a voltage value to see the Adjustment controls:

<img src="docs/img/androidAppLipoMonitor.png" alt="LiPo Monitor" width="350">

<img src="docs/img/androidAppLipoAdjust.png" alt="LiPo Monitor Adjustment" width="350">

Adjust until measured voltage corresponds to the voltage of your LiPo battery while it's powering the Chorus device.

<a name="setup-and-usage-guide"></a>
## Setup and Usage Guide
 1. Power on the Chorus device and put it on the ground in the middle of the finish gate facing upwards.
 2. Start the mobile app and connect to the Chorus device.
 3. Setup VTX Band/Channel for each Solo device in Android app (on the "Freq" tab)
 4. Fully prepare racing drones and power them up (VTX must be powered in racing mode).
 5. Take a powered drone a bit above the gate.
 6. Capture current RSSI value as a threshold using the Android app (use "Set" button for appropriate channel on "Pilots" tab).
 7. Repeat steps 5,6 for each drone taking part in a race.
 8. Calibrate timers using the corresponding button on a "Race" tab (in case you have more than 1 Solo device).
 6. Start Race in the app.
 7. Fly the track and see the lap times being captured.

<img src="docs/img/placementAndSetup.png" alt="Device placement and setup" width="">

Also consider shielding the Chorus device with a piece of metal on one side where drones are approaching from. It might increase the accuracy by partially blocking the VTx signal before a drone is inside a gate.

<a name="troubleshooting"></a>
## Troubleshooting

If the app connects to Bluetooth module but doesn't seem to communicate with the device, check the following:

1. Arduino must be 5V 16Mhz (proper work of 3.3V 8Mhz is not guaranteed)
2. Bluetooth module baud rate must correspond to Arduino's: 115200 baud
3. Loopback jumper must be in place.
4. Wiring :)

<a name="contributors"></a>
## Contributors

Big thanks to all contributors to this project:

Android app:
- Louis Plett (highway11) - voice speaking
- Ray Erik Rabe (raveerk) - CSV reports generation
- evgen48 - display frequencies in MHz
- Gleb Godonoga (SidhNor) - internationalization support, Russian localization, permissions tuning
- Jose Luis Ortiz (JLOFPV) - Spanish localization
- thestealth131205, anunique - German localization
- Nicola Gorghetto (nikybiasion) - Italian localization

Arduino app:
- anunique - arbitrary frequency setting, predefined Connex frequencies, improvements to Arduino code

iOS app:
- Lazar Djordjevic (lazar89nis) - entire app implementation!

<a name="contacts"></a>
## Contacts
- YouTube channel: https://www.youtube.com/user/voroshkov
- Facebook: https://www.facebook.com/andrey.voroshkov
- RCGroups discussion thread: https://www.rcgroups.com/forums/showthread.php?2801815

Feel free to ask questions, suggest improvements, report your usage results.

Happy flying!

/Google Play and the Google Play logo are trademarks of Google LLC./
