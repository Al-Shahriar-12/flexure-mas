# Android wrapper (optional)

These two files turn Flexure MAS into an offline Android app that shows `index.html` in a WebView, saves real `.fmas` files through Android’s file picker, autosaves the current beam and handles the Back button.

1. In Android Studio, create a new **Empty Views Activity** project named `Flexure MAS`, package `com.masstudio.flexuremas` (or your own), Kotlin, minimum SDK 24.
2. Create the assets folder (right-click `app` → New → Folder → Assets Folder) and copy into it, from the repository root: `index.html`, `three.min.js`, `logo.png` and `author.jpg`. The fonts are built into `index.html`.
3. Replace the project’s `MainActivity.kt` and `AndroidManifest.xml` with the ones here. If your package name differs, change the first line of `MainActivity.kt`.
4. Run it on a phone, or use Build → Generate Signed App Bundle or APK to make an installable APK.

The same `index.html` works in the browser and in the app: it uses the Android bridge when it is present and the browser’s own storage otherwise.
