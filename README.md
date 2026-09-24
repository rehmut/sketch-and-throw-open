# Pottery Wheel Detector

An independent native Android project for observing a wheel-thrown vessel through the rear camera or selecting a photo from the gallery. CameraX displays live video and Google's on-device ML Kit detector draws a box around visible objects. The photo flow uses ML Kit to suggest the object region, traces a silhouette, draws a 2D profile, and revolves that profile into a rotatable 3D vessel. The 3D mesh can be exported as OBJ.

The app requests camera permission for live view. Android's Photo Picker grants access only to photos you select. Frames and photos are processed on device and are not uploaded.

## Current scope

The standard ML Kit object detector locates and tracks general objects. It does **not** recognize pottery as a distinct class or segment its exact outline. The photo outline is estimated by comparing the vessel with sampled background colours, then mirroring the stronger side around an estimated axis. It works best for one upright wheel-thrown vessel photographed straight-on against a simple background. Tap near the centre of the vessel or change the sensitivity when the outline is wrong. Complex scenes, handles, occlusion, and low contrast can still need manual tracing or a trained segmentation model.

The 3D result assumes rotational symmetry and uses an approximate wall thickness. OBJ coordinates are relative units; the app does not know physical dimensions from a photo. Calibrate before using the mesh for making or manufacturing.

## Open and run

1. Open this folder in Android Studio. Let Gradle sync and install Android SDK 36 if prompted.
2. Run `app` on a physical Android device with a rear camera (Android 6.0 / API 23 or later).
3. Grant camera permission and aim at a well-lit vessel, or tap **Pick photo** to select an existing image.
4. In the photo view, inspect the orange outline and 2D profile. Tap the vessel or adjust sensitivity if needed. Drag the 3D preview to rotate it; tap **Export OBJ** to save the mesh.

The command-line build is `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows). The debug APK is written to `app/build/outputs/apk/debug/`.

## Google Play path

The project targets API 36, meeting the September 2026 target API requirement for new phone apps. Before publishing, replace `com.example.potterywheeldetector` with a unique permanent application ID, set the final name and icon, test on real devices, and use Android Studio's **Build > Generate Signed Bundle / APK** to create a signed Android App Bundle. A Play Console account and store listing are required. Review Play's data safety and camera permission declarations for the final feature set.
