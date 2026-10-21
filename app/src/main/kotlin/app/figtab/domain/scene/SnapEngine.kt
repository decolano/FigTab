package app.figtab.domain.scene

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Edge / center snapping against other boxes. All values are world coordinates. */
object SnapEngine {
    data class Guide(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    data class MoveResult(val dx: Float, val dy: Float, val guides: List<Guide>)

    private fun xLines(r: Rect) = floatArrayOf(r.x, r.x + r.w / 2f, r.right)
    private fun yLines(r: Rect) = floatArrayOf(r.y, r.y + r.h / 2f, r.bottom)

    fun candidatesX(targets: List<Rect>): List<Float> = targets.flatMap { xLines(it).toList() }
    fun candidatesY(targets: List<Rect>): List<Float> = targets.flatMap { yLines(it).toList() }

    fun snapMove(moving: Rect, targets: List<Rect>, threshold: Float): MoveResult {
        var bestDx = Float.NaN
        var bestDy = Float.NaN
        for (t in targets) {
            for (m in xLines(moving)) for (l in xLines(t)) {
                val d = l - m
                if (abs(d) <= threshold && (bestDx.isNaN() || abs(d) < abs(bestDx))) bestDx = d
            }
            for (m in yLines(moving)) for (l in yLines(t)) {
                val d = l - m
                if (abs(d) <= threshold && (bestDy.isNaN() || abs(d) < abs(bestDy))) bestDy = d
            }
        }
        val dx = if (bestDx.isNaN()) 0f else bestDx
        val dy = if (bestDy.isNaN()) 0f else bestDy
        val snapped = Rect(moving.x + dx, moving.y + dy, moving.w, moving.h)
        val xs = if (bestDx.isNaN()) emptyList() else xLines(snapped).toList()
        val ys = if (bestDy.isNaN()) emptyList() else yLines(snapped).toList()
        return MoveResult(dx, dy, guides(snapped, targets, xs, ys))
    }

    /** Returns the snapped value of a single dragged edge, or null if nothing is within [threshold]. */
    fun snapEdge(value: Float, lines: List<Float>, threshold: Float): Float? {
        var best: Float? = null
        var bestDist = Float.MAX_VALUE
        for (l in lines) {
            val d = abs(l - value)
            if (d <= threshold && d < bestDist) { bestDist = d; best = l }
        }
        return best
    }

    /** Guide lines for every x / y in [xs] / [ys] that lines up with at least one target. */
    fun guides(bounds: Rect, targets: List<Rect>, xs: List<Float>, ys: List<Float>): List<Guide> {
        val out = ArrayList<Guide>()
        for (x in xs) {
            var top = bounds.y
            var bottom = bounds.bottom
            var matched = false
            for (t in targets) {
                if (xLines(t).any { abs(it - x) < 0.5f }) {
                    matched = true
                    top = min(top, t.y)
                    bottom = max(bottom, t.bottom)
                }
            }
            if (matched) out.add(Guide(x, top, x, bottom))
        }
        for (y in ys) {
            var left = bounds.x
            var right = bounds.right
            var matched = false
            for (t in targets) {
                if (yLines(t).any { abs(it - y) < 0.5f }) {
                    matched = true
                    left = min(left, t.x)
                    right = max(right, t.right)
                }
            }
            if (matched) out.add(Guide(left, y, right, y))
        }
        return out
    }
}
