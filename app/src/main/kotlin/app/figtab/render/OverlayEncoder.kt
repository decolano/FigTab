package app.figtab.render

import app.figtab.domain.scene.OverlayPrim

/**
 * Encodes screen-space overlay primitives for the native overlay pass (see drawOverlay in render_thread.cpp).
 * Layout: u32 count, then per primitive 28 bytes: u32 type, f32 a, b, c, d, u32 argb, f32 width.
 */
object OverlayEncoder {
    fun encode(prims: List<OverlayPrim>, out: PatchBuffer) {
        out.reset()
        out.putInt(prims.size)
        for (p in prims) {
            when (p) {
                is OverlayPrim.Line -> rec(out, 0, p.x1, p.y1, p.x2, p.y2, p.color, p.width)
                is OverlayPrim.RectStroke -> rec(out, 1, p.x, p.y, p.w, p.h, p.color, p.width)
                is OverlayPrim.RectFill -> rec(out, 2, p.x, p.y, p.w, p.h, p.color, 0f)
            }
        }
    }

    private fun rec(out: PatchBuffer, type: Int, a: Float, b: Float, c: Float, d: Float, color: Int, width: Float) {
        out.putInt(type).putFloat(a).putFloat(b).putFloat(c).putFloat(d).putInt(color).putFloat(width)
    }
}
