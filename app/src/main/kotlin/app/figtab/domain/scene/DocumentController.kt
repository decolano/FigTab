package app.figtab.domain.scene

import app.figtab.domain.viewport.ViewportState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Handle { TL, T, TR, R, BR, B, BL, L }

/** What changed since the last call to [DocumentController.takeChanges]. */
data class Changes(val full: Boolean, val ids: Set<NodeId>)

/**
 * Owns the live document, the selection and the pointer-driven edit gestures
 * (select, move, resize, marquee) plus undo/redo. Pure Kotlin: no Android types.
 */
class DocumentController(initial: Canvas, private val viewport: ViewportState) {

    var canvas: Canvas = initial
        private set
    var selection: Set<NodeId> = emptySet()
        private set
    var multiSelectMode: Boolean = false
        set(value) { field = value; notifyListeners() }

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    private val history = History()
    private val listeners = ArrayList<() -> Unit>()
    private val dirty = LinkedHashSet<NodeId>()
    private var fullDirty = true
    private var guides: List<SnapEngine.Guide> = emptyList()
    private var gesture: Gesture = Gesture.Idle

    private sealed interface Gesture {
        object Idle : Gesture
        class Pending(
            val downScreen: Vec2, val downWorld: Vec2, val hit: NodeId?, val handle: Handle?, val add: Boolean,
            val startCanvas: Canvas, val startSelection: Set<NodeId>,
        ) : Gesture
        class Moving(
            val startWorld: Vec2, val startBounds: Rect, val originals: Map<NodeId, SceneNode>,
            val targets: List<Rect>, val startCanvas: Canvas, val startSelection: Set<NodeId>,
        ) : Gesture
        class Resizing(
            val handle: Handle, val startWorld: Vec2, val startBounds: Rect, val originals: Map<NodeId, SceneNode>,
            val worlds: Map<NodeId, Affine2D>, val parents: Map<NodeId, Affine2D>, val targets: List<Rect>,
            val startCanvas: Canvas, val startSelection: Set<NodeId>,
        ) : Gesture
        class Marquee(
            val startWorld: Vec2, var current: Vec2, val add: Boolean, val base: Set<NodeId>,
            val scope: List<NodeId>, val startCanvas: Canvas, val startSelection: Set<NodeId>,
        ) : Gesture
    }

    // ───────── listeners & change tracking ─────────

    fun addListener(l: () -> Unit) { listeners.add(l) }
    private fun notifyListeners() { for (l in listeners.toList()) l() }

    fun takeChanges(): Changes {
        val c = Changes(fullDirty, LinkedHashSet(dirty))
        dirty.clear()
        fullDirty = false
        return c
    }

    // ───────── pointer gestures (screen coordinates) ─────────

    fun pointerDown(sx: Float, sy: Float, addModifier: Boolean) {
        cancelInternal()
        val add = addModifier || multiSelectMode
        val world = viewport.screenToWorld(sx, sy)
        val handle = hitHandle(sx, sy)
        val hit = if (handle == null) HitTester.pick(canvas, world, 4f / viewport.scale) else null
        val startSel = selection
        gesture = Gesture.Pending(Vec2(sx, sy), world, hit, handle, add, canvas, startSel)

        // Immediate feedback: pressing an unselected object selects it (top-level frames wait: a drag
        // on one starts a marquee inside it instead).
        if (hit != null && hit !in selection && !isRootContainer(hit)) {
            selection = if (add) selection + hit else setOf(hit)
            notifyListeners()
        }
    }

    fun pointerMove(sx: Float, sy: Float, keepAspect: Boolean) {
        val world = viewport.screenToWorld(sx, sy)
        val g = gesture
        if (g is Gesture.Idle) return
        if (g is Gesture.Pending) {
            if (hypot(sx - g.downScreen.x, sy - g.downScreen.y) < DRAG_SLOP_PX) return
            beginDrag(g)
        }
        updateActive(world, keepAspect)
        notifyListeners()
    }

