# Building FigTab

Supports **Android 14 (API 34) and above** (minSdk 34, targetSdk 35; runs on Android 15/16 devices).

## Easiest: let GitHub build the APK for you (nothing to install)
1. Create a free GitHub repository and upload this project (on the web: *Add file → Upload files*, drag in the unzipped folder contents, including the hidden `.github` folder).
2. Open the repo's **Actions** tab → **Build APK**. A push starts it automatically (~8–15 min the first time).
3. When it finishes, open the run and download **figtab-debug-apk** (a zip containing `app-debug.apk`). Unzip, copy to your tablet, and install (allow "install unknown apps" for your file manager/browser).
4. For the full Skia renderer: Actions → Build APK → **Run workflow** → tick *with_skia*. The first Skia run takes 30–60+ min; the result is cached afterwards.

## Prerequisites (local build)
- Android Studio Ladybug (2024.2) or newer, **or** command-line: JDK 17 + Android SDK
- SDK Platform 35, **NDK 27.0.12077973**, **CMake 3.22.1** (SDK Manager → SDK Tools)
- Gradle 8.10+ (Android Studio bundles it)

## Build a debug APK
Android Studio: *File → Open* this folder, let it sync, then *Build → Build APK(s)*.

Command line (first time, generate the wrapper):
```
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

The APK is fully offline (no INTERNET permission). `assembleRelease` also yields an installable APK
(signed with the debug key — replace with your own keystore before distributing).

## Two renderer modes (chosen automatically by CMake)
| Mode | When | What you see |
|---|---|---|
| **GLES fallback** | `app/src/main/cpp/third_party/skia/` absent | Dark canvas with a pan/zoom grid. Verifies Kotlin ↔ JNI ↔ EGL ↔ gestures. |
| **Skia** | Skia headers + `lib/<abi>/*.a` present | Demo document (frame, rectangles) via Skia. |

To get Skia mode: `ANDROID_NDK_HOME=<path to NDK 27> scripts/build_skia.sh`, then rebuild.
The native code targets Skia milestone **m126** (`GrDirectContexts::MakeGL`, `SkSurfaces::WrapBackendRenderTarget`,
`GrBackendRenderTargets::MakeGL`). Other milestones may need small API adjustments.

## Controls (Phase 1)
- One finger: pan. Two fingers: pan + pinch-zoom (1.5% – 25,600%).
- Mouse wheel: pan; Ctrl/Cmd + wheel: zoom at cursor.
- Stylus tools, selection, Auto Layout, etc. arrive in later phases.
