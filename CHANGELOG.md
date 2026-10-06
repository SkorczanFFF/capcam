# Changelog

All notable changes to this project are listed here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- README and `.gitignore` (Android/Gradle, IDE, node_modules, OS files).
- Changelog.
- Android project skeleton (`android/`): Gradle 9.8 wrapper, AGP 9.4, Kotlin 2.4, Jetpack Compose, minSdk 26, compileSdk/targetSdk 37. A hello-world screen that builds with `gradlew assembleDebug`.
- Phone app MVP: reads the game rotation vector at the fastest rate, applies the mounting (flat on the brim screen up, or upright on the forehead or helmet front with the camera forward or back; landscape or portrait, top edge in any of 4 directions), zeroes yaw on Start and on Recenter, and streams over UDP in the opentrack (48 bytes, port 4242) or CapCam v1 (36 bytes, port 4243) format. One screen shows live data (Hz, yaw/pitch/roll with centre bars, sent/error counts, status) above Start/Stop, Recenter and the settings, which are locked while streaming. Keeps the screen on and holds a Wi-Fi low-latency lock while streaming; dimming the screen is a switch (on by default). Settings are remembered. UI in the brand colours (primary blue background, deep blue panels, raspberry gradient for primary actions and selection, orange for warnings).
- Phone app: the volume buttons recenter while streaming (reachable with the phone on your head), and the phone buzzes each time "forward" is captured.
- Phone app: "Default game camera" button. While it is on, the neutral pose (straight ahead) is sent instead of the head pose, so the game shows its normal camera without disconnecting. "Resume head tracking" switches back.
- `tools/udp-monitor.mjs`: listens on a UDP port and prints Hz, packet gap p50/p99/max, loss (CapCam format) and the latest angles every second, with a summary at the end (`--minutes N`).
- Release build: shrunk with R8 (about 1.2 MB) and signed with a key kept outside the repo (Gradle property `capcam.signing`). Debug builds install as "CapCam debug" (`.debug` id) next to the released app.
- Unit tests for the head math (Euler angles, all 12 mountings, yaw zeroing) and both packet formats.

### Fixed
- "Forward" was sometimes wrong after Start (e.g. the camera looked backwards): the first sensor events can be stale, and yaw was zeroed on the very first one. The phone now sends the neutral pose for 0.5 s after Start and zeroes on the first settled sample.
- Yaw direction: angles now follow opentrack's signs (yaw+ = turn right). Before, turning your head left turned the game camera right.

### Changed
- Phone app: mounting is now picked from three full-width tiles with a side-view drawing and a plain-words description: lying flat (on the cap brim, screen up), standing portrait and standing landscape (on the brim, forehead or helmet, screen facing the monitor). Each has a "turned 180°" switch. Any other position is under "Custom position". Facings are now named by where the screen points (up / forward / towards the forehead).
- README: the project now covers several games (phone app as the single source, opentrack for games with TrackIR support, own mod for BeamNG.drive).
