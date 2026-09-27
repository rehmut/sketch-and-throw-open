# Sketch & Throw

An independent native Android project for planning and saving wheel-thrown vessels. The interface follows 1970s Braun product design: warm grey housing, graphite keys, one signal colour, lower-case labels and monospaced readouts. It opens to a home screen with a line illustration of a vase, a bowl and a mug, quick actions and the most recent vessels. Every saved vessel has a category (bowl, mug, cup, vase, jar or plate) shown as a line illustration; the app suggests one from the vessel's proportions and whether it has a handle, and the library filters by category within user-named folders. A gallery photo can be outlined with MediaPipe Interactive Image Segmenter, viewed as a 2D profile, measured after entering a known height, and revolved into a 3D preview. Vessels, profiles, scale settings, and photos can be saved locally and reopened. The 3D mesh can be exported as OBJ. A live camera screen uses CameraX and ML Kit to draw object boxes; its shutter takes a picture and traces it like a picked photo.

The app requests camera permission for live view. Android's Photo Picker grants access only to photos you select. Frames and photos are processed on device and are not uploaded.

## Current scope

The standard ML Kit object detector locates and tracks general objects, but returns boxes rather than exact edges. For gallery photos, the bundled MediaPipe MagicTouch model produces a pixel mask around a point inside the vessel. The app initially uses the centre of the detected object or image; tap **inside the vessel** to guide it when the wrong object is selected. The sensitivity slider changes the confidence threshold of the mask. Switch off **ML outline** to compare the earlier colour-based outline. Neither method is specifically trained to identify pottery; complex scenes, occlusion, and reflective surfaces can still give an inaccurate profile. The 2D and 3D results work best for one upright vessel photographed straight-on.

A photo opens in four steps: **01 outline**, **02 handle**, **03 measure** and **04 3d**.

### Handles

Handle detection is off for a new vessel: switch on **find a handle** in step 01 or 02 for mugs and cups. It then runs only for that vessel, so bowls and vases are never altered by it. A handle is found when the vessel is photographed side-on with the handle sticking out to the left or right. A thrown body is close to symmetric, so the side that reaches further from the axis is pulled back to mirror the other side, and what is left of the silhouette outside the body is the handle candidate. Pieces separated by a short gap, as an ML mask often leaves them, count as one handle; the ML path also uses a looser confidence threshold for the handle, which the model is less sure of than the body. Candidates that are too short, too shallow or too small, such as a cast shadow or a spout, are ignored. The handle's midline is traced with rays from the body edge, so C, D and ear shapes all work, and the body outline stays straight across the joins.

The photo shows the handle's thickness in the image plane only. Its front-to-back width is an assumption (default 20 mm), and the cross-section can be round, oval or a pulled strap. The handle is swept along its midline into a closed tube, added to the 3D preview and to the OBJ as a separate `handle` group, and its clay is added to the estimate before the trimming allowance. A handle facing the camera or hidden behind the body cannot be traced; turn the handle off in step 02 to model the body alone.

The bundled `interactive_segmentation.task` model comes from the [official MediaPipe model download](https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task). Its [model card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf) lists the Apache License 2.0. Gallery segmentation runs on device without a runtime model download.


The 2D profile reports height, widest and narrowest wall diameters and their heights above the base, approximate rim and foot diameters, and the height-to-width ratio. A photo alone has no physical scale: enter the height in centimetres at the stage you want to plan. The first and last 2% of mask rows are excluded from rim and foot measurements to avoid isolated edge pixels. The narrowest wall measurement also excludes the lower base curve, estimated from how much the silhouette flares above the foot; it cannot locate the hidden inside junction exactly.

The rough clay estimate revolves the outside profile, assumes a uniform wall and a separate base thickness, uses a planning density of 1.8 g/cm³ for wet clay, then applies an adjustable trimming allowance (default 25%). Actual clay body, thickness, hidden inside contour, trimming, and shrinkage can change the required weight. [Ceramic Arts Network's throwing weight guide](https://ceramicartsnetwork.org/docs/default-source/uploadedfiles/wp-content/uploads/2009/12/potteryclay.pdf) also recommends keeping a personal chart of weights and dimensions for repeat work. The app does not apply a shrinkage correction; enter the intended throwing-stage height if estimating clay for a finished reference. The 3D preview and OBJ have an open rim, inner and outer walls, and a solid base. Entering height and wall thickness changes the modeled wall. OBJ coordinates remain relative rather than centimetres.

## Open and run

1. Open this folder in Android Studio. Let Gradle sync and install Android SDK 36 if prompted.
2. Run `app` on a physical Android device with a rear camera (Android 7.0 / API 24 or later).
3. The app opens on a single-screen **Home** view. Use **pick photo**, or tap **my vessels** to open your saved folders and create one. **live camera** requests camera permission only when opened; its round shutter key captures a picture for tracing.
4. In the photo view, tap **inside the vessel** if the orange outline selects something else. For a mug or cup, switch on **find a handle** and check the handle in step 02. Enter a height in cm in step 03 and inspect the lettered dimensions and clay estimate. Wall, base and trimming allowance live in **settings** (the dial icon, or the clay settings line in step 03); they are the defaults for new vessels and can be applied to the open one.
5. Tap **save**, give the vessel a name, and choose a folder. Open it later from **my vessels**. Step 04 shows the rotating model; **export OBJ** saves the mesh.

The command-line build is `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows). The debug APK is written to `app/build/outputs/apk/debug/`.

## Google Play path

The project targets API 36, meeting the September 2026 target API requirement for new phone apps. The application ID is `io.github.rehmut.sketchandthrow` and is permanent once published. Test on real devices and use Android Studio's **Build > Generate Signed Bundle / APK** to create a signed Android App Bundle. A Play Console account and store listing are required. Review Play's data safety and camera permission declarations for the final feature set.
