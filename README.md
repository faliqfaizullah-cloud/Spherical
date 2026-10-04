# Spherical
Minimal techno sequencer + line-art visualizer. Landscape only. WebView app (Java) with WebAudio engine in `app/src/main/assets/www/index.html`.

## Publish from Termux
1. `termux-setup-storage`, then unzip this project and `cd Spherical`
2. `bash publish.sh` (logs into GitHub, creates the repo, pushes, tags `v1.0.0`)
3. GitHub Actions builds `Spherical.apk` -> download from the repo's Releases page (or Actions > Artifacts)

New version: bump `versionCode` in `app/build.gradle`, then `bash publish.sh v1.0.1`.

## Controls
- Tap a track line: add node. Drag node: left/right = timing, up/down = intensity (width of the bump). Drag to the baseline or double-tap: delete.
- Tap a track label: change waveform (SUB/SYN/CHORD) or tuning (drums). Hold: mute.
- Drag the BPM number up/down, or TAP. Arrows: switch pattern. Dice icon: randomize. Square icon: visualizer (tap/swipe for ROOM / CELL / LAND, x to close).

## Haptics
Taps, node grabs, 1/16-step detents while dragging, add/delete, mute, pattern changes, BPM changes, play/stop, and every kick/clap hit. Toggle in the menu (⋮ > HAPTICS).

## Music input (menu ⋮)
- SYSTEM AUDIO: taps the phone's output mix, so music playing in Spotify, YouTube Music, etc. drives the app. Needs the microphone permission (Android requirement for the Visualizer API). Some apps/devices block it; then use MIC.
- MIC: listens to speakers or the room.
- FILE: pick an audio file; it plays inside Spherical.
While an input is active the tracks are rebuilt live from the music: onsets in each frequency band place nodes (quantized to 1/16), nodes fade out, BPM follows the kick, the visualizer scenes react, and the phone pulses on each kick. STOP INPUT restores your pattern.

## Icon & widget
- Launcher icon: your hex-flower logo (adaptive icon, cream background, themed-icon ready). A 512px preview is `Spherical-icon-512.png`.
- Widget: long-press the home screen > Widgets > Spherical (4x2, resizable). Cream card showing the current pattern (or LIVE SYSTEM/MIC/FILE), BPM, node count, and every track's nodes. It refreshes about every 1.5 s while the app is open and keeps the last state afterwards. Tap it to open the app.
