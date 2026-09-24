# Centra

An independent native Android project for planning and saving wheel-thrown vessels. It opens to a home screen with rotating pottery photos, creator and license links, and quick actions. The library holds user-named folders. A gallery photo can be outlined with MediaPipe Interactive Image Segmenter, viewed as a 2D profile, measured after entering a known height, and revolved into a 3D preview. Vessels, profiles, scale settings, and photos can be saved locally and reopened. The 3D mesh can be exported as OBJ. A separate live camera screen uses CameraX and ML Kit to draw object boxes.

The app requests camera permission for live view. Android's Photo Picker grants access only to photos you select. Frames and photos are processed on device and are not uploaded.

## Current scope

The standard ML Kit object detector locates and tracks general objects, but returns boxes rather than exact edges. For gallery photos, the bundled MediaPipe MagicTouch model produces a pixel mask around a point inside the vessel. The app initially uses the centre of the detected object or image; tap **inside the vessel** to guide it when the wrong object is selected. The sensitivity slider changes the confidence threshold of the mask. Switch off **Use ML outline** to compare the earlier colour-based outline. Neither method is specifically trained to identify pottery; complex scenes, handles, occlusion, and reflective surfaces can still give an inaccurate profile. The 2D and 3D results work best for one upright vessel photographed straight-on.

The bundled `interactive_segmentation.task` model comes from the [official MediaPipe model download](https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task). Its [model card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf) lists the Apache License 2.0. Gallery segmentation runs on device without a runtime model download.

The home screen bundles three Wikimedia Commons pottery photographs so the rotating display works offline. Each photo displays the creator, a source link, and a license link. See [photo credits](PHOTO_CREDITS.md) for the full list.

The 2D profile reports height, widest and narrowest sustained body diameters and their heights above the base, approximate rim and foot diameters, and the height-to-width ratio. A photo alone has no physical scale: enter the height in centimetres at the stage you want to plan. The first and last 2% of mask rows are excluded from reported rim, foot, and narrowest measurements to avoid isolated edge pixels.

The rough clay estimate revolves the outside profile, assumes a uniform adjustable wall and base thickness, uses a planning density of 1.8 g/cm³ for wet clay, then applies an adjustable trimming allowance (default 25%). Actual clay body, thickness, hidden inside contour, trimming, and shrinkage can change the required weight. [Ceramic Arts Network's throwing weight guide](https://ceramicartsnetwork.org/docs/default-source/uploadedfiles/wp-content/uploads/2009/12/potteryclay.pdf) also recommends keeping a personal chart of weights and dimensions for repeat work. The app does not apply a shrinkage correction; enter the intended throwing-stage height if estimating clay for a finished reference. The 3D preview and OBJ still use relative coordinates and an approximate wall thickness.

## Open and run

1. Open this folder in Android Studio. Let Gradle sync and install Android SDK 36 if prompted.
2. Run `app` on a physical Android device with a rear camera (Android 7.0 / API 24 or later).
3. The app opens on **Home**. Use the central **Pick photo** action, or create a folder such as Bowls under Saved vessels. **Live camera** is optional and requests camera permission only when opened. The featured pottery photo changes every eight seconds, and the arrow advances it manually.
4. In the photo view, tap **inside the vessel** if the orange outline selects something else. Enter a height in cm, adjust wall thickness in mm and trimming allowance in percent, and inspect the 2D proportions and clay estimate.
5. Tap **Save vessel**, give it a name, and choose a folder. Open it later from Library. Tap **Open 3D** for a separate full-height rotating viewer, or **Export OBJ** to save the mesh.

The command-line build is `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows). The debug APK is written to `app/build/outputs/apk/debug/`.

## Google Play path

The project targets API 36, meeting the September 2026 target API requirement for new phone apps. Before publishing, replace `com.example.potterywheeldetector` with a unique permanent application ID, set the final name and icon, test on real devices, and use Android Studio's **Build > Generate Signed Bundle / APK** to create a signed Android App Bundle. A Play Console account and store listing are required. Review Play's data safety and camera permission declarations for the final feature set.