    fun pointerUp() {
        val g = gesture
        when (g) {
            is Gesture.Pending -> {
                val hit = g.hit
                selection = when {
                    g.handle != null -> g.startSelection
                    hit != null && g.add && hit in g.startSelection -> g.startSelection - hit
                    hit != null && g.add -> g.startSelection + hit
                    hit != null -> setOf(hit)
                    g.add -> g.startSelection
                    else -> emptySet()
                }
            }
            is Gesture.Moving -> commit(g.startCanvas, g.startSelection)
            is Gesture.Resizing -> commit(g.startCanvas, g.startSelection)
            else -> Unit
        }
        gesture = Gesture.Idle
        guides = emptyList()
        notifyListeners()
    }

    /** Aborts the current gesture and restores the state it started from (e.g. a second finger lands). */
    fun cancelGesture() {
        cancelInternal()
        notifyListeners()
    }

    private fun cancelInternal() {
        when (val g = gesture) {
            is Gesture.Pending -> selection = g.startSelection
            is Gesture.Moving -> { canvas = g.startCanvas; selection = g.startSelection; fullDirty = true }
            is Gesture.Resizing -> { canvas = g.startCanvas; selection = g.startSelection; fullDirty = true }
            is Gesture.Marquee -> selection = g.startSelection
            else -> Unit
        }
        gesture = Gesture.Idle
        guides = emptyList()
    }

    private fun commit(start: Canvas, startSel: Set<NodeId>) {
        if (canvas !== start) history.push(Snapshot(start, startSel))
    }

    private fun beginDrag(g: Gesture.Pending) {
        val handle = g.handle
        val hit = g.hit
        gesture = when {
            handle != null -> startResize(g, handle)
            hit != null && hit in selection -> startMove(g)
            hit != null -> {
                val kids = (canvas[hit] as? ContainerNode)?.children?.toList() ?: emptyList()
                Gesture.Marquee(g.downWorld, g.downWorld, g.add, g.startSelection, kids, g.startCanvas, g.startSelection)
            }
            else -> Gesture.Marquee(g.downWorld, g.downWorld, g.add, g.startSelection, canvas.roots.toList(), g.startCanvas, g.startSelection)
        }
    }

    private fun startMove(g: Gesture.Pending): Gesture {
        val ids = topLevelSelection()
        val originals = ids.mapNotNull { id -> canvas[id]?.let { id to it } }.toMap()
        val bounds = unionBounds(ids) ?: return Gesture.Idle
        return Gesture.Moving(g.downWorld, bounds, originals, snapTargets(ids), canvas, g.startSelection)
    }

    private fun startResize(g: Gesture.Pending, handle: Handle): Gesture {
        val ids = topLevelSelection()
        val originals = ids.mapNotNull { id -> canvas[id]?.let { id to it } }.toMap()
        val bounds = unionBounds(ids) ?: return Gesture.Idle
        val worlds = HashMap<NodeId, Affine2D>()
        val parents = HashMap<NodeId, Affine2D>()
        for ((id, n) in originals) {
            worlds[id] = canvas.worldTransform(id)
            parents[id] = parentWorld(canvas, n)
        }
        return Gesture.Resizing(handle, g.downWorld, bounds, originals, worlds, parents, snapTargets(ids), canvas, g.startSelection)
    }

    private fun updateActive(world: Vec2, keepAspect: Boolean) {
        when (val g = gesture) {
            is Gesture.Moving -> updateMoving(g, world)
            is Gesture.Resizing -> updateResizing(g, world, keepAspect)
            is Gesture.Marquee -> updateMarquee(g, world)
            else -> Unit
        }
    }

    private fun updateMoving(g: Gesture.Moving, world: Vec2) {
        val rawX = world.x - g.startWorld.x
        val rawY = world.y - g.startWorld.y
        val snap = SnapEngine.snapMove(g.startBounds.offset(rawX, rawY), g.targets, SNAP_PX / viewport.scale)
        val dx = rawX + snap.dx
        val dy = rawY + snap.dy
        guides = snap.guides
        var c = g.startCanvas
        for ((id, orig) in g.originals) {
            val inv = parentWorld(g.startCanvas, orig).invert() ?: continue
            val d = inv.mapVector(Vec2(dx, dy))
            val t = orig.transform
            c = c.withNode(orig.withGeometry(t.copy(tx = t.tx + d.x, ty = t.ty + d.y), orig.width, orig.height))
            dirty.add(id)
        }
        canvas = c
    }

