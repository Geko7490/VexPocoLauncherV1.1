# VEX Launcher

A performance-first Android launcher designed and optimized for the POCO X6 Pro.

## Product direction

- No widgets, news feeds, weather panels, or background content services.
- Minimal Pixel + Nothing-inspired home screen and app drawer.
- Manual display refresh preference: 60 Hz or 120 Hz.
- Customizable app launch/return animation presets and speed controls.
- Offline-first; installed apps are discovered locally through Android package APIs.
- Performance measured on real devices with frame pacing, jank, and launch benchmarks.

## Performance goals

- Request the selected supported display mode (60 Hz or 120 Hz); the OS and device firmware retain final control.
- Keep UI work off the main thread where practical.
- Avoid continuous animations, unnecessary recompositions, blur-heavy effects, and background polling.
- Measure frame timing rather than claiming guaranteed 120 FPS.

## Build

Open this repository in Android Studio with JDK 17 and Android SDK installed, or run:

```bash
gradle assembleDebug
```

The GitHub Actions workflow builds the debug APK on pushes and pull requests.

## Platform notes

The launcher uses Android's HOME role and launches apps through their exported launcher activities. App transition customization is best-effort: Android versions and OEM firmware (including HyperOS) may constrain or override system-level transitions.

## License

Project source is licensed under Apache-2.0. Third-party dependencies retain their respective licenses.
