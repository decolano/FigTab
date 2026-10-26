package app.figtab.domain.scene

import kotlin.math.sqrt

/** Picks the node under a world-space point. Groups are atomic; frames are selected only if no child is hit. */
object HitTester {
    fun pick(canvas: Canvas, world: Vec2, tolerance: Float): NodeId? {
        for (id in canvas.roots.asReversed()) {
            val hit = hitNode(canvas, id, world, tolerance)
            if (hit != null) return hit
        }
        return null
    }

    /** Deepest frame / section / component under the point: the parent for a newly drawn node. */
    fun pickContainer(canvas: Canvas, world: Vec2): NodeId? {
        for (id in canvas.roots.asReversed()) {
            val r = containerIn(canvas, id, world)
            if (r != null) return r
        }
        return null
    }

    private fun containerIn(canvas: Canvas, id: NodeId, pParent: Vec2): NodeId? {
        val n = canvas[id] ?: return null
        if (!n.visible || n.locked) return null
        if (n !is ContainerNode || n is GroupNode) return null
        val inv = n.transform.invert() ?: return null
        val p = inv.map(pParent)
        if (!inBox(n, p, 0f)) return null
        for (c in n.children.asReversed()) {
            val inner = containerIn(canvas, c, p)
            if (inner != null) return inner
        }
        return n.id
    }

    private fun hitNode(canvas: Canvas, id: NodeId, pParent: Vec2, tol: Float): NodeId? {
        val n = canvas[id] ?: return null
        if (!n.visible || n.locked) return null
        val inv = n.transform.invert() ?: return null
        val p = inv.map(pParent)

        if (n is GroupNode) {
            for (c in n.children.asReversed()) {
                if (hitNode(canvas, c, p, tol) != null) return n.id
            }
            return null
        }
        if (n is ContainerNode) {
            val clips = (n is FrameNode && n.clipsContent) || (n is ComponentNode && n.clipsContent)
            if (!clips || inBox(n, p, tol)) {
                val order: List<NodeId> = if (n.autoLayoutOrNull()?.reverseZ == true) n.children else n.children.asReversed()
                for (c in order) {
                    val h = hitNode(canvas, c, p, tol)
                    if (h != null) return h
                }
            }
        }
        return if (inShape(n, p, tol)) n.id else null
    }

    private fun inBox(n: SceneNode, p: Vec2, tol: Float): Boolean =
        p.x >= -tol && p.y >= -tol && p.x <= n.width + tol && p.y <= n.height + tol

    private fun inShape(n: SceneNode, p: Vec2, tol: Float): Boolean {
        if (n is ShapeNode && n.kind == ShapeKind.LINE) {
            val x = p.x.coerceIn(0f, n.width)
            val dx = p.x - x
            val dist = sqrt(dx * dx + p.y * p.y)
            val half = (n.stroke?.weight ?: 1f) / 2f
            return dist <= maxOf(tol, half)
        }
        if (n is ShapeNode && n.kind == ShapeKind.ELLIPSE) {
            val rx = n.width / 2f
            val ry = n.height / 2f
            if (rx <= 0f || ry <= 0f) return false
            val nx = (p.x - rx) / rx
            val ny = (p.y - ry) / ry
            return nx * nx + ny * ny <= 1f
        }
        return inBox(n, p, tol)
    }
}
