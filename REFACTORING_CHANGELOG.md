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
- Whitespace check: passed. Commit: pending explicit approval.
