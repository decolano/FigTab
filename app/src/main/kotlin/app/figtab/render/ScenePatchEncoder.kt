package app.figtab.render

import app.figtab.domain.scene.*

/**
 * Encodes domain scene state into the binary patch format defined in native scene_patch.h (version 3).
 * Keep this file and scene_patch.h in lock-step.
 */
object ScenePatchEncoder {
    private const val MAGIC = 0x46545031
    private const val VERSION = 3
    private const val OP_UPSERT = 1
    private const val OP_SET_CHILDREN = 3
    private const val OP_SET_ROOTS = 4
    private const val OP_CLEAR_ALL = 5

    private const val FLAG_VISIBLE = 1
    private const val FLAG_CLIPS = 2
    private const val FLAG_EFFECTS = 4

    /** Full snapshot: clear, then upsert every node, then child lists and roots. */
    fun encodeFull(canvas: Canvas, out: PatchBuffer) {
        out.reset()
        var ops = 2 // ClearAll + SetRoots
        for (n in canvas.nodes.values) {
            ops++
            if (n is ContainerNode) ops++
        }
        out.putInt(MAGIC).putInt(VERSION).putInt(ops).putInt(0)

        op(out, OP_CLEAR_ALL) { }

        for (n in canvas.nodes.values) op(out, OP_UPSERT) { writeNode(out, n) }

        for (n in canvas.nodes.values) {
            if (n !is ContainerNode) continue
            op(out, OP_SET_CHILDREN) {
                out.putLong(n.id.raw).putInt(n.children.size)
                for (k in n.children) out.putLong(k.raw)
            }
        }

        op(out, OP_SET_ROOTS) {
            out.putInt(canvas.roots.size)
            for (r in canvas.roots) out.putLong(r.raw)
        }
    }

    /** Incremental update: re-upserts only [ids] (no ClearAll). Used while dragging. */
    fun encodeNodes(canvas: Canvas, ids: Collection<NodeId>, out: PatchBuffer) {
        val nodes = ids.mapNotNull { canvas[it] }
        out.reset()
        out.putInt(MAGIC).putInt(VERSION).putInt(nodes.size).putInt(0)
        for (n in nodes) op(out, OP_UPSERT) { writeNode(out, n) }
    }

    /** Writes an op header, runs [body], then back-fills the payload size. */
    private inline fun op(out: PatchBuffer, code: Int, body: () -> Unit) {
        out.putInt(code)
        val sizePos = out.length
        out.putInt(0)
        body()
        out.setInt(sizePos, out.length - sizePos - 4)
    }

    private fun writeNode(o: PatchBuffer, n: SceneNode) {
        var flags = 0
        if (n.visible) flags = flags or FLAG_VISIBLE
        val clips = when (n) {
            is FrameNode -> n.clipsContent
            is ComponentNode -> n.clipsContent
            else -> false
        }
        if (clips) flags = flags or FLAG_CLIPS
        if (n.effects.isNotEmpty()) flags = flags or FLAG_EFFECTS
        val t = n.transform

        // NodeRecord: 56 bytes
        o.putLong(n.id.raw).putInt(kindOf(n)).putInt(flags)
        o.putFloat(t.a).putFloat(t.b).putFloat(t.c).putFloat(t.d).putFloat(t.tx).putFloat(t.ty)
        o.putFloat(n.width).putFloat(n.height).putFloat(n.opacity).putInt(n.blendMode.ordinal)

        writePayload(o, n)
    }

    private fun writePayload(o: PatchBuffer, n: SceneNode) {
        var shape = 0
        var radii = CornerRadii.Zero
        var pointCount = 5
        var inner = 0.382f
        var arc: ArcData? = null
        var fills: List<Paint> = emptyList()
        var stroke: Stroke? = null

        when (n) {
            is ShapeNode -> {
                shape = when (n.kind) {
                    ShapeKind.RECTANGLE -> 1
                    ShapeKind.ELLIPSE -> 2
                    ShapeKind.STAR -> 3
                    ShapeKind.POLYGON -> 4
                    ShapeKind.LINE -> 5
                }
                radii = n.cornerRadii; pointCount = n.pointCount; inner = n.innerRadiusRatio; arc = n.arc
                fills = n.fills; stroke = n.stroke
            }
            is FrameNode -> { shape = 1; radii = n.cornerRadii; fills = n.fills; stroke = n.stroke }
            is ComponentNode -> { shape = 1; radii = n.cornerRadii; fills = n.fills; stroke = n.stroke }
            is SectionNode -> { shape = 1; fills = n.fills; stroke = n.stroke }
            is VectorNetworkNode, is GroupNode, is TextNode, is InstanceNode -> Unit  // Phase 2b / 5
        }

        o.putInt(shape)
        o.putFloat(radii.tl).putFloat(radii.tr).putFloat(radii.br).putFloat(radii.bl)
        o.putInt(pointCount).putFloat(inner)
        o.putFloat(arc?.startDeg ?: 0f).putFloat(arc?.sweepDeg ?: 360f).putFloat(arc?.innerRatio ?: 0f)

        val visibleFills = fills.filter { it.visible }
        o.putInt(visibleFills.size)
        for (p in visibleFills) writePaint(o, p)

        val strokePaints = stroke?.paints?.filter { it.visible }.orEmpty()
        if (stroke != null && strokePaints.isNotEmpty()) {
            o.putInt(1)
            o.putFloat(stroke.weight)
            o.putInt(stroke.align.ordinal)   // INSIDE 0, CENTER 1, OUTSIDE 2
            o.putInt(stroke.cap.ordinal)     // NONE 0, ROUND 1, SQUARE 2, ARROW 3
            o.putInt(stroke.join.ordinal)    // MITER 0, BEVEL 1, ROUND 2
            o.putFloat(stroke.miterLimit)
            o.putInt(stroke.dash.size)
            for (d in stroke.dash) o.putFloat(d)
            o.putInt(strokePaints.size)
            for (p in strokePaints) writePaint(o, p)
        } else {
            o.putInt(0)
        }

        val effects = n.effects.filter { it.visible }
        o.putInt(effects.size)
        for (e in effects) {
            when (e) {
                is Effect.DropShadow -> writeEffect(o, 0, e.color, e.offset.x, e.offset.y, e.blur, e.spread)
                is Effect.InnerShadow -> writeEffect(o, 1, e.color, e.offset.x, e.offset.y, e.blur, e.spread)
                is Effect.LayerBlur -> writeEffect(o, 2, 0, 0f, 0f, e.radius, 0f)
                is Effect.BackgroundBlur -> writeEffect(o, 3, 0, 0f, 0f, e.radius, 0f)
            }
        }

        writeText(o, n)
    }

