# FigTab — Architecture (Phase 1)

```
figtab/
├── settings.gradle.kts
├── build.gradle.kts
├── app/                                   # Android application shell
│   └── src/main/kotlin/app/figtab/
│       ├── FigTabActivity.kt              # single-activity host, window insets, hardware key dispatch
│       ├── di/AppGraph.kt                 # manual DI (no reflection on hot paths)
│       └── ui/                            # ── Jetpack Compose CHROME ONLY ──
│           ├── workspace/WorkspaceScreen.kt        # adaptive 3-pane layout (WindowSizeClass)
│           ├── topbar/TopAppBar.kt                 # menu, tool selector, title, undo/redo, Run/Dev/Export
│           ├── leftpanel/{PagesList,LayersTree,AssetBrowser}.kt
│           ├── inspector/{Alignment,Geometry,AutoLayoutBuilder,Fills,Strokes,Effects,Export}.kt
│           ├── canvas/CanvasHost.kt                # AndroidView wrapper around CanvasSurfaceView
│           └── theme/
│
├── core-domain/                           # pure Kotlin/JVM, zero Android deps, unit-testable
│   └── src/main/kotlin/app/figtab/domain/
│       ├── scene/                         # Module A
│       │   ├── SceneGraph.kt              # ← delivered (kotlin/SceneGraph.kt)
│       │   ├── Geometry.kt                # Affine2D, Rect, Vec2
│       │   └── NodePatch.kt               # override / mutation payloads
│       ├── index/SpatialIndex.kt          # R-Tree / BVH, O(log n) pick + marquee
│       ├── layout/                        # Module B
│       │   ├── AutoLayoutSolver.kt
│       │   └── ConstraintSolver.kt
│       ├── history/                       # Undo/redo
│       │   ├── Command.kt                 # invertible atomic mutation
│       │   ├── CommandPool.kt             # object pools → zero-GC during drags
│       │   └── HistoryStack.kt
│       ├── input/                         # Module C (pure logic; Android events adapted upstream)
│       │   ├── InputDemux.kt              # stylus | finger | keyboard | mouse routing matrix
│       │   ├── GestureRecognizer.kt       # 2-finger pan/pinch, 2-tap undo, 3-tap redo
│       │   ├── ShortcutEngine.kt
│       │   └── tools/{MoveTool,FrameTool,RectTool,PenTool,TextTool,HandTool}.kt
│       ├── snapping/SnapEngine.kt
│       ├── viewport/ViewportState.kt      # zoom clamp 1.5%..25600%, pan, matrix
│       └── io/{DocumentJson,DocumentProto}.kt
│
├── core-render/                           # Kotlin side of the native canvas
│   └── src/main/kotlin/app/figtab/render/
│       ├── NativeCanvas.kt                # ← delivered: JNI contract
│       ├── CanvasSurfaceView.kt           # SurfaceView, Choreographer pacing, MotionEvent → domain
│       ├── ScenePatchEncoder.kt           # domain diff → binary patch (direct ByteBuffer)
│       └── PatchBuffer.kt                 # pooled direct buffers
│
└── native/                                # C++17 (or Rust via cxx/jni) — Skia lives HERE ONLY
    ├── CMakeLists.txt                     # ← delivered
    ├── third_party/skia/                  # prebuilt libskia.a per ABI (arm64-v8a, x86_64)
    └── src/
        ├── jni_bridge.cpp                 # ← delivered
        ├── scene_patch.h                  # ← delivered: wire format shared with Kotlin
        ├── render_scene.{h,cpp}           # flat, cache-friendly render tree (SoA)
        ├── render_thread.{h,cpp}          # ← delivered: EGL + Skia GL, frame loop
        ├── painters/{shape,text,effects}.cpp      # Phase 2
        └── cache/{picture_cache,glyph_cache}.cpp  # tile / SkPicture caching for static subtrees
```

## Dependency rules (enforced via Gradle module boundaries)
- `core-domain` depends on nothing Android. `ui` and `core-render` depend on it; never the reverse.
- Compose never draws canvas content. The canvas is a `SurfaceView` (own compositor layer, off the UI thread).
- Skia is never exposed to Kotlin. Kotlin talks to native only through `NativeCanvas` (6 floats + direct ByteBuffers).

## Threads
| Thread | Owns |
|---|---|
| Main/UI | Compose, MotionEvent intake |
| Domain (single-threaded executor) | scene graph mutations, spatial index, history |
| Native render | EGL context, `GrDirectContext`, `RenderScene`, frame drawing |

Domain → render is one-way: immutable-snapshot diff encoded as a patch. Render never calls back into the domain.

## Data flow
```
MotionEvent ─► InputDemux ─► Tool ─► Command ─► SceneGraph (persistent, structural sharing)
                                         │                │
                                         │                ├─► SpatialIndex update
                                         │                └─► diff ─► ScenePatchEncoder ─► nativeApplyPatch
                                         └─► HistoryStack (invertible)           │
ViewportState ─► nativeSetViewport (6 floats, no alloc) ─► nativeRequestFrame ◄───┘
                                                         native render thread ─► Skia ─► EGL swap
```

## As-built Phase 1 layout (single Gradle module)
The tree above is the target modularization. To get a buildable project now, the code lives in one
`:app` module, with package boundaries matching the planned modules (`domain`, `render`, `ui`):

```
app/src/main/
├── AndroidManifest.xml, res/values/themes.xml
├── kotlin/app/figtab/
│   ├── FigTabActivity.kt
│   ├── domain/scene/{SceneGraph,DemoScene}.kt
│   ├── domain/viewport/ViewportState.kt
│   ├── render/{NativeCanvas,CanvasSurfaceView,PatchBuffer,ScenePatchEncoder}.kt
│   └── ui/WorkspaceScreen.kt
└── cpp/
    ├── CMakeLists.txt         (Skia optional → GLES fallback)
    └── src/{jni_bridge,render_thread,render_scene}.cpp + .h, scene_patch.h
```
Split into Gradle modules when `domain` needs JVM-only unit tests in isolation.
