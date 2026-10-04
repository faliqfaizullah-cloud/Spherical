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
# Spherical