    private fun writeText(o: PatchBuffer, n: SceneNode) {
        if (n !is TextNode) { o.putInt(0); return }
        o.putInt(1)
        o.putInt(n.autoSize.ordinal)    // AUTO_WIDTH 0, AUTO_HEIGHT 1, FIXED 2
        o.putInt(n.alignH.ordinal)      // LEFT 0, CENTER 1, RIGHT 2, JUSTIFIED 3 (drawn as left for now)
        o.putInt(n.alignV.ordinal)      // TOP 0, CENTER 1, BOTTOM 2
        o.putFloat(n.paragraphSpacing)
        o.putInt(n.maxLines ?: 0)

        val len = n.characters.length
        val runs = if (n.runs.isEmpty()) listOf(TextRun(0, len, TextStyle(fontFamily = "sans-serif")))
                   else n.runs.sortedBy { it.start }
        val segments = runs.mapNotNull { r ->
            val s = r.start.coerceIn(0, len)
            val e = r.end.coerceIn(s, len)
            if (e > s) r to n.characters.substring(s, e) else null
        }
        o.putInt(segments.size)
        for ((r, text) in segments) {
            val st = r.style
            o.putFloat(st.fontSize).putFloat(st.lineHeightPx ?: 0f).putFloat(st.letterSpacingPx)
            o.putInt(st.fontWeight)
            o.putInt(if (st.fontStyle.contains("italic", ignoreCase = true)) 1 else 0)
            o.putInt((if (st.underline) 1 else 0) or (if (st.strikethrough) 2 else 0))
            val fam = st.fontFamily.toByteArray(Charsets.UTF_8)
            o.putInt(fam.size).putBytes(fam)
            val txt = text.toByteArray(Charsets.UTF_8)
            o.putInt(txt.size).putBytes(txt)
            val fills = st.fills.filter { it.visible }
            o.putInt(fills.size)
            for (p in fills) writePaint(o, p)
        }
    }

    private fun writeEffect(o: PatchBuffer, type: Int, color: Int, dx: Float, dy: Float, blur: Float, spread: Float) {
        o.putInt(type).putInt(color).putFloat(dx).putFloat(dy).putFloat(blur).putFloat(spread)
    }

    private fun writePaint(o: PatchBuffer, p: Paint) {
        when (p) {
            is Paint.Solid -> {
                o.putInt(0).putFloat(p.opacity).putInt(p.color).putInt(0)
            }
            is Paint.Linear -> writeGradient(o, 1, p.opacity, p.stops, p.transform)
            is Paint.Radial -> writeGradient(o, 2, p.opacity, p.stops, p.transform)
            is Paint.Image -> {
                // Image fills arrive in a later phase: draw a neutral placeholder.
                o.putInt(0).putFloat(p.opacity).putInt(0xFF9CA3AF.toInt()).putInt(0)
            }
        }
    }

    private fun writeGradient(o: PatchBuffer, type: Int, opacity: Float, stops: List<GradientStop>, t: Affine2D) {
        o.putInt(type).putFloat(opacity).putInt(stops.firstOrNull()?.color ?: 0).putInt(stops.size)
        for (s in stops) o.putFloat(s.position).putInt(s.color)
        o.putFloat(t.a).putFloat(t.b).putFloat(t.c).putFloat(t.d).putFloat(t.tx).putFloat(t.ty)
    }

    private fun kindOf(n: SceneNode): Int = when (n) {
        is SectionNode -> 1
        is FrameNode -> 2
        is GroupNode -> 3
        is ShapeNode -> 4
        is VectorNetworkNode -> 5
        is TextNode -> 6
        is ComponentNode -> 7
        is InstanceNode -> 8
    }
}
