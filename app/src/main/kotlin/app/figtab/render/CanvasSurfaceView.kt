package app.figtab.render

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import app.figtab.domain.viewport.ViewportState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosts the native canvas. Finger input is navigation only (pan / pinch-zoom); stylus and
 * mouse tool input arrives in Phase 3. Frame requests are coalesced to vsync.
 */
class CanvasSurfaceView(
    context: Context,
    private val nativeCanvas: NativeCanvas,
    private val viewport: ViewportState,
) : SurfaceView(context), SurfaceHolder.Callback {

    var onViewportChanged: ((ViewportState) -> Unit)? = null

    private val frameQueued = AtomicBoolean(false)
    private val frameCallback = Choreographer.FrameCallback {
        frameQueued.set(false)
        nativeCanvas.requestFrame()
    }

    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoomAt(detector.focusX, detector.focusY, detector.scaleFactor)
            pushViewport()
            return true
        }
    })

    init { holder.addCallback(this) }

    /** Safe from any thread; at most one native frame per vsync. */
    fun invalidateCanvas() {
        if (!frameQueued.compareAndSet(false, true)) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            post { Choreographer.getInstance().postFrameCallback(frameCallback) }
        }
    }

    private fun pushViewport() {
        val s = viewport.scale
        nativeCanvas.setViewport(s, 0f, 0f, s, viewport.tx, viewport.ty)
        invalidateCanvas()
        onViewportChanged?.invoke(viewport)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false   // stylus handled in Phase 3
        scaleDetector.onTouchEvent(e)

        // Centroid of the pointers that remain down (excluding one that is lifting).
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        if (n == 0) return true
        sx /= n; sy /= n

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                lastX = sx; lastY = sy
            }
            MotionEvent.ACTION_MOVE -> {
                viewport.panBy(sx - lastX, sy - lastY)
                lastX = sx; lastY = sy
                pushViewport()
            }
        }
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_SCROLL && e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val h = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (e.isCtrlPressed || e.isMetaPressed) {
                viewport.zoomAt(e.x, e.y, Math.pow(1.1, v.toDouble()).toFloat())
            } else {
                viewport.panBy(h * 48f, v * 48f)
            }
            pushViewport()
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = nativeCanvas.surfaceCreated(holder.surface)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        nativeCanvas.surfaceChanged(width, height, resources.displayMetrics.density)
        pushViewport()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = nativeCanvas.surfaceDestroyed()
}
