# YardTopo v0.2 — camera and elevation repair

## Changes
- Sets `Session.setDisplayGeometry(rotation, width, height)` on the GL thread before every frame update. This is essential for displaying ARCore camera frames and correct screen hit tests.
- Recomputes external camera texture UVs on every frame (avoids blank/uninitialized texture mapping).
- Adds camera, tracking, depth-support, and AR error status text.
- Correctly captures the full window (including camera and overlay) when saving a photo.
- Uses `setup-android@v4` in GitHub Actions.

## Installing from GitHub
Replace the existing repository files with this ZIP's contents, *including* `.github/workflows/build.yml`, commit and push. If browser drag/drop ignores `.github`, create or edit `.github/workflows/build.yml` directly in GitHub.

## Field test
Grant the camera permission, verify you see live video, then slowly move the phone looking at textured ground in daylight. It may take time for ARCore to track. Check the top line for `Camera live`, the tracking state, and depth status. The bottom counter should advance above zero on recognized ground.

## Safety
This is an exploratory visualization, **not** a reliable map for grading or drainage construction. ARCore's plane hits can flatten slopes and depth estimates can be wrong on grass, water, and moving plants. A laser level or surveyed control points are needed for real grade measurements.

---

# YardTopo — Pixel 10 yard elevation preview

Native Java Android proof of concept using ARCore depth-assisted hit tests and camera tracking. Live camera background, color-coded relative elevations, approximately 2-inch contour lines, baseline reset, and annotated screenshot export.

## Build

Open this directory in Android Studio (JDK 17; Android SDK 35). Sync Gradle and run on your Pixel 10 with Google Play Services for AR installed. Or run `gradle assembleDebug` with an Android SDK and Gradle installed; APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Use

Grant camera permission. Outdoors in daylight, walk slowly while aiming the phone toward textured ground. Move around to improve ARCore depth. Use **Reset baseline** to set a new relative-height reference. Use **Save photo** to save the current overlay.

## IMPORTANT LIMITATIONS

* This is a **prototype**, not LiDAR, not a topographic survey, and not accurate enough for construction grading or drainage design.
* The camera view shows **current-frame** ARCore depth hits; it does not fuse multiple frames into a persistent terrain map. It can be noisy or sparse, especially on grass, sand, featureless ground, and in strong sunlight.
* ARCore horizontal planes can flatten real slopes. ARCore DepthPoints may include plants and obstacles, not just soil. No semantic ground segmentation is implemented.
* Elevations are camera-tracked world-Y coordinates relative to the first valid hit; the reference is not an absolute datum. ARCore drift can distort elevations.
* The color range is fixed at ±0.3 m around the baseline. The displayed range is the min/max of currently sampled hits, not a surveyed property-wide range.
* 2-inch contours are interpolated from sparse/noisy points and do **not** imply 2-inch accuracy.
* Screenshot includes UI and is not georeferenced.

## Next steps for a usable yard survey

Persistent world-space point-cloud accumulation, ground-only segmentation, robust surface fitting, scale/level calibration, uncertainty estimates, distance and grade measurement, and exports (CSV/GeoTIFF). Validate against a rotary laser level or RTK GNSS benchmarks.
