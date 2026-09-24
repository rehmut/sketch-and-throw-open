# Pottery Wheel Detector

An independent native Android project for observing a wheel-thrown vessel through the rear camera or selecting a photo from the gallery. CameraX displays live video and Google's on-device ML Kit detector draws a box around visible objects. The photo flow uses MediaPipe Interactive Image Segmenter to find the vessel boundary, draws a 2D profile, and revolves that profile into a rotatable 3D vessel. The 3D mesh can be exported as OBJ.

The app requests camera permission for live view. Android's Photo Picker grants access only to photos you select. Frames and photos are processed on device and are not uploaded.

## Current scope

The standard ML Kit object detector locates and tracks general objects, but returns boxes rather than exact edges. For gallery photos, the bundled MediaPipe MagicTouch model produces a pixel mask around a point inside the vessel. The app initially uses the centre of the detected object or image; tap **inside the vessel** to guide it when the wrong object is selected. The sensitivity slider changes the confidence threshold of the mask. Switch off **Use ML outline** to compare the earlier colour-based outline. Neither method is specifically trained to identify pottery; complex scenes, handles, occlusion, and reflective surfaces can still give an inaccurate profile. The 2D and 3D results work best for one upright vessel photographed straight-on.

The bundled `interactive_segmentation.task` model comes from the [official MediaPipe model download](https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task). Its [model card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf) lists the Apache License 2.0. Gallery segmentation runs on device without a runtime model download.

The 3D result assumes rotational symmetry and uses an approximate wall thickness. OBJ coordinates are relative units; the app does not know physical dimensions from a photo. Calibrate before using the mesh for making or manufacturing.

## Open and run

1. Open this folder in Android Studio. Let Gradle sync and install Android SDK 36 if prompted.
2. Run `app` on a physical Android device with a rear camera (Android 7.0 / API 24 or later).
3. Grant camera permission and aim at a well-lit vessel, or tap **Pick photo** to select an existing image.
4. In the photo view, inspect the orange outline and 2D profile. Tap **inside the vessel** if the outline selects something else, then adjust sensitivity if needed. Drag the 3D preview to rotate it; tap **Export OBJ** to save the mesh.

The command-line build is `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows). The debug APK is written to `app/build/outputs/apk/debug/`.

## Google Play path

The project targets API 36, meeting the September 2026 target API requirement for new phone apps. Before publishing, replace `com.example.potterywheeldetector` with a unique permanent application ID, set the final name and icon, test on real devices, and use Android Studio's **Build > Generate Signed Bundle / APK** to create a signed Android App Bundle. A Play Console account and store listing are required. Review Play's data safety and camera permission declarations for the final feature set.
