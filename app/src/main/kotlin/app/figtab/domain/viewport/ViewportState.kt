package app.figtab.domain.viewport

import app.figtab.domain.scene.Vec2

/**
 * World → screen transform: screen = scale * world + (tx, ty). Uniform scale, no rotation.
 * Zoom is clamped to 1.5% … 25,600%.
 */
class ViewportState {
    var scale: Float = 1f
        private set
    var tx: Float = 0f
        private set
    var ty: Float = 0f
        private set

    fun panBy(dx: Float, dy: Float) { tx += dx; ty += dy }

    /** Multiply zoom by [factor], keeping the world point under screen point (fx, fy) fixed. */
    fun zoomAt(fx: Float, fy: Float, factor: Float) {
        if (factor.isNaN() || factor <= 0f) return
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = next / scale
        tx = fx - (fx - tx) * k
        ty = fy - (fy - ty) * k
        scale = next
    }

    fun screenToWorld(x: Float, y: Float) = Vec2((x - tx) / scale, (y - ty) / scale)

    companion object {
        const val MIN_SCALE = 0.015f
        const val MAX_SCALE = 256f
    }
}
