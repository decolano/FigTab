package app.figtab.render

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.viewport.ViewportState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Hosts the native canvas and routes input:
 *  - stylus / mouse  → edit tools (select, move, resize, marquee)
 *  - finger          → navigation (pan, pinch-zoom); 2-finger tap = undo, 3-finger tap = redo
 *  - finger + [fingerEditing] → one finger edits, two fingers navigate (for devices without a stylus)
 */
class CanvasSurfaceView(
    context: Context,
    private val nativeCanvas: NativeCanvas,
    private val viewport: ViewportState,
    private val controller: DocumentController,
) : SurfaceView(context), SurfaceHolder.Callback {

    var onViewportChanged: ((ViewportState) -> Unit)? = null
    var fingerEditing: Boolean = false

    private enum class Mode { NONE, TOOL, NAV }

    private val frameQueued = AtomicBoolean(false)
    private val frameCallback = Choreographer.FrameCallback {
        frameQueued.set(false)
        nativeCanvas.requestFrame()
    }

    private val patchBuffer = PatchBuffer()
    private val overlayBuffer = PatchBuffer(4096)

    private var mode = Mode.NONE
    private var toolIsFinger = false
    private var maxPointers = 0
    private var downTime = 0L
    private var travel = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoomAt(detector.focusX, detector.focusY, detector.scaleFactor)
            pushViewport()
            return true
        }
    })

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        controller.addListener { afterEdit() }
        afterEdit()   // pushes the initial scene
    }

    /** Safe from any thread; at most one native frame per vsync. */
    fun invalidateCanvas() {
        if (!frameQueued.compareAndSet(false, true)) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            post { Choreographer.getInstance().postFrameCallback(frameCallback) }
        }
    }

    // ───────── scene + overlay sync ─────────

    private fun afterEdit() {
        val ch = controller.takeChanges()
        if (ch.full) {
            ScenePatchEncoder.encodeFull(controller.canvas, patchBuffer)
            nativeCanvas.applyPatch(patchBuffer.buffer, patchBuffer.length)
        } else if (ch.ids.isNotEmpty()) {
            ScenePatchEncoder.encodeNodes(controller.canvas, ch.ids, patchBuffer)
            nativeCanvas.applyPatch(patchBuffer.buffer, patchBuffer.length)
        }
        pushOverlay()
        invalidateCanvas()
    }

    private fun pushOverlay() {
        OverlayEncoder.encode(controller.overlay(), overlayBuffer)
        nativeCanvas.setOverlay(overlayBuffer.buffer, overlayBuffer.length)
    }

    private fun pushViewport() {
        val s = viewport.scale
        nativeCanvas.setViewport(s, 0f, 0f, s, viewport.tx, viewport.ty)
        pushOverlay()
        invalidateCanvas()
        onViewportChanged?.invoke(viewport)
    }

    // ───────── touch ─────────

    private fun addModifier(e: MotionEvent): Boolean =
        (e.metaState and KeyEvent.META_SHIFT_ON) != 0 || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

    /** Re-anchors the pan to the centroid of the pointers still down (excluding [skipIndex]). */
    private fun navAnchor(e: MotionEvent, skipIndex: Int) {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skipIndex) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        if (n > 0) { lastX = sx / n; lastY = sy / n }
    }

    private fun navMove(e: MotionEvent) {
        var sx = 0f
        var sy = 0f
        for (i in 0 until e.pointerCount) { sx += e.getX(i); sy += e.getY(i) }
        sx /= e.pointerCount; sy /= e.pointerCount
        val dx = sx - lastX
        val dy = sy - lastY
        viewport.panBy(dx, dy)
        travel += hypot(dx, dy)
        lastX = sx; lastY = sy
        pushViewport()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                maxPointers = 1
                downTime = e.eventTime
                travel = 0f
                val type = e.getToolType(0)
                val isTool = type == MotionEvent.TOOL_TYPE_STYLUS ||
                    type == MotionEvent.TOOL_TYPE_MOUSE ||
                    (type == MotionEvent.TOOL_TYPE_FINGER && fingerEditing)
                if (isTool) {
                    mode = Mode.TOOL
                    toolIsFinger = type == MotionEvent.TOOL_TYPE_FINGER
                    controller.pointerDown(e.x, e.y, addModifier(e))
                } else {
                    mode = Mode.NAV
                    scaleDetector.onTouchEvent(e)
                    navAnchor(e, -1)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                if (mode == Mode.TOOL && toolIsFinger) {
                    controller.cancelGesture()   // a second finger means "navigate", not "edit"
                    mode = Mode.NAV
                }
                if (mode == Mode.NAV) {
                    scaleDetector.onTouchEvent(e)
                    navAnchor(e, -1)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.TOOL) {
                    controller.pointerMove(e.x, e.y, (e.metaState and KeyEvent.META_SHIFT_ON) != 0)
                } else if (mode == Mode.NAV) {
                    scaleDetector.onTouchEvent(e)
                    navMove(e)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.NAV) {
                    scaleDetector.onTouchEvent(e)
                    navAnchor(e, e.actionIndex)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.TOOL) {
                    controller.pointerUp()
                } else if (mode == Mode.NAV) {
                    scaleDetector.onTouchEvent(e)
                    val quick = e.eventTime - downTime < TAP_MS && travel < TAP_SLOP_PX
                    if (quick && maxPointers == 2) controller.undo()
                    else if (quick && maxPointers == 3) controller.redo()
                }
                mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.TOOL) controller.cancelGesture() else scaleDetector.onTouchEvent(e)
                mode = Mode.NONE
            }
        }
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_SCROLL && e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val h = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if ((e.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON)) != 0) {
                viewport.zoomAt(e.x, e.y, Math.pow(1.1, v.toDouble()).toFloat())
            } else {
                viewport.panBy(h * 48f, v * 48f)
            }
            pushViewport()
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ───────── keyboard ─────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val shift = event.isShiftPressed
        val step = if (shift) 10f else 1f
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> { controller.deleteSelection(); true }
            KeyEvent.KEYCODE_ESCAPE -> { controller.clearSelection(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { controller.nudge(-step, 0f); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { controller.nudge(step, 0f); true }
            KeyEvent.KEYCODE_DPAD_UP -> { controller.nudge(0f, -step); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { controller.nudge(0f, step); true }
            KeyEvent.KEYCODE_Z -> {
                if (ctrl) { if (shift) controller.redo() else controller.undo(); true } else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_A -> {
                if (ctrl) { controller.selectAll(); true } else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ───────── surface lifecycle ─────────

    override fun surfaceCreated(holder: SurfaceHolder) = nativeCanvas.surfaceCreated(holder.surface)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        nativeCanvas.surfaceChanged(width, height, resources.displayMetrics.density)
        pushViewport()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = nativeCanvas.surfaceDestroyed()

    private companion object {
        const val TAP_MS = 350L
        const val TAP_SLOP_PX = 24f
    }
}
