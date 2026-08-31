# 镜导 · Lens Coach

Android camera with a live framing overlay, auto lens from the phone’s zoom range, tap-to-focus, and double-tap to shoot. AI picks a scene recipe (portrait / landscape / street), letterboxes the preview to that crop, then crops the capture to match. Color looks live in a secondary **Filters** sheet. Saves to `Pictures/LensCoach`.

## Run

1. Open this folder in Android Studio, or:

```bat
gradlew.bat assembleDebug
```

2. Android 8.0+ device. Grant camera permission on first launch.

Sideload the debug APK from GitHub Releases if USB install is flaky on HyperOS.

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Gestures

- **Tap**: focus + metering
- **Double-tap**: focus and shoot
- **Shutter**: backup capture
- **Filters**: color looks only (Neutral, Soft, Cinema, Documentary, High-contrast)
- **AI framing**: auto lens, AF, and crop overlay
