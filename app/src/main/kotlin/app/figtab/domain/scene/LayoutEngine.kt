package app.figtab.domain.scene

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Auto Layout solver plus the legacy constraint solver.
 *
 * Conventions:
 *  - "Fixed" means the node's current size (the px in AxisSizing.Fixed is informational).
 *  - Hidden children and children marked `absolute` do not take part in the flow.
 *  - Layout only touches translation and size; a child's rotation is left alone.
 *  - Running [apply] twice gives the same result (it is idempotent), so it can run after every edit.
 */
object LayoutEngine {
    private const val EPS = 0.01f
    private const val MAX_ROUNDS = 5

    data class Result(val canvas: Canvas, val changed: Set<NodeId>)

    private class Ctx(var canvas: Canvas) {
        val changed = LinkedHashSet<NodeId>()
        var edits = 0
        fun put(n: SceneNode) {
            canvas = canvas.withNode(n)
            changed.add(n.id)
            edits++
        }
    }

    private class Item(val node: SceneNode) {
        val lc: LayoutChild = node.layoutChild
        var ox = 0f
        var oy = 0f
        var w = node.width
        var h = node.height
        var nw = 0f
        var nh = 0f
        var x = 0f
        var y = 0f
    }

    /** Re-lays out every auto-layout container: bottom-up (hug sizes) then top-down (fill sizes), until stable. */
    fun apply(canvas: Canvas): Result {
        val ctx = Ctx(canvas)
        for (round in 0 until MAX_ROUNDS) {
            val before = ctx.edits
            for (r in ctx.canvas.roots.toList()) bottomUp(ctx, r)
            for (r in ctx.canvas.roots.toList()) topDown(ctx, r)
            if (ctx.edits == before) break
        }
        return Result(ctx.canvas, ctx.changed)
    }

    private fun bottomUp(ctx: Ctx, id: NodeId) {
        val n = ctx.canvas[id] ?: return
        if (n is ContainerNode) for (k in n.children.toList()) bottomUp(ctx, k)
        layoutNode(ctx, id)
    }

    private fun topDown(ctx: Ctx, id: NodeId) {
        layoutNode(ctx, id)
        val n = ctx.canvas[id] ?: return
        if (n is ContainerNode) for (k in n.children.toList()) topDown(ctx, k)
    }

    private fun layoutNode(ctx: Ctx, id: NodeId) {
        val n = ctx.canvas[id] ?: return
        if (n !is ContainerNode) return
        val al = n.autoLayoutOrNull() ?: return
        if (al.mode == LayoutMode.NONE) return
        layoutContainer(ctx, n, al)
    }

    // ───────── helpers ─────────

    private fun clampAxis(v: Float, mn: Float?, mx: Float?): Float {
        var r = v
        if (mn != null) r = max(r, mn)
        if (mx != null) r = min(r, mx)
        return r
    }

    private fun sizingOf(item: Item, isX: Boolean): AxisSizing = if (isX) item.lc.widthSizing else item.lc.heightSizing
    private fun minFor(item: Item, isX: Boolean): Float? = if (isX) item.lc.minW else item.lc.minH
    private fun maxFor(item: Item, isX: Boolean): Float? = if (isX) item.lc.maxW else item.lc.maxH

    private fun transformRect(m: Affine2D, r: Rect): Rect {
        val p0 = m.map(Vec2(r.x, r.y))
        val p1 = m.map(Vec2(r.right, r.y))
        val p2 = m.map(Vec2(r.right, r.bottom))
        val p3 = m.map(Vec2(r.x, r.bottom))
        val minX = min(min(p0.x, p1.x), min(p2.x, p3.x))
        val maxX = max(max(p0.x, p1.x), max(p2.x, p3.x))
        val minY = min(min(p0.y, p1.y), min(p2.y, p3.y))
        val maxY = max(max(p0.y, p1.y), max(p2.y, p3.y))
        return Rect(minX, minY, maxX - minX, maxY - minY)
    }

