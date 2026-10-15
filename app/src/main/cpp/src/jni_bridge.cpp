#include <android/native_window_jni.h>
#include <jni.h>
#include <cstdint>
#include "render_thread.h"

using figtab::RenderThread;

namespace {
inline RenderThread* H(jlong h) { return reinterpret_cast<RenderThread*>(h); }
}

#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_app_figtab_render_NativeCanvas_##name

FN(jlong, nativeCreate)(JNIEnv*, jobject) { return reinterpret_cast<jlong>(new RenderThread()); }
FN(void, nativeDestroy)(JNIEnv*, jobject, jlong h) { delete H(h); }

FN(void, nativeSurfaceCreated)(JNIEnv* env, jobject, jlong h, jobject surface) {
    if (ANativeWindow* w = ANativeWindow_fromSurface(env, surface)) {   // +1 ref
        H(h)->surfaceCreated(w);                                         // RenderThread takes its own ref
        ANativeWindow_release(w);
    }
}
FN(void, nativeSurfaceChanged)(JNIEnv*, jobject, jlong h, jint w, jint hgt, jfloat d) { H(h)->surfaceChanged(w, hgt, d); }
FN(void, nativeSurfaceDestroyed)(JNIEnv*, jobject, jlong h) { H(h)->surfaceDestroyed(); }

FN(void, nativeSetViewport)(JNIEnv*, jobject, jlong h, jfloat a, jfloat b, jfloat c, jfloat d, jfloat tx, jfloat ty) {
    const float m[6] = {a, b, c, d, tx, ty};
    H(h)->setViewport(m);
}
FN(void, nativeSetBackground)(JNIEnv*, jobject, jlong h, jint argb) { H(h)->setBackground(static_cast<uint32_t>(argb)); }

FN(void, nativeApplyPatch)(JNIEnv* env, jobject, jlong h, jobject buf, jint len) {
    auto* p = static_cast<const uint8_t*>(env->GetDirectBufferAddress(buf));
    if (p && len > 0 && len <= env->GetDirectBufferCapacity(buf)) H(h)->enqueuePatch(p, size_t(len));
}
FN(void, nativeSetOverlay)(JNIEnv* env, jobject, jlong h, jobject buf, jint len) {
    auto* p = static_cast<const uint8_t*>(env->GetDirectBufferAddress(buf));
    if (p && len >= 0 && len <= env->GetDirectBufferCapacity(buf)) H(h)->enqueueOverlay(p, size_t(len));
}
FN(void, nativeRequestFrame)(JNIEnv*, jobject, jlong h) { H(h)->requestFrame(); }
