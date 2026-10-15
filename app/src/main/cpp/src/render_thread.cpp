#include "render_thread.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <cmath>

#if FIGTAB_HAS_SKIA
#include "include/core/SkCanvas.h"
#include "include/core/SkColorSpace.h"
#include "include/core/SkMatrix.h"
#include "include/core/SkSurfaceProps.h"
#include "include/gpu/ganesh/GrBackendSurface.h"
#include "include/gpu/ganesh/GrTypes.h"
#include "include/gpu/ganesh/SkSurfaceGanesh.h"
#include "include/gpu/ganesh/gl/GrGLBackendSurface.h"
#include "include/gpu/ganesh/gl/GrGLDirectContext.h"
#include "include/gpu/ganesh/gl/GrGLInterface.h"
#include "include/gpu/ganesh/gl/GrGLTypes.h"
#include "include/gpu/ganesh/gl/egl/GrGLMakeEGLInterface.h"
#endif

#define TAG "figtab"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace figtab {

RenderThread::RenderThread() { thread_ = std::thread([this] { loop(); }); }

RenderThread::~RenderThread() {
    { std::lock_guard<std::mutex> l(mu_); quit_ = true; }
    cv_.notify_all();
    if (thread_.joinable()) thread_.join();
    if (pendingWindow_) { ANativeWindow_release(pendingWindow_); pendingWindow_ = nullptr; }
}

void RenderThread::surfaceCreated(ANativeWindow* w) {
    ANativeWindow_acquire(w);
    {
        std::lock_guard<std::mutex> l(mu_);
        if (pendingWindow_) ANativeWindow_release(pendingWindow_);
        pendingWindow_ = w;
        pendingCreate_ = true;
    }
    cv_.notify_all();
}

void RenderThread::surfaceChanged(int w, int h, float d) {
    {
        std::lock_guard<std::mutex> l(mu_);
        reqW_ = w; reqH_ = h; density_ = d;
        pendingResize_ = true; frameRequested_ = true;
    }
    cv_.notify_all();
}

void RenderThread::surfaceDestroyed() {
    std::unique_lock<std::mutex> l(mu_);
    pendingDestroy_ = true;
    cv_.notify_all();
    doneCv_.wait(l, [this] { return !pendingDestroy_; });   // the Surface must not be used after this returns
}

void RenderThread::setViewport(const float m[6]) {
    std::lock_guard<std::mutex> l(mu_);
    for (int i = 0; i < 6; ++i) viewport_.m[i] = m[i];
}

void RenderThread::setBackground(uint32_t argb) {
    std::lock_guard<std::mutex> l(mu_);
    background_ = argb;
}

void RenderThread::enqueuePatch(const uint8_t* d, size_t n) {
#if FIGTAB_HAS_SKIA
    std::lock_guard<std::mutex> l(mu_);
    patchQueue_.emplace_back(d, d + n);
#else
    (void)d; (void)n;   // fallback renderer has no scene
#endif
}

void RenderThread::enqueueOverlay(const uint8_t* d, size_t n) {
    std::lock_guard<std::mutex> l(mu_);
    overlay_.assign(d, d + n);
}

void RenderThread::requestFrame() {
    { std::lock_guard<std::mutex> l(mu_); frameRequested_ = true; }
    cv_.notify_one();
}

bool RenderThread::initEgl() {
    dpy_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy_ == EGL_NO_DISPLAY || !eglInitialize(dpy_, nullptr, nullptr)) { LOGE("eglInitialize failed"); return false; }

    const EGLint cfgAttrs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE};
    EGLint n = 0;
    if (!eglChooseConfig(dpy_, cfgAttrs, &cfg_, 1, &n) || n < 1) { LOGE("no suitable EGL config"); return false; }

    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    ctx_ = eglCreateContext(dpy_, cfg_, EGL_NO_CONTEXT, ctxAttrs);
    if (ctx_ == EGL_NO_CONTEXT) { LOGE("eglCreateContext failed"); return false; }

    surf_ = eglCreateWindowSurface(dpy_, cfg_, window_, nullptr);
    if (surf_ == EGL_NO_SURFACE || !eglMakeCurrent(dpy_, surf_, surf_, ctx_)) { LOGE("EGL surface/make-current failed"); return false; }
    eglSwapInterval(dpy_, 1);

#if FIGTAB_HAS_SKIA
    sk_sp<const GrGLInterface> iface = GrGLInterfaces::MakeEGL();
    grCtx_ = GrDirectContexts::MakeGL(iface);
    if (!grCtx_) { LOGE("GrDirectContext creation failed"); return false; }
    LOGI("renderer: Skia (Ganesh/GL)");
#else
    LOGI("renderer: GLES fallback (no Skia)");
#endif
    return true;
}

void RenderThread::teardownEgl() {
#if FIGTAB_HAS_SKIA
    skSurface_.reset();
    if (grCtx_) {
        grCtx_->releaseResourcesAndAbandonContext();   // EGL context is still current here
        grCtx_.reset();
    }
#endif
    if (dpy_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(dpy_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surf_ != EGL_NO_SURFACE) eglDestroySurface(dpy_, surf_);
        if (ctx_ != EGL_NO_CONTEXT) eglDestroyContext(dpy_, ctx_);
        eglTerminate(dpy_);
    }
    dpy_ = EGL_NO_DISPLAY; ctx_ = EGL_NO_CONTEXT; surf_ = EGL_NO_SURFACE;
    if (window_) { ANativeWindow_release(window_); window_ = nullptr; }
}