    private fun updateResizing(g: Gesture.Resizing, world: Vec2, keepAspect: Boolean) {
        val sb = g.startBounds
        val dx = world.x - g.startWorld.x
        val dy = world.y - g.startWorld.y
        var l = sb.x
        var t = sb.y
        var r = sb.right
        var b = sb.bottom
        val hd = g.handle
        val movesL = hd == Handle.TL || hd == Handle.L || hd == Handle.BL
        val movesR = hd == Handle.TR || hd == Handle.R || hd == Handle.BR
        val movesT = hd == Handle.TL || hd == Handle.T || hd == Handle.TR
        val movesB = hd == Handle.BL || hd == Handle.B || hd == Handle.BR
        if (movesL) l += dx
        if (movesR) r += dx
        if (movesT) t += dy
        if (movesB) b += dy

        val th = SNAP_PX / viewport.scale
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val xc = SnapEngine.candidatesX(g.targets)
        val yc = SnapEngine.candidatesY(g.targets)
        if (movesL) SnapEngine.snapEdge(l, xc, th)?.let { l = it; xs.add(it) }
        if (movesR) SnapEngine.snapEdge(r, xc, th)?.let { r = it; xs.add(it) }
        if (movesT) SnapEngine.snapEdge(t, yc, th)?.let { t = it; ys.add(it) }
        if (movesB) SnapEngine.snapEdge(b, yc, th)?.let { b = it; ys.add(it) }

        if (keepAspect && sb.w > 0f && sb.h > 0f && (movesL || movesR) && (movesT || movesB)) {
            val ratio = sb.w / sb.h
            var nw = r - l
            var nh = b - t
            if (nw / ratio > nh) nh = nw / ratio else nw = nh * ratio
            if (movesL) l = r - nw else r = l + nw
            if (movesT) t = b - nh else b = t + nh
        }
        if (r - l < MIN_SIZE) { if (movesL) l = r - MIN_SIZE else r = l + MIN_SIZE }
        if (b - t < MIN_SIZE) { if (movesT) t = b - MIN_SIZE else b = t + MIN_SIZE }

        val nb = Rect(l, t, r - l, b - t)
        val sx = if (sb.w > 0.0001f) nb.w / sb.w else 1f
        val sy = if (sb.h > 0.0001f) nb.h / sb.h else 1f

        var c = g.startCanvas
        for ((id, orig) in g.originals) {
            val w = g.worlds[id] ?: continue
            val p = g.parents[id] ?: continue
            if (p.a <= 0f || p.d <= 0f || w.a <= 0f || w.d <= 0f) continue
            val ow = orig.width * w.a
            val oh = orig.height * w.d
            val nx = nb.x + (w.tx - sb.x) * sx
            val ny = nb.y + (w.ty - sb.y) * sy
            val t0 = orig.transform
            val resized = orig.withGeometry(
                t0.copy(tx = (nx - p.tx) / p.a, ty = (ny - p.ty) / p.d),
                (ow * sx) / w.a,
                (oh * sy) / w.d,
            ).fixedIfText()
            c = c.withNode(resized)
        }
        canvas = c
        dirty.addAll(g.originals.keys)
        guides = SnapEngine.guides(nb, g.targets, xs, ys)
    }

    private fun updateMarquee(g: Gesture.Marquee, world: Vec2) {
        g.current = world
        val rect = marqueeRect(g)
        val hits = g.scope.filter { id ->
            val n = canvas[id]
            n != null && n.visible && !n.locked && (canvas.worldBounds(id)?.intersects(rect) == true)
        }
        selection = if (g.add) g.base + hits else hits.toSet()
    }

    private fun marqueeRect(g: Gesture.Marquee): Rect {
        val x = min(g.startWorld.x, g.current.x)
        val y = min(g.startWorld.y, g.current.y)
        return Rect(x, y, abs(g.current.x - g.startWorld.x), abs(g.current.y - g.startWorld.y))
    }

    // ───────── commands ─────────

