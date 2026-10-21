package app.figtab.domain.scene

import kotlinx.collections.immutable.PersistentList

// ───────── small geometry helpers ─────────

fun Affine2D.mapVector(v: Vec2) = Vec2(a * v.x + c * v.y, b * v.x + d * v.y)

fun Rect.offset(dx: Float, dy: Float) = Rect(x + dx, y + dy, w, h)
fun Rect.intersects(o: Rect): Boolean = x < o.right && right > o.x && y < o.bottom && bottom > o.y

/** World-space AABB of a node's local (0,0,w,h) box. */
fun boundsOfBox(m: Affine2D, w: Float, h: Float): Rect {
    val p0 = m.map(Vec2(0f, 0f)); val p1 = m.map(Vec2(w, 0f))
    val p2 = m.map(Vec2(w, h));   val p3 = m.map(Vec2(0f, h))
    val minX = minOf(minOf(p0.x, p1.x), minOf(p2.x, p3.x))
    val maxX = maxOf(maxOf(p0.x, p1.x), maxOf(p2.x, p3.x))
    val minY = minOf(minOf(p0.y, p1.y), minOf(p2.y, p3.y))
    val maxY = maxOf(maxOf(p0.y, p1.y), maxOf(p2.y, p3.y))
    return Rect(minX, minY, maxX - minX, maxY - minY)
}

/** World bounds of a node; groups derive theirs from their children. */
fun Canvas.worldBounds(id: NodeId): Rect? {
    val n = nodes[id] ?: return null
    if (n is GroupNode) {
        var acc: Rect? = null
        for (c in n.children) {
            val b = worldBounds(c) ?: continue
            acc = acc?.union(b) ?: b
        }
        return acc
    }
    return boundsOfBox(worldTransform(id), n.width, n.height)
}

// ───────── immutable node edits ─────────

fun SceneNode.withGeometry(transform: Affine2D, width: Float, height: Float): SceneNode = when (this) {
    is SectionNode -> copy(transform = transform, width = width, height = height)
    is FrameNode -> copy(transform = transform, width = width, height = height)
    is GroupNode -> copy(transform = transform, width = width, height = height)
    is ShapeNode -> copy(transform = transform, width = width, height = height)
    is VectorNetworkNode -> copy(transform = transform, width = width, height = height)
    is TextNode -> copy(transform = transform, width = width, height = height)
    is ComponentNode -> copy(transform = transform, width = width, height = height)
    is InstanceNode -> copy(transform = transform, width = width, height = height)
}

/** Resizing a text box switches it to a fixed-size box, like Figma. */
fun SceneNode.fixedIfText(): SceneNode {
    val n = this
    return if (n is TextNode && n.autoSize != TextAutoSize.FIXED) n.copy(autoSize = TextAutoSize.FIXED) else n
}

fun ContainerNode.withChildren(children: PersistentList<NodeId>): SceneNode = when (this) {
    is SectionNode -> copy(children = children)
    is FrameNode -> copy(children = children)
    is GroupNode -> copy(children = children)
    is ComponentNode -> copy(children = children)
}

fun Canvas.withNode(n: SceneNode): Canvas = copy(nodes = nodes.put(n.id, n))

/** Removes the given nodes and everything beneath them, detaching them from their parents. */
fun Canvas.removeSubtrees(ids: Set<NodeId>): Canvas {
    val doomed = LinkedHashSet<NodeId>()
    fun collect(id: NodeId) {
        if (!doomed.add(id)) return
        val n = nodes[id]
        if (n is ContainerNode) n.children.forEach { collect(it) }
    }
    ids.forEach { collect(it) }

    var newNodes = nodes
    var newRoots = roots
    for (id in ids) {
        val n = nodes[id] ?: continue
        val pid = n.parent
        if (pid == null) {
            newRoots = newRoots.remove(id)
        } else {
            val p = newNodes[pid]
            if (p is ContainerNode) newNodes = newNodes.put(pid, p.withChildren(p.children.remove(id)))
        }
    }
    for (id in doomed) newNodes = newNodes.remove(id)
    return copy(nodes = newNodes, roots = newRoots)
}
