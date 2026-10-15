#pragma once
#include <android/native_window.h>
#include <EGL/egl.h>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <thread>
#include <vector>

#if FIGTAB_HAS_SKIA
#include "include/core/SkSurface.h"
#include "include/gpu/ganesh/GrDirectContext.h"
#include "render_scene.h"
#endif

namespace figtab {

struct Viewport { float m[6] = {1, 0, 0, 1, 0, 0}; };

// Owns the EGL context (and Skia's GrDirectContext when available). All GPU work runs on one thread.
class RenderThread {
public:
    RenderThread();
    ~RenderThread();

    // Called from the JNI/main thread; cheap. Real work happens on the render thread.
    void surfaceCreated(ANativeWindow* window);     // takes its own reference
    void surfaceChanged(int w, int h, float density);
    void surfaceDestroyed();                         // BLOCKS until GPU resources for the window are released
    void setViewport(const float m[6]);
    void setBackground(uint32_t argb);
    void enqueuePatch(const uint8_t* data, size_t len);   // copies: the Kotlin buffer is reused right after
    void enqueueOverlay(const uint8_t* data, size_t len);
    void requestFrame();

private:
    void loop();
    bool initEgl();
    void teardownEgl();
    void renderFrame();
#if FIGTAB_HAS_SKIA
    bool ensureSkSurface();
#else
    void drawFallbackGrid(const Viewport& vp);
#endif

    std::thread thread_;
    std::mutex mu_;
    std::condition_variable cv_;
    std::condition_variable doneCv_;
    bool quit_ = false;
    bool frameRequested_ = false;
    bool pendingCreate_ = false, pendingDestroy_ = false, pendingResize_ = false;
    ANativeWindow* pendingWindow_ = nullptr;
    int reqW_ = 0, reqH_ = 0;
    float density_ = 1.f;
    Viewport viewport_;
    uint32_t background_ = 0xFF2C2C2C;
    std::vector<std::vector<uint8_t>> patchQueue_;
    std::vector<uint8_t> overlay_;

    // Render-thread-only state
    ANativeWindow* window_ = nullptr;
    EGLDisplay dpy_ = EGL_NO_DISPLAY;
    EGLContext ctx_ = EGL_NO_CONTEXT;
    EGLSurface surf_ = EGL_NO_SURFACE;
    EGLConfig cfg_ = nullptr;
    int width_ = 0, height_ = 0;
#if FIGTAB_HAS_SKIA
    sk_sp<GrDirectContext> grCtx_;
    sk_sp<SkSurface> skSurface_;
    RenderScene scene_;
#endif
};

}  // namespace figtab