    fun undo() {
        val s = history.undo(Snapshot(canvas, selection)) ?: return
        apply(s)
    }

    fun redo() {
        val s = history.redo(Snapshot(canvas, selection)) ?: return
        apply(s)
    }

    private fun apply(s: Snapshot) {
        canvas = s.canvas
        selection = s.selection.filter { canvas[it] != null }.toSet()
        fullDirty = true
        gesture = Gesture.Idle
        guides = emptyList()
        notifyListeners()
    }

    fun selectAll() {
        selection = canvas.roots.toSet()
        notifyListeners()
    }

    fun clearSelection() {
        selection = emptySet()
        notifyListeners()
    }

    fun deleteSelection() {
        val ids = topLevelSelection()
        if (ids.isEmpty()) return
        history.push(Snapshot(canvas, selection))
        canvas = canvas.removeSubtrees(ids.toSet())
        selection = emptySet()
        fullDirty = true
        notifyListeners()
    }

    /** Moves the selection by (dx, dy) world units. */
    fun nudge(dx: Float, dy: Float) {
        val ids = topLevelSelection()
        if (ids.isEmpty()) return
        history.push(Snapshot(canvas, selection))
        var c = canvas
        for (id in ids) {
            val n = c[id] ?: continue
            val inv = parentWorld(c, n).invert() ?: continue
            val d = inv.mapVector(Vec2(dx, dy))
            val t = n.transform
            c = c.withNode(n.withGeometry(t.copy(tx = t.tx + d.x, ty = t.ty + d.y), n.width, n.height))
            dirty.add(id)
        }
        canvas = c
        notifyListeners()
    }

    fun selectionSummary(): String {
        val ids = topLevelSelection()
        if (selection.isEmpty()) return "Nothing selected"
        val b = unionBounds(ids)
        val size = if (b != null) " · ${b.w.roundToInt()}×${b.h.roundToInt()}" else ""
        return if (selection.size == 1) canvas[selection.first()]?.name.orEmpty() + size else "${selection.size} selected$size"
    }

    // ───────── overlay ─────────

    fun overlay(): List<OverlayPrim> {
        val out = ArrayList<OverlayPrim>()
        val ids = selection.filter { canvas[it] != null }
        for (id in ids) {
            val b = canvas.worldBounds(id) ?: continue
            out.add(strokeRect(b, BLUE, 1.5f))
        }
        val top = topLevelSelection()
        val box = unionBounds(top)
        if (box != null && ids.isNotEmpty()) {
            if (ids.size > 1) out.add(strokeRect(box, BLUE, 1.5f))
            if (canResize(top) && gesture !is Gesture.Marquee) {
                for ((_, p) in handlePoints(box)) {
                    val s = w2s(p)
                    out.add(OverlayPrim.RectFill(s.x - HANDLE_PX / 2f, s.y - HANDLE_PX / 2f, HANDLE_PX, HANDLE_PX, WHITE))
                    out.add(OverlayPrim.RectStroke(s.x - HANDLE_PX / 2f, s.y - HANDLE_PX / 2f, HANDLE_PX, HANDLE_PX, BLUE, 1.5f))
                }
            }
        }
        for (gd in guides) {
            val a = w2s(Vec2(gd.x1, gd.y1))
            val b = w2s(Vec2(gd.x2, gd.y2))
            out.add(OverlayPrim.Line(a.x, a.y, b.x, b.y, GUIDE, 1f))
        }
        val g = gesture
        if (g is Gesture.Marquee) {
            val r = marqueeRect(g)
            val a = w2s(Vec2(r.x, r.y))
            val b = w2s(Vec2(r.right, r.bottom))
            out.add(OverlayPrim.RectFill(a.x, a.y, b.x - a.x, b.y - a.y, MARQUEE_FILL))
            out.add(OverlayPrim.RectStroke(a.x, a.y, b.x - a.x, b.y - a.y, BLUE, 1f))
        }
        return out
    }

    // ───────── helpers ─────────

    private fun w2s(p: Vec2) = Vec2(p.x * viewport.scale + viewport.tx, p.y * viewport.scale + viewport.ty)

