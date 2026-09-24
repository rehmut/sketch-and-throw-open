# Pottery Wheel Detector

An independent native Android starter project for observing a wheel-thrown vessel through the rear camera. CameraX displays live video and Google's on-device ML Kit detector draws a box around the prominent visible object. The app only requests camera permission; it does not store or upload frames.

## Current scope

The standard ML Kit object detector locates and tracks general objects. It does **not** recognize a pottery vessel as a distinct class or trace its exact silhouette. For reliable pottery-specific recognition, the next step is collecting representative wheel images, annotating vessel boundaries, training and evaluating a model, then bundling it with the app. The existing web app has a silhouette detector that could also inform a native implementation, but it has not been ported here.

## Open and run

1. Open this folder in Android Studio. Let Gradle sync and install Android SDK 36 if prompted.
2. Run `app` on a physical Android device with a rear camera (Android 6.0 / API 23 or later).
3. Grant camera permission and aim at a well-lit vessel against a relatively clear background.

The command-line build is `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows). The debug APK is written to `app/build/outputs/apk/debug/`.

## Google Play path

The project targets API 36, meeting the September 2026 target API requirement for new phone apps. Before publishing, replace `com.example.potterywheeldetector` with a unique permanent application ID, set the final name and icon, test on real devices, and use Android Studio's **Build > Generate Signed Bundle / APK** to create a signed Android App Bundle. A Play Console account and store listing are required. Review Play's data safety and camera permission declarations for the final feature set.
