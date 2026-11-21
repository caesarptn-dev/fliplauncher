# Flip Launcher

An iOS 18-style home screen for Android 2.1+ (API 7), tuned for tiny, slow phones
with a slide-out keyboard (Motorola Flipout class).

## Look
- Two widgets on top of page one: a blue clock/date/battery card and a map card (tap to open Maps)
- Flat, rounded iOS icon tiles tinted from each app's own colours (optional iOS 6 glossy mode)
- Hand-drawn Phone / Messages / Browser / Camera icons in the dock
- Floating, rounded frosted-glass dock and a "Search" pill above it (turns into page dots while swiping)
- Wallpaper blurred once at startup (never live), subtle parallax
- iOS-style zoom animation when opening an app

## Keyboard
- Just start typing on the home screen: Spotlight search opens, Enter launches the top hit
- D-pad navigation with a focus highlight; pages follow the focus
- Menu key (or long-press empty space): toggles for blur, parallax, glossy icons

## Other
- Long-press an icon: wiggle mode, tap the badge to uninstall; Home/Back exits
- No libraries, tiny APK, low RAM; keyboard flip/rotation does not restart the launcher

## Build locally
Android Studio (Arctic Fox / 2020.3 or newer with JDK 11): open this folder and let Gradle sync,
then Build > Build APK(s). Then press Home on the phone and choose "Flip Launcher" > Always.

## Build with GitHub Actions (no Android Studio needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Open the repo's **Actions** tab. The "Build APK" workflow runs on every push
   (or click "Run workflow").
3. When it finishes, download **FlipLauncher-debug** from the run's Artifacts and
   install `app-debug.apk` on the phone.
4. Tag a commit `v2.0` to also attach the APK to a GitHub Release.