    private fun strokeRect(r: Rect, color: Int, width: Float): OverlayPrim {
        val a = w2s(Vec2(r.x, r.y))
        val b = w2s(Vec2(r.right, r.bottom))
        return OverlayPrim.RectStroke(a.x, a.y, b.x - a.x, b.y - a.y, color, width)
    }

    private fun parentWorld(c: Canvas, n: SceneNode): Affine2D {
        val pid = n.parent ?: return Affine2D.Identity
        return c.worldTransform(pid)
    }

    private fun isRootContainer(id: NodeId): Boolean {
        val n = canvas[id] ?: return false
        return n.parent == null && (n is FrameNode || n is SectionNode)
    }

    /** Selected nodes whose ancestors are not also selected (so a moved frame doesn't double-move its children). */
    private fun topLevelSelection(): List<NodeId> {
        val sel = selection
        val out = ArrayList<NodeId>()
        for (id in sel) {
            if (canvas[id] == null) continue
            var p = canvas[id]?.parent
            var covered = false
            while (p != null) {
                if (p in sel) { covered = true; break }
                p = canvas[p]?.parent
            }
            if (!covered) out.add(id)
        }
        return out
    }

    private fun unionBounds(ids: List<NodeId>): Rect? {
        var acc: Rect? = null
        for (id in ids) {
            val b = canvas.worldBounds(id) ?: continue
            acc = acc?.union(b) ?: b
        }
        return acc
    }

    private fun snapTargets(ids: List<NodeId>): List<Rect> {
        if (ids.isEmpty()) return emptyList()
        val first = canvas[ids.first()] ?: return emptyList()
        val parentId = first.parent
        val siblings: List<NodeId> =
            if (parentId == null) canvas.roots.toList()
            else (canvas[parentId] as? ContainerNode)?.children?.toList() ?: emptyList()
        val out = ArrayList<Rect>()
        for (s in siblings) {
            if (s in selection) continue
            val n = canvas[s] ?: continue
            if (!n.visible) continue
            canvas.worldBounds(s)?.let { out.add(it) }
        }
        if (parentId != null) canvas.worldBounds(parentId)?.let { out.add(it) }
        return out
    }

    /** Resizing works for axis-aligned, non-group selections (rotation and group scaling come later). */
    private fun canResize(ids: List<NodeId>): Boolean {
        if (ids.isEmpty()) return false
        for (id in ids) {
            val n = canvas[id] ?: return false
            if (n is GroupNode) return false
            val w = canvas.worldTransform(id)
            if (abs(w.b) > 1e-4f || abs(w.c) > 1e-4f || w.a <= 0f || w.d <= 0f) return false
        }
        return true
    }

    private fun handlePoints(r: Rect): List<Pair<Handle, Vec2>> = listOf(
        Handle.TL to Vec2(r.x, r.y),
        Handle.T to Vec2(r.x + r.w / 2f, r.y),
        Handle.TR to Vec2(r.right, r.y),
        Handle.R to Vec2(r.right, r.y + r.h / 2f),
        Handle.BR to Vec2(r.right, r.bottom),
        Handle.B to Vec2(r.x + r.w / 2f, r.bottom),
        Handle.BL to Vec2(r.x, r.bottom),
        Handle.L to Vec2(r.x, r.y + r.h / 2f),
    )

    private fun hitHandle(sx: Float, sy: Float): Handle? {
        val top = topLevelSelection()
        if (!canResize(top)) return null
        val box = unionBounds(top) ?: return null
        var best: Handle? = null
        var bestDist = HANDLE_HIT_PX
        for ((hd, p) in handlePoints(box)) {
            val s = w2s(p)
            val d = hypot(s.x - sx, s.y - sy)
            if (d <= bestDist) { bestDist = d; best = hd }
        }
        return best
    }

    private companion object {
        const val DRAG_SLOP_PX = 6f
        const val HANDLE_HIT_PX = 22f
        const val HANDLE_PX = 10f
        const val SNAP_PX = 6f
        const val MIN_SIZE = 1f
        val BLUE = 0xFF0D99FF.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val GUIDE = 0xFFFF3B6B.toInt()
        val MARQUEE_FILL = 0x330D99FF
    }
}
