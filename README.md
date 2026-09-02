# 镜导 · Lens Coach

Android camera with a live framing overlay, auto lens from the phone's zoom range, tap-to-focus, and double-tap to shoot. AI picks a scene recipe (portrait / landscape / street), letterboxes the preview to that crop, then crops the capture to match. Color looks preview **live on the viewfinder** (Android 12+; applied after capture on older systems). Saves to `Pictures/LensCoach`.

## Run

1. Open this folder in Android Studio, or:

```bat
gradlew.bat assembleDebug
```

2. Android 8.0+ device. Grant camera permission on first launch.

Sideload the debug APK from GitHub Releases if USB install is flaky on HyperOS.

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Gestures

- **Tap**: focus + metering. While the AI frame is locked, tapping inside the frame only focuses; tap the dim area to release the AI crop.
- **Pinch**: manual zoom (pauses AI lens picks for 15 s).
- **Double-tap**: focus and shoot
- **Shutter**: backup capture
- **Thumbnail** (bottom-right): open the last saved photo; the save snackbar also has a **View** action
- **Filters**: color looks with live viewfinder preview and swatch chips
- **Coach banner**: one actionable hint at a time; tap it to expand the "why" and the detected lens list
- **AI framing**: auto lens, AF, and crop overlay — flash, filter, and AI state persist across launches

## Development

```bat
gradlew.bat testDebugUnitTest
```

Unit tests cover the capture crop math, scene classification, framing recipes, and lens step building.
