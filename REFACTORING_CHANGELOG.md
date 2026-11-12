# Frontend refactoring migration changelog

Changes follow the MVP Validation Findings & Refactoring Priorities and preserve
the existing nested Android project structure. Backend B1–B8 have landed on the
backend migration branch; B8 is `dcbe383`. No push or merge was performed.

## F1 — Camera preview processing and API clients

- Priority: #3 capture reliability, with supporting lifecycle/network fixes.
- Migrated bounded preview JPEG encoding: sampled decoding, EXIF orientation,
  a 1280-pixel maximum edge and a 1 MB upload cap without rewriting the source.
- Preview polling cleans temporary files on cancellation and late callbacks.
  Invalid/oversized preview responses do not count as server-offline failures.
- Stop callbacks before native analyzer cleanup; queue native resource release
  on the analysis executor. Release camera/polling on pause and restart only on
  the resumed camera page. Guard duplicate starts and permission requests.
- Separate validation and processing Retrofit URL caches. Added the required
  trailing slash to the existing development base URL.
- Kept later upload, onboarding, result UI, security and model-selection changes
  outside F1. Existing permission messaging is retained.
- Verification: cached Kotlin 1.9.0/JUnit 4.13.2 compiled PreviewSizing and the
  original ExampleUnitTest on Java 17; all 5 JVM tests passed. This isolated run
  does not compile Android-dependent camera/encoder code.
- Android verification attempted:
  `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-daemon`
  using Android Studio's Java 17 runtime and the existing Gradle cache. Gradle
  stopped before compilation because the configured Android SDK directory
  `C:\Users\Dell\AppData\Local\Android\Sdk` was missing. No SDK/build-dependency
  versions or local.properties were changed by this migration.
- Follow-up: the user reports that the Android Studio build now succeeds.
  This is user-provided build verification; the agent did not rerun the full
  Android Gradle build/test tasks. The five JVM tests above passed independently.
  Full Android unit-test execution and camera device checks remain unverified.
- Whitespace check: passed. Commit: `61eeb51` - `fix(camera): stabilize preview processing and API clients`.

## F2 - File selection, cropping and upload

- Priority #3: migrated the file picker, crop/preview screen, upload loader,
  upload/quality API contracts and private crop activity registration. Retained
  the existing result UI, onboarding and default recognition selection for later
  commits. No new application dependencies were required.
- Submit the same cropped JPEG bytes for camera registration, quality validation
  and OCR, compatible with the backend checksum policy. Stop on failed camera
  quality or registration. Uploads use the shared server acceptance policy.
- Added bounded copying and a 24-megapixel header check before image allocation.
  Normalize selected JPEG/PNG/WebP inputs to JPEG, including EXIF orientation,
  so declared MIME matches the submitted bytes. Device originals are unchanged.
- Retain source files across crop activity recreation; delete app-cache attempt
  copies on completion/failure and crop cancellation. Crop selection may reset
  after rotation; no crop-state restoration beyond temporary file ownership is
  claimed. The gallery alternative does not require camera permission.
- Priority #2 privacy dependency: added a non-blocking, accessible notice on both
  capture and crop screens before submission. It explains external processing
  and actual encryption/retention limitations without deletion/masking promises.
  Moved this notice into F2 so the new input path does not precede disclosure.
- Verification: 12 cached Kotlin/JVM tests passed; manifest/resource XML parsed
  and string references/whitespace checks passed. These checks do not compile
  Android-dependent code. Android Gradle build/test permission was declined; no
  full F2 Android build or device test was executed by the agent.
  Follow-up: the user reports that the requested F2 Android Studio build and
  unit-test run succeeded. This is user-provided verification; device interaction
  checks have not been independently verified by the agent.
- Commit: approved by the user; commit reference recorded in the next entry.
