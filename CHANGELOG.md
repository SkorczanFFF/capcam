# Changelog

All notable changes to this project are listed here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.1] - 2026-10-07

### Added
- Phone app: under the PC address it shows the phone's own address and what the PC's address should start with, and warns when the typed address isn't on the phone's network or a VPN is on.
- Phone app: **Screen while streaming: On / Dim / Black** next to Start and Recenter, changeable while streaming. *Black* covers the whole screen including the system bars (on an OLED screen the pixels are off) while tracking and the volume buttons keep working; a tap shows the app for 10 s.

### Changed
- Phone app: the "Dim screen while streaming" switch at the bottom is replaced by the screen modes above (a saved "dim" choice carries over).
- Phone app: the "How you wear the phone" pictures are now a minimal cap with the phone on its brim and an arrow showing where the screen faces (no head or monitor).
- README: how to find the PC's address (Windows 11 settings or `ipconfig`, which address to pick, VPN adapters, DHCP reservation).
- README: explains why sites like whatismyip show the wrong address (public instead of the PC's local one).
- README: opentrack main window screenshot, the hammer settings buttons, and a connection check (Raw tracker data moves with your head).

### Fixed
- Phone app: the recenter buzz never arrived on phones with touch haptics turned off. It is now sent as feedback for a physical button, and is two short taps.

## [0.1.0] - 2026-10-06

First release: head tracking for TrackIR games through opentrack, tested in BeamNG.drive and
Richard Burns Rally (RallySimFans).

### Added
- Phone app (Android 8.0+, needs a gyroscope):
  - Reads the game rotation vector (gyroscope + accelerometer, no magnetometer) at the fastest rate the phone allows, about 200 Hz.
  - Streams over UDP in the **opentrack** format (48 bytes, port 4242; signs match opentrack: yaw+ = right, pitch+ = up, roll+ = right ear down) or the **CapCam v1** format (36 bytes, port 4243, a quaternion for game mods).
  - How you wear the phone: three illustrated tiles (lying flat on the cap brim; standing portrait or landscape on the brim, forehead or helmet with the screen facing the monitor), a "turned 180°" switch and a custom position for anything else.
  - "Forward" is set on Start (after a 0.5 s settle, because the first sensor readings are stale) and on Recenter or a volume button, with a short buzz as confirmation.
  - "Default game camera" sends the neutral pose so the game shows its normal camera without disconnecting.
  - One screen with live data (rate, angles with centre bars, sent/error counts, status) above the controls and settings; settings are locked while streaming and remembered.
  - Keeps the screen on, optionally dimmed, and holds a Wi-Fi low-latency lock while streaming.
  - UI in the brand colours.
- `tools/udp-monitor.mjs`: prints the phone's packet rate, timing jitter (p50/p99/max), loss and angles, with a summary at the end (`--minutes N`).
- README: quick start, setup of the phone app and opentrack, wearing the phone, calibration, game notes (BeamNG.drive, Richard Burns Rally RSF, Euro Truck Simulator 2), troubleshooting and packet formats.
- Build: Gradle 9.8, AGP 9.4, Kotlin 2.4, Jetpack Compose; minSdk 26, targetSdk 37. Release APK shrunk with R8 (about 1.2 MB) and signed with a key kept outside the repo. Debug builds install as "CapCam debug" next to the release app.
- Unit tests for the head math (Euler angles, all 12 mountings, yaw zeroing) and both packet formats.

[Unreleased]: https://github.com/SkorczanFFF/capcam/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/SkorczanFFF/capcam/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/SkorczanFFF/capcam/releases/tag/v0.1.0
