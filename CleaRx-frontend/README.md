# CleaRx Android

Android frontend for CleaRx, a prescription-reading application. Capture or choose
a prescription image, review the extracted medicine cards, and compare flagged
fields with the source image. Recognition and catalog matching run on the backend.

CleaRx assists reading; it does not establish medication safety or make dispensing
decisions.

## Companion backend

- [Backend repository](https://github.com/oblivjules/clearrx-backend)
- Configured release endpoint: `https://clearx-refactored.onrender.com/`

## Features

- CameraX capture with live quality guidance, focus, flashlight and auto-capture.
- Gallery/file selection, cropping, preview and backend quality validation.
- Structured medicine cards with action-oriented review flags.
- Collapsible Details for comparison images and recognized text.
- First-use/replayable guidance and adjustable result text size.
- Persisted recognition-model selection.
- Official CleaRx splash/launcher artwork with Android's compatible splash API.
- Separate build-time backend configuration for debug and release.

## Requirements

| Component | Project configuration |
| --- | --- |
| Language | Kotlin 1.9.0 |
| Android Gradle Plugin | 8.3.2 |
| Gradle wrapper | 8.4 |
| Java / Gradle JDK | 17 |
| Compile / target SDK | Android API 34 |
| Minimum SDK | API 26 (Android 8.0) |

Use Android Studio with Android SDK Platform 34 installed. The manifest requires
camera hardware, although users may choose an image without granting camera
permission. Camera capture requires its runtime permission.

## Image and session flow

1. Capture a photo or choose a JPEG, PNG or WebP image.
2. Crop selected images to the prescription/medicine region and review quality
   guidance. Selected files are normalized before submission; device originals
   are not overwritten.
3. The app creates/uses a capture session, registers the image, validates quality
   and submits the same cropped bytes with the registered image ID for processing.
4. Review medicine cards, field warnings and Details. Continue or retake when done.

Selected-file input is limited to 10 MB and 24 megapixels. Camera and upload
acceptance share backend quality rules. Warnings support manual comparison and
are not verified accuracy percentages or medication-safety ratings.
