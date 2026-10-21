package app.figtab.domain.scene

/** Screen-space drawing primitives for selection chrome. Colors are ARGB ints. */
sealed interface OverlayPrim {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float) : OverlayPrim
    data class RectStroke(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int, val width: Float) : OverlayPrim
    data class RectFill(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int) : OverlayPrim
}