    /** Local-space box of a node. Groups derive theirs from their children. */
    private fun localBox(c: Canvas, n: SceneNode): Rect {
        if (n is GroupNode) {
            var acc: Rect? = null
            for (k in n.children) {
                val ch = c[k] ?: continue
                val b = transformRect(ch.transform, localBox(c, ch))
                acc = acc?.union(b) ?: b
            }
            return acc ?: Rect(0f, 0f, 0f, 0f)
        }
        return Rect(0f, 0f, n.width, n.height)
    }

    private fun makeItem(c: Canvas, ch: SceneNode): Item {
        val item = Item(ch)
        if (ch is GroupNode) {
            val b = localBox(c, ch)
            item.ox = b.x
            item.oy = b.y
            item.w = b.w
            item.h = b.h
        }
        return item
    }

    // ───────── auto layout ─────────

    private fun layoutContainer(ctx: Ctx, f: ContainerNode, al: AutoLayout) {
        val c = ctx.canvas
        val horizontal = al.mode != LayoutMode.VERTICAL
        val wrap = al.mode == LayoutMode.WRAP
        val pad = al.padding

        val items = ArrayList<Item>()
        for (id in f.children) {
            val ch = c[id] ?: continue
            if (!ch.visible || ch.layoutChild.absolute) continue
            items.add(makeItem(c, ch))
        }

        val hugW = f.layoutChild.widthSizing is AxisSizing.HugContent
        val hugH = f.layoutChild.heightSizing is AxisSizing.HugContent
        var frameW = f.width
        var frameH = f.height
        val innerW = f.width - pad.left - pad.right
        val innerH = f.height - pad.top - pad.bottom

        if (items.isEmpty()) {
            if (hugW) frameW = pad.left + pad.right
            if (hugH) frameH = pad.top + pad.bottom
        } else if (wrap) {
            val availW = if (hugW) Float.MAX_VALUE else innerW
            val lines = ArrayList<List<Item>>()
            var cur = ArrayList<Item>()
            var curW = 0f
            for (item in items) {
                if (cur.isNotEmpty() && curW + al.gap + item.w > availW + EPS) {
                    lines.add(cur)
                    cur = ArrayList()
                    curW = 0f
                }
                if (cur.isNotEmpty()) curW += al.gap
                cur.add(item)
                curW += item.w
            }
            if (cur.isNotEmpty()) lines.add(cur)

            var y = pad.top
            var maxLineW = 0f
            for (line in lines) {
                var sumW = 0f
                var lineH = 0f
                for (item in line) {
                    sumW += item.w
                    lineH = max(lineH, item.h)
                }
                val lineW = sumW + al.gap * (line.size - 1)
                maxLineW = max(maxLineW, lineW)
                val room = if (hugW) lineW else innerW
                var x = pad.left
                var between = al.gap
                when (al.primaryAlign) {
                    PrimaryAlign.START -> Unit
                    PrimaryAlign.CENTER -> x += (room - lineW) / 2f
                    PrimaryAlign.END -> x += room - lineW
                    PrimaryAlign.SPACE_BETWEEN -> {
                        if (line.size > 1 && !hugW) between = max(0f, (innerW - sumW) / (line.size - 1))
                    }
                }
                for (item in line) {
                    item.nw = item.w
                    item.nh = if (al.counterAlign == CounterAlign.STRETCH) lineH else item.h
                    item.x = x
                    val off = when (al.counterAlign) {
                        CounterAlign.START, CounterAlign.BASELINE, CounterAlign.STRETCH -> 0f
                        CounterAlign.CENTER -> (lineH - item.nh) / 2f
                        CounterAlign.END -> lineH - item.nh
                    }
                    item.y = y + off
                    x += item.w + between
                }
                y += lineH + al.crossGap
            }
            val contentH = y - al.crossGap - pad.top
            if (hugW) frameW = maxLineW + pad.left + pad.right
            if (hugH) frameH = contentH + pad.top + pad.bottom
        } else {
            val n = items.size
            val ps = FloatArray(n)
            val cs = FloatArray(n)
            for (i in 0 until n) {
                ps[i] = if (horizontal) items[i].w else items[i].h
                cs[i] = if (horizontal) items[i].h else items[i].w
            }
            val primHug = if (horizontal) hugW else hugH
            val counterHug = if (horizontal) hugH else hugW
            val padPS = if (horizontal) pad.left else pad.top
            val padPE = if (horizontal) pad.right else pad.bottom
            val padCS = if (horizontal) pad.top else pad.left
            val padCE = if (horizontal) pad.bottom else pad.right
            val availP = if (horizontal) innerW else innerH
            val availC = if (horizontal) innerH else innerW

            // share leftover primary space among "Fill" children
            if (!primHug) {
                var fixedSum = 0f
                var flexSum = 0f
                for (i in 0 until n) {
                    val s = sizingOf(items[i], horizontal)
                    if (s is AxisSizing.FillContainer) flexSum += s.flex else fixedSum += ps[i]
                }
                if (flexSum > 0f) {
                    val gaps = if (al.primaryAlign == PrimaryAlign.SPACE_BETWEEN) 0f else al.gap * (n - 1)
                    val room = max(0f, availP - fixedSum - gaps)
                    for (i in 0 until n) {
                        val s = sizingOf(items[i], horizontal)
                        if (s is AxisSizing.FillContainer) {
                            ps[i] = clampAxis(room * s.flex / flexSum, minFor(items[i], horizontal), maxFor(items[i], horizontal))
                        }
                    }
                }
            }

            // counter axis: stretch / fill children take the full inner size
            var innerC = availC
            if (counterHug) {
                var m = 0f
                var any = false
                for (i in 0 until n) {
                    val s = sizingOf(items[i], !horizontal)
                    if (s !is AxisSizing.FillContainer && al.counterAlign != CounterAlign.STRETCH) {
                        m = max(m, cs[i])
                        any = true
                    }
                }
                if (!any) for (i in 0 until n) m = max(m, cs[i])
                innerC = m
            }
            for (i in 0 until n) {
                val s = sizingOf(items[i], !horizontal)
                if (s is AxisSizing.FillContainer || al.counterAlign == CounterAlign.STRETCH) {
                    cs[i] = clampAxis(innerC, minFor(items[i], !horizontal), maxFor(items[i], !horizontal))
                }
            }

            var sumPs = 0f
            for (v in ps) sumPs += v
            val usedWithGap = sumPs + al.gap * (n - 1)
            val availFinal = if (primHug) usedWithGap else availP
            var gapEff = al.gap
            var pos = padPS
            when (al.primaryAlign) {
                PrimaryAlign.START -> Unit
                PrimaryAlign.CENTER -> pos += (availFinal - usedWithGap) / 2f
                PrimaryAlign.END -> pos += availFinal - usedWithGap
                PrimaryAlign.SPACE_BETWEEN -> {
                    if (n > 1 && !primHug) gapEff = max(0f, (availFinal - sumPs) / (n - 1))
                }
            }

            for (i in 0 until n) {
                val item = items[i]
                val off = when (al.counterAlign) {
                    CounterAlign.START, CounterAlign.BASELINE, CounterAlign.STRETCH -> 0f
                    CounterAlign.CENTER -> (innerC - cs[i]) / 2f
                    CounterAlign.END -> innerC - cs[i]
                }
                val cp = padCS + off
                if (horizontal) {
                    item.x = pos; item.y = cp; item.nw = ps[i]; item.nh = cs[i]
                } else {
                    item.x = cp; item.y = pos; item.nw = cs[i]; item.nh = ps[i]
                }
                pos += ps[i] + gapEff
            }

            if (primHug) {
                val v = usedWithGap + padPS + padPE
                if (horizontal) frameW = v else frameH = v
            }
            if (counterHug) {
                val v = innerC + padCS + padCE
                if (horizontal) frameH = v else frameW = v
            }
        }

        // ── write children ──
        for (item in items) {
            val ch = item.node
            val isGroup = ch is GroupNode
            val sizeChanged = !isGroup && (abs(item.nw - ch.width) > EPS || abs(item.nh - ch.height) > EPS)
            val t = ch.transform
            val tx = item.x - item.ox
            val ty = item.y - item.oy
            val moved = abs(t.tx - tx) > EPS || abs(t.ty - ty) > EPS
            if (!sizeChanged && !moved) continue
            val w2 = if (isGroup) ch.width else item.nw
            val h2 = if (isGroup) ch.height else item.nh
            val base = ch.withGeometry(t.copy(tx = tx, ty = ty), w2, h2)
            val result: SceneNode = if (base is TextNode && sizeChanged) {
                val t2 = if (base.autoSize == TextAutoSize.AUTO_WIDTH) base.copy(autoSize = TextAutoSize.AUTO_HEIGHT) else base
                t2.estimated()
            } else base
            ctx.put(result)
            if (sizeChanged) ctx.canvas = resizeChildren(ctx.canvas, ch.id, ch.width, ch.height)
        }

        // ── hug: the container itself ──
        val flc = f.layoutChild
        if (hugW) frameW = clampAxis(frameW, flc.minW, flc.maxW)
        if (hugH) frameH = clampAxis(frameH, flc.minH, flc.maxH)
        if (abs(frameW - f.width) > EPS || abs(frameH - f.height) > EPS) {
            ctx.put(f.withGeometry(f.transform, frameW, frameH))
        }
    }

