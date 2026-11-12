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
- Commit: `b3dcd6c` - `feat(capture): add file selection and prescription cropping`.

## F3 - Actionable result and field review guidance

- Priorities #2, #4 and #5, with #1 result hierarchy: show medicine cards before
  collapsible image/text details; retain capture/upload privacy notices.
- Migrate provisional 95/80 tiers, explicit field warnings, unresolved-name
  guidance, missing critical fields, and narrow-screen/large-text card stacking.
  Labels describe detected warnings, not medication safety or verified accuracy.
- Add review/provenance response fields. Label catalog scores as name similarity;
  do not substitute image quality for missing OCR confidence. Strip OCR annotation
  markers only for display. Keep a separate persistent saving-failure notice.
- Remove prescription text/medicine payloads from result logs. Preserve camera
  lifecycle, upload/cropping and default recognition selection from F1/F2.
- Separate Android resource mappings and the prescription DTO from the pure tier
  rules so regression tests run without an Android runtime or generated resources.
- Verification: 18 cached Kotlin/JVM tests passed on Java 17, including six review
  policy cases. Parsed all 34 resource/manifest XML files; Kotlin string/id
  references, result ordering, retained F2 controls and whitespace checks passed.
  These checks do not compile Android-dependent UI code. The full Gradle build
  and Android unit-test request was declined; F3 Android Studio build/tests and
  device checks (details, review labels, large text, saving failures) are pending.
- Follow-up: the user reports the requested F3 Android Studio build and
  unit-test run succeeded. Device interaction checks remain unverified by the
  agent. The user approved this commit.
- Commit: `3466a5b` - `feat(results): show actionable field review guidance`.

## F4 - Guided workflow and accessible result controls

- Priorities #1 and #2, supporting #4: migrate a skippable four-step first-use
  tour, replayable How to use, workflow status and persisted result text sizes.
- Include external-processing disclosure in capture guidance and retain the
  existing detailed privacy notice on capture/crop before submission.
- Pause camera analysis for the tour, restore prior screen state afterward,
  prevent resume from restarting capture under the tour, and cancel the overlay
  on activity destruction. Back closes the guide. Keep tour text scrollable and
  hide underlying controls from accessibility while the guide is active.
- Preserve result explanations on demand without automatic result popups.
  Replace the misleading Continue toast claiming an image was saved with neutral
  next-prescription wording. No save action is added.
- Verification: all 34 resource/manifest XML files parse; Kotlin string/id
  references, unique view IDs, retained upload/privacy controls and whitespace
  checks pass. Recompiled the unchanged pure Kotlin regression suite using cached
  Kotlin/JUnit on Java 17: 18 tests passed. This does not compile or exercise the
  new Android UI code. Full F4 Android build/test and device checks remain pending;
  Gradle execution was previously declined, so it was not requested again.
  Verify first-use/replayed tour, Skip/Back, rotation/backgrounding, camera
  restart, gallery without camera permission, large text and results Details.
- Follow-up: the user reports the requested F4 Android Studio verification
  succeeded and approves the commit. This is user-provided verification; the
  agent did not execute the Android build or device interaction checks.
- Commit: `a00dda7` - `feat(onboarding): guide capture and result review`.

## F5 - Android transport, logging and backup configuration

- Priority #2 privacy/security support: disable release cleartext traffic and
  migrate separate debug network resources. Tighten the refactored debug policy
  to the active development host, emulator host and loopback; other HTTP is denied.
- Disable backup and explicitly exclude app-data domains from legacy backup,
  cloud backup and device transfer. Do not claim existing backups were erased.
- Disable Retrofit HTTP logging outside debug builds; preserve BASIC-only debug
  logging and the F1 client cache/timeout behavior.
- Document the current HTTP development URL: release network processing remains
  unavailable until a real HTTPS endpoint is configured. No endpoint or certificate
  is invented, and storage/provider retention limitations remain documented.
