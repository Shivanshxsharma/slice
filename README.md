# Slice

**[Download Slice.apk](https://github.com/Shivanshxsharma/slice/releases/latest/download/Slice.apk)** · Android 10+

Android app that cuts a video into parts **without re-encoding**. Every frame and audio
packet is copied byte-for-byte from the original, so each part has exactly the original
quality, and splitting runs at storage speed.

- **Number of parts:** enter N, get N parts as equal as the keyframes allow.
- **Max length:** enter seconds, every part is at most that long (Status/Stories limits).
- Pick from the photo picker, or *Share → Slice* from the gallery.
- Parts are saved to `Movies/Slice` as `<name>_part1.mp4`, …; play, share one, or share all.
- No internet permission, no storage permission.

## Install

1. Download `Slice.apk` from the [latest release](https://github.com/Shivanshxsharma/slice/releases/latest) on your phone.
2. Open it and allow installing from your browser/files app when Android asks.
3. The APK is signed with a development key, so Play Protect may warn that it doesn't
   recognise the developer; choose *Install anyway*.

## How the cut works

A lossless cut can only start a part on a keyframe (phone videos have one about every second),
so parts can differ from the exact length you asked for by up to one keyframe gap. The setup
screen shows the real cut points on a filmstrip before you split.

- `split/VideoProbe.kt` reads the video and lists its keyframes (hops keyframe-to-keyframe on MP4/MOV).
- `split/CutPlanner.kt` picks the cut keyframes (pure Kotlin, unit-tested).
- `split/PartFilter.kt` decides sample-by-sample what goes in each part (pure Kotlin, unit-tested).
- `split/Splitter.kt` copies samples with `MediaExtractor` → `MediaMuxer` into MediaStore.

Open-GOP video (common in x265 encodes, not phone cameras): the part before a cut also keeps the
next keyframe so its leading frames still decode, so 1 frame repeats instead of ~4 being lost.

## Build

```
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Tests run the planner and the routing over real packet lists of three ffmpeg-made videos
(`app/src/test/resources/*.csv`: H.264 no B-frames, H.264 with B-frames, HEVC open GOP):
no packet lost, none duplicated (except the open-GOP keyframe), audio split exactly at the cuts.

## License

MIT, see [LICENSE](LICENSE).
