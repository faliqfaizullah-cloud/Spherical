# Spherical
Draw-a-hill techno sequencer + line-art visualizer. Android app (Java + WebView), audio engine in WebAudio: `app/src/main/assets/www/index.html`.

## Publish from Termux
1. `termux-setup-storage`, unzip the project, `cd Spherical`
2. `bash publish.sh` (first time) or `bash publish.sh v1.3.0` (updates; bump `versionCode` in `app/build.gradle` first)
3. GitHub Actions builds `Spherical.apk` -> Releases page.

## How it plays
- **Draw a hill, the higher it climbs the more it plays.** Drag a finger across a track to paint its density curve. Wherever the curve rises above the dashed threshold a note is sown on that 16th step (filled dot). Taller = louder. Tap = a hill; double-tap = clear the track.
- **Loops drift.** Drag the end tick of a track's line to change its loop length (2-16 steps). Different lengths never line up the same way twice. Tap the small `16 · 16th` text to cycle x2 / ÷2 speed.
- **Tap a track name** to mute it (row fades, shows "muted").
- **Change the key.** Tap or drag across `key A` in the header; the key ruler appears. Everything follows (kick, sub, synths, bells, chords).
- **Hand the melody over.** SYN, BELL and CHORD take turns leading every 2 bars (the leader is underlined, the others sit back).
- **Mixer** (dial icon): 11 faders (dashed line = unity, tap the icon to mute) and knobs LOW / MID / HIGH (EQ), ECHO, TIDE (slow filter sweep), ROOM (reverb). Drag knobs up/down, double-tap to reset.
- **Dice** rolls a new section (new hills, lengths, mutes and melodies). `‹ FOUR ›` switches the built-in patterns.
- **BPM**: drag the number or TAP. Play/stop = round button.
- **Scenes** (menu): CELL, LAND, ORBIT, ROOM line art driven by the notes and the sound. Tap or swipe to switch, x to close.
- **Night / Day** and **Orientation** (landscape by default, then portrait or auto-rotate) are in the menu.
- **Haptics** on touches, detents and every kick.
- **Music input** (menu): SYSTEM AUDIO, MIC or FILE. The music paints hills into the tracks live, BPM follows the kick; STOP INPUT restores your pattern.

## Icon & widget
Adaptive hex-flower icon. Widget (4x2): cream card with the pattern, key, BPM and the drawn curves and active notes of the audible tracks.