- Verification: all 35 source XML files parse. Static checks confirm release
  has no HTTP exceptions, debug exceptions include the active backend host,
  all nine app-data domains are excluded from all three backup/transfer rule
  sets, and internal activities remain private. Whitespace checks pass.
  These are configuration checks, not Android manifest/resource merge or device
  verification. Full debug/release builds, Android unit tests, transport enforcement
  and backup/restore checks remain pending. Gradle execution was previously
  declined and was not requested again. Existing pure Kotlin logic is unchanged.
- Follow-up: the user reports the requested debug/release builds and Android
  unit tests succeeded and approves proceeding. This is user-provided build/test
  verification; device transport and backup/restore behavior remain unverified
  by the agent.
- Commit: `bdacad3` - `fix(security): restrict Android transport and backups`.

### F5 operational notes

- The newly prepared `CleaRx-frontend/SECURITY.md` disappeared before staging.
  Its removal was preserved; the material limitations are recorded here instead.
- Release uses system-trusted certificates and rejects HTTP. Debug permits HTTP
  only to `192.168.1.3`, `10.0.2.2`, `localhost` and `127.0.0.1`. If the LAN server
  changes, update MainActivity.BASE_URL and the debug-only domain list together.
  Do not use personal prescription images over unencrypted local debug HTTP.
- The development endpoint is still HTTP. Configure a real HTTPS endpoint and
  test capture/upload before using a release build; this migration does not
  provision a production backend or certificate.
- Backup exclusions include preferences, so onboarding/text-size settings will
  not transfer. No OEM/device backup behavior has been verified. The settings
  do not erase existing backups or local/backend records. Cache cleanup remains
  best effort; no secure-erasure, masking, provider retention/training guarantee
  or backend retention deadline is implemented or claimed.
- Inspect debug/release merged manifests and packaged network-security resources.
  On a disposable test device, verify debug local access, rejection of unlisted
  HTTP, release HTTP rejection, HTTPS connectivity and backup/transfer exclusions.
  Do not bypass certificate validation. Retrofit uses BASIC debug logging and
  NONE release logging; this is not a claim that all app/backend logs are audited.
- References: [Android network security configuration](https://developer.android.com/privacy-and-security/security-config)
  and [Android backup rules](https://developer.android.com/identity/data/autobackup).

## F6 - Public recognition-model selection

- Priority #2: migrate a persisted recognition picker with the approved public
  options CleaRx TrOCR and Claude Sonnet 5.5. Use the backend public primary
  identifier `clearx-trocr`; retain truthful remote-processing disclosure.
- Add the optional model multipart field. Camera and uploaded crops share the
  same processing request path and snapshot the selection before submission.
- Disable selection during a processing attempt; restore it on reset. Preserve
  the current result layout, replayed tour state and F5 debug/release policies.
- Missing or unsupported saved IDs safely select the primary option. Provider
  availability is still a backend/runtime concern; the picker does not establish
  that a model is available or that recognition accuracy targets were achieved.
- Verification: 20 isolated Kotlin/JVM tests passed using cached Kotlin/JUnit
  on Java 17, including saved-model restoration and invalid/missing preference
  fallback. All source XML parses; string/id references, unique view IDs, retained
  privacy/upload controls, backend option identifiers and whitespace checks pass.
  These checks do not compile Android UI or exercise multipart HTTP requests.
  F6 Android Studio build/unit tests and picker interaction remain pending.
  Check both camera/upload paths, persistence after relaunch, disabled selection
  during processing, reset, tour replay and actual returned OCR source. Live
  provider calls and backend runtime model availability were not tested.
- Follow-up: the user reports the requested F6 Android Studio verification
  succeeded and explicitly approves this commit. This is user-provided
  verification; live provider calls and model availability were not independently
  tested by the agent.
- Commit: approved as `feat(ocr): add public recognition-model selection`.
  Resolve its hash with `git log -1 --format=%h --grep="^feat(ocr): add public recognition-model selection$"`.

The approved B1-B8 and F1-F6 commit sequence is complete upon this commit.
Backend final commit: `dcbe383`. Frontend F1-F5 references are recorded above.
No push, merge, deployment, live database migration or accuracy evaluation was
performed. Release network processing still requires a configured HTTPS endpoint.