#if FIGTAB_HAS_SKIA

bool RenderThread::ensureSkSurface() {
    if (skSurface_ && skSurface_->width() == width_ && skSurface_->height() == height_) return true;
    GrGLFramebufferInfo fb;
    fb.fFBOID = 0;               // default framebuffer
    fb.fFormat = GL_RGBA8;
    GrBackendRenderTarget target = GrBackendRenderTargets::MakeGL(width_, height_, /*sampleCnt=*/0, /*stencilBits=*/8, fb);
    SkSurfaceProps props(0, kRGB_H_SkPixelGeometry);
    skSurface_ = SkSurfaces::WrapBackendRenderTarget(
        grCtx_.get(), target, kBottomLeft_GrSurfaceOrigin, kRGBA_8888_SkColorType, nullptr, &props);
    return skSurface_ != nullptr;
}

void RenderThread::renderFrame() {
    Viewport vp; uint32_t bg; std::vector<std::vector<uint8_t>> patches;
    {
        std::lock_guard<std::mutex> l(mu_);
        vp = viewport_; bg = background_; patches.swap(patchQueue_);
    }
    for (auto& p : patches)
        if (!scene_.applyPatch(p.data(), p.size())) LOGE("malformed scene patch dropped (%zu bytes)", p.size());

    if (!ensureSkSurface()) { LOGE("SkSurface unavailable"); return; }
    SkCanvas* canvas = skSurface_->getCanvas();
    canvas->resetMatrix();
    canvas->clear(static_cast<SkColor>(bg));

    canvas->concat(SkMatrix::MakeAll(vp.m[0], vp.m[2], vp.m[4], vp.m[1], vp.m[3], vp.m[5], 0, 0, 1));

    SkRect visible = SkRect::MakeWH(float(width_), float(height_));   // screen → world for culling
    SkMatrix inv;
    if (canvas->getTotalMatrix().invert(&inv)) inv.mapRect(&visible);

    scene_.draw(canvas, visible);
    // TODO(Phase 3): overlay pass in SCREEN space (resetMatrix) so handles/guides stay crisp at any zoom.

    grCtx_->flushAndSubmit();
    eglSwapBuffers(dpy_, surf_);
}

#else  // ── GLES fallback: clear + pan/zoom grid via scissored clears ──

void RenderThread::drawFallbackGrid(const Viewport& vp) {
    const float scale = vp.m[0];
    if (!(scale > 0.f) || !std::isfinite(vp.m[4]) || !std::isfinite(vp.m[5])) return;
    float step = 100.f * scale;
    while (step < 12.f) step *= 10.f;          // coarsen when zoomed far out

    glEnable(GL_SCISSOR_TEST);
    glClearColor(0.35f, 0.35f, 0.38f, 1.f);

    float x0 = std::fmod(vp.m[4], step); if (x0 < 0.f) x0 += step;
    for (float x = x0; x < float(width_); x += step) {
        glScissor(GLint(x), 0, 1, height_);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    float y0 = std::fmod(vp.m[5], step); if (y0 < 0.f) y0 += step;
    for (float y = y0; y < float(height_); y += step) {
        glScissor(0, height_ - 1 - GLint(y), width_, 1);   // GL origin is bottom-left
        glClear(GL_COLOR_BUFFER_BIT);
    }
    glDisable(GL_SCISSOR_TEST);
}

void RenderThread::renderFrame() {
    Viewport vp; uint32_t bg;
    { std::lock_guard<std::mutex> l(mu_); vp = viewport_; bg = background_; }
    glViewport(0, 0, width_, height_);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(((bg >> 16) & 0xFF) / 255.f, ((bg >> 8) & 0xFF) / 255.f, (bg & 0xFF) / 255.f, ((bg >> 24) & 0xFF) / 255.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    drawFallbackGrid(vp);
    eglSwapBuffers(dpy_, surf_);
}

#endif  // FIGTAB_HAS_SKIA

void RenderThread::loop() {
    for (;;) {
        std::unique_lock<std::mutex> l(mu_);
        cv_.wait(l, [this] { return quit_ || pendingCreate_ || pendingDestroy_ || pendingResize_ || frameRequested_; });
        if (quit_) break;

        if (pendingDestroy_) {
            // A create that raced with this destroy refers to a dead Surface: drop it.
            if (pendingWindow_) { ANativeWindow_release(pendingWindow_); pendingWindow_ = nullptr; }
            pendingCreate_ = false; pendingResize_ = false; frameRequested_ = false;
            l.unlock();
            teardownEgl();
            width_ = height_ = 0;
            l.lock();
            pendingDestroy_ = false;
            doneCv_.notify_all();
            continue;
        }

        if (pendingCreate_) {
            ANativeWindow* w = pendingWindow_;
            pendingWindow_ = nullptr; pendingCreate_ = false;
            l.unlock();
            if (window_) teardownEgl();
            window_ = w;
            if (!initEgl()) teardownEgl();
            l.lock();
        }
        if (pendingResize_) {
            width_ = reqW_; height_ = reqH_;
            pendingResize_ = false;
#if FIGTAB_HAS_SKIA
            skSurface_.reset();
#endif
        }
        const bool draw = frameRequested_ && window_ && width_ > 0 && height_ > 0;
        frameRequested_ = false;
        l.unlock();
        if (draw) renderFrame();
    }
    teardownEgl();
}

}  // namespace figtab
