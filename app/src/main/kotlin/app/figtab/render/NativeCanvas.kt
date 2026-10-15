package app.figtab.render

import android.view.Surface
import java.nio.ByteBuffer

/**
 * JNI contract between Kotlin and the native renderer.
 *
 *  - Only primitives and direct ByteBuffers cross the boundary; Skia types never do.
 *  - Viewport = 6 primitive floats (no allocation). Scene data = pooled direct buffers.
 *  - Surface lifecycle calls come from SurfaceHolder callbacks (main thread). Native BLOCKS on
 *    surfaceDestroyed so the Surface is never touched after it returns.
 *  - Every method is a no-op after [close], so late SurfaceView callbacks can't hit a freed handle.
 */
class NativeCanvas : AutoCloseable {
    private var handle: Long = nativeCreate()

    fun surfaceCreated(surface: Surface) { if (handle != 0L) nativeSurfaceCreated(handle, surface) }
    fun surfaceChanged(width: Int, height: Int, density: Float) { if (handle != 0L) nativeSurfaceChanged(handle, width, height, density) }
    fun surfaceDestroyed() { if (handle != 0L) nativeSurfaceDestroyed(handle) }

    /** World→screen transform in pixels; same convention as Affine2D (x' = a*x + c*y + tx). */
    fun setViewport(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float) {
        if (handle != 0L) nativeSetViewport(handle, a, b, c, d, tx, ty)
    }

    fun setBackground(argb: Int) { if (handle != 0L) nativeSetBackground(handle, argb) }

    /** Apply a binary scene patch (see scene_patch.h). [buf] must be direct; bytes [0, length) are read and copied. */
    fun applyPatch(buf: ByteBuffer, length: Int) {
        require(buf.isDirect) { "Patch buffer must be direct" }
        if (handle != 0L) nativeApplyPatch(handle, buf, length)
    }

    /** Overlay buffer (selection handles, snap guides…). Reserved for Phase 3. */
    fun setOverlay(buf: ByteBuffer, length: Int) {
        require(buf.isDirect) { "Overlay buffer must be direct" }
        if (handle != 0L) nativeSetOverlay(handle, buf, length)
    }

    /** Coalesced: many calls between vsyncs produce one frame. */
    fun requestFrame() { if (handle != 0L) nativeRequestFrame(handle) }

    override fun close() {
        val h = handle
        if (h != 0L) { handle = 0L; nativeDestroy(h) }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeSurfaceCreated(handle: Long, surface: Surface)
    private external fun nativeSurfaceChanged(handle: Long, w: Int, h: Int, density: Float)
    private external fun nativeSurfaceDestroyed(handle: Long)
    private external fun nativeSetViewport(handle: Long, a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float)
    private external fun nativeSetBackground(handle: Long, argb: Int)
    private external fun nativeApplyPatch(handle: Long, buf: ByteBuffer, length: Int)
    private external fun nativeSetOverlay(handle: Long, buf: ByteBuffer, length: Int)
    private external fun nativeRequestFrame(handle: Long)

    companion object {
        init { System.loadLibrary("figtab_native") }
    }
}