    // ───────── legacy constraints (frames without auto layout) ─────────

    /**
     * Call after [frameId] changed size from (oldW, oldH): children follow their constraints
     * (left / right / left+right / center / scale), recursively for nested frames.
     */
    fun resizeChildren(canvas: Canvas, frameId: NodeId, oldW: Float, oldH: Float): Canvas {
        val f = canvas[frameId] ?: return canvas
        if (f !is ContainerNode || f is GroupNode) return canvas
        val dw = f.width - oldW
        val dh = f.height - oldH
        if (abs(dw) < EPS && abs(dh) < EPS) return canvas
        val al = f.autoLayoutOrNull()
        if (al != null && al.mode != LayoutMode.NONE) return canvas      // auto-layout frames place their own children

        var c = canvas
        for (id in f.children) {
            val ch = c[id] ?: continue
            val k = ch.layoutChild.constraints
            val t = ch.transform
            var x = t.tx
            var y = t.ty
            var w = ch.width
            var h = ch.height
            when (k.horizontal) {
                AxisConstraint.MIN -> Unit
                AxisConstraint.MAX -> x += dw
                AxisConstraint.STRETCH -> w += dw
                AxisConstraint.CENTER -> x += dw / 2f
                AxisConstraint.SCALE -> {
                    val s = if (oldW > 0.0001f) f.width / oldW else 1f
                    x *= s
                    w *= s
                }
            }
            when (k.vertical) {
                AxisConstraint.MIN -> Unit
                AxisConstraint.MAX -> y += dh
                AxisConstraint.STRETCH -> h += dh
                AxisConstraint.CENTER -> y += dh / 2f
                AxisConstraint.SCALE -> {
                    val s = if (oldH > 0.0001f) f.height / oldH else 1f
                    y *= s
                    h *= s
                }
            }
            val isGroup = ch is GroupNode
            val nw = if (isGroup) ch.width else max(clampAxis(w, ch.layoutChild.minW, ch.layoutChild.maxW), 1f)
            val nh = if (isGroup) ch.height else max(clampAxis(h, ch.layoutChild.minH, ch.layoutChild.maxH), 1f)
            val changed = abs(x - t.tx) > EPS || abs(y - t.ty) > EPS ||
                abs(nw - ch.width) > EPS || abs(nh - ch.height) > EPS
            if (!changed) continue
            c = c.withNode(ch.withGeometry(t.copy(tx = x, ty = y), nw, nh).fixedIfText())
            c = resizeChildren(c, id, ch.width, ch.height)
        }
        return c
    }
}
