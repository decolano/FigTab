package app.figtab.domain.scene

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlin.math.cos
import kotlin.math.sin

/** Hard-coded document that exercises the Phase 2 painters (left frame) and text (right frame). */
object DemoScene {
    private fun solid(argb: Long) = Paint.Solid(argb.toInt())
    private fun stroke(argb: Long, w: Float, align: StrokeAlign = StrokeAlign.CENTER,
                       cap: StrokeCap = StrokeCap.NONE, dash: List<Float> = emptyList()) =
        Stroke(paints = listOf(solid(argb)), weight = w, align = align, cap = cap, dash = dash)

    private fun ts(
        size: Float, weight: Int = 400, color: Long = 0xFF111827, italic: Boolean = false,
        ls: Float = 0f, lh: Float? = null, underline: Boolean = false, strike: Boolean = false,
    ) = TextStyle(
        fontFamily = "sans-serif", fontStyle = if (italic) "Italic" else "Regular", fontWeight = weight,
        fontSize = size, lineHeightPx = lh, letterSpacingPx = ls, fills = listOf(solid(color)),
        underline = underline, strikethrough = strike,
    )

    /** Builds one string plus a run per styled part. */
    private fun rich(vararg parts: Pair<String, TextStyle>): Pair<String, List<TextRun>> {
        val sb = StringBuilder()
        val runs = ArrayList<TextRun>()
        for ((t, st) in parts) {
            val start = sb.length
            sb.append(t)
            runs.add(TextRun(start, sb.length, st))
        }
        return sb.toString() to runs
    }

    private fun textNode(
        id: NodeId, parent: NodeId, x: Float, y: Float, w: Float, h: Float,
        content: Pair<String, List<TextRun>>, autoSize: TextAutoSize,
        alignH: TextAlignH = TextAlignH.LEFT, alignV: TextAlignV = TextAlignV.TOP,
    ) = TextNode(
        id = id, parent = parent, transform = Affine2D(tx = x, ty = y), width = w, height = h,
        characters = content.first, runs = content.second,
        autoSize = autoSize, alignH = alignH, alignV = alignV,
    )

    fun create(): Canvas {
        // ───── Left frame: shapes ─────
        val frameId = NodeId(1)
        val gradRect = NodeId(2); val sun = NodeId(3); val star = NodeId(4); val hex = NodeId(5)
        val line = NodeId(6); val inset = NodeId(7); val donut = NodeId(8)
        val multA = NodeId(9); val multB = NodeId(10); val blurred = NodeId(11); val clipped = NodeId(12)

        val frame = FrameNode(
            id = frameId, parent = null, name = "Frame 1",
            transform = Affine2D(tx = 80f, ty = 80f), width = 760f, height = 520f,
            cornerRadii = CornerRadii.uniform(24f),
            fills = listOf(solid(0xFFFFFFFF)),
            stroke = stroke(0xFFD1D5DB, 2f, StrokeAlign.INSIDE),
            effects = listOf(Effect.DropShadow(0x40000000, Vec2(0f, 10f), blur = 28f, spread = 0f)),
            children = persistentListOf(gradRect, sun, star, hex, line, inset, donut, multA, multB, blurred, clipped),
        )
        val grad = ShapeNode(
            id = gradRect, parent = frameId, name = "Gradient", kind = ShapeKind.RECTANGLE,
            transform = Affine2D(tx = 32f, ty = 32f), width = 200f, height = 120f,
            cornerRadii = CornerRadii.uniform(16f),
            fills = listOf(Paint.Linear(
                stops = listOf(GradientStop(0f, 0xFF6366F1.toInt()), GradientStop(1f, 0xFFEC4899.toInt())),
                transform = Affine2D(a = 200f, d = 120f))),
        )
        val sunNode = ShapeNode(
            id = sun, parent = frameId, name = "Sun", kind = ShapeKind.ELLIPSE,
            transform = Affine2D(tx = 270f, ty = 32f), width = 120f, height = 120f,
            fills = listOf(Paint.Radial(
                stops = listOf(GradientStop(0f, 0xFFFDE68A.toInt()), GradientStop(1f, 0xFFF59E0B.toInt())),
                transform = Affine2D(a = 120f, d = 120f))),
            stroke = stroke(0xFF92400E, 4f),
        )
        val starNode = ShapeNode(
            id = star, parent = frameId, name = "Star", kind = ShapeKind.STAR, pointCount = 5,
            transform = Affine2D(tx = 430f, ty = 32f), width = 120f, height = 120f,
            fills = listOf(solid(0xFFEF4444)),
            stroke = stroke(0xFF7F1D1D, 4f, StrokeAlign.INSIDE),
        )
        val hexNode = ShapeNode(
            id = hex, parent = frameId, name = "Hexagon", kind = ShapeKind.POLYGON, pointCount = 6,
            transform = Affine2D(tx = 590f, ty = 32f), width = 140f, height = 120f,
            fills = listOf(solid(0xFF10B981)),
            stroke = stroke(0xFF065F46, 6f, StrokeAlign.OUTSIDE, dash = listOf(12f, 8f)),
        )
        val rad = Math.toRadians(8.0)
        val lineNode = ShapeNode(
            id = line, parent = frameId, name = "Line", kind = ShapeKind.LINE,
            transform = Affine2D(
                a = cos(rad).toFloat(), b = sin(rad).toFloat(),
                c = (-sin(rad)).toFloat(), d = cos(rad).toFloat(), tx = 32f, ty = 200f),
            width = 320f, height = 0f,
            stroke = stroke(0xFF111827, 6f, cap = StrokeCap.ROUND),
        )
        val insetNode = ShapeNode(
            id = inset, parent = frameId, name = "Inner shadow", kind = ShapeKind.RECTANGLE,
            transform = Affine2D(tx = 32f, ty = 300f), width = 200f, height = 120f,
            cornerRadii = CornerRadii.uniform(14f),
            fills = listOf(solid(0xFFE5E7EB)),
            effects = listOf(Effect.InnerShadow(0x66000000, Vec2(0f, 5f), blur = 14f, spread = 0f)),
        )
        val donutNode = ShapeNode(
            id = donut, parent = frameId, name = "Arc", kind = ShapeKind.ELLIPSE,
            transform = Affine2D(tx = 270f, ty = 290f), width = 140f, height = 140f,
            fills = listOf(solid(0xFF3B82F6)),
            arc = ArcData(startDeg = -90f, sweepDeg = 270f, innerRatio = 0.6f),
        )
        val multiplyBase = ShapeNode(
            id = multA, parent = frameId, name = "Base", kind = ShapeKind.RECTANGLE,
            transform = Affine2D(tx = 450f, ty = 290f), width = 150f, height = 110f,
            cornerRadii = CornerRadii(tl = 4f, tr = 28f, br = 4f, bl = 28f),
            fills = listOf(solid(0xFFFDE047)),
        )
        val multiplyTop = ShapeNode(
            id = multB, parent = frameId, name = "Multiply", kind = ShapeKind.ELLIPSE,
            transform = Affine2D(tx = 520f, ty = 330f), width = 130f, height = 130f,
            blendMode = BlendMode.MULTIPLY, opacity = 0.9f,
            fills = listOf(solid(0xFFEC4899)),
        )
        val blurNode = ShapeNode(
            id = blurred, parent = frameId, name = "Layer blur", kind = ShapeKind.RECTANGLE,
            transform = Affine2D(tx = 32f, ty = 450f), width = 160f, height = 50f,
            cornerRadii = CornerRadii.uniform(25f),
            fills = listOf(solid(0xFF8B5CF6)),
            effects = listOf(Effect.LayerBlur(radius = 8f)),
        )
        val clippedFrame = FrameNode(
            id = clipped, parent = frameId, name = "Clipping frame",
            transform = Affine2D(tx = 230f, ty = 450f), width = 150f, height = 50f,
            cornerRadii = CornerRadii.uniform(12f),
            fills = listOf(solid(0xFFFEF3C7)),
            stroke = stroke(0xFFF59E0B, 2f),
        )

        // ───── Right frame: text ─────
        val textFrameId = NodeId(13)
        val tBg = NodeId(14)
        val tTitle = NodeId(15); val tPara = NodeId(16); val tCenter = NodeId(17); val tSpaced = NodeId(18)
        val tDeco = NodeId(19); val tRight = NodeId(20); val tMixed = NodeId(21)

        val textFrame = FrameNode(
            id = textFrameId, parent = null, name = "Text frame",
            transform = Affine2D(tx = 880f, ty = 80f), width = 520f, height = 560f,
            cornerRadii = CornerRadii.uniform(24f),
            fills = listOf(solid(0xFFFFFFFF)),
            stroke = stroke(0xFFD1D5DB, 2f, StrokeAlign.INSIDE),
            effects = listOf(Effect.DropShadow(0x40000000, Vec2(0f, 10f), blur = 28f, spread = 0f)),
            children = persistentListOf(tBg, tTitle, tPara, tCenter, tSpaced, tDeco, tRight, tMixed),
        )
        val centerBg = ShapeNode(
            id = tBg, parent = textFrameId, name = "Center box", kind = ShapeKind.RECTANGLE,
            transform = Affine2D(tx = 32f, ty = 214f), width = 456f, height = 72f,
            cornerRadii = CornerRadii.uniform(12f), fills = listOf(solid(0xFFEEF2FF)),
        )
        val title = textNode(tTitle, textFrameId, 32f, 28f, 400f, 44f,
            rich("Text rendering" to ts(36f, 700)), TextAutoSize.AUTO_WIDTH)
        val para = textNode(tPara, textFrameId, 32f, 88f, 456f, 110f,
            rich(
                "FigTab draws " to ts(16f, 400, 0xFF374151, lh = 24f),
                "bold" to ts(16f, 700, 0xFF374151, lh = 24f),
                ", " to ts(16f, 400, 0xFF374151, lh = 24f),
                "italic" to ts(16f, 400, 0xFF374151, italic = true, lh = 24f),
                " and " to ts(16f, 400, 0xFF374151, lh = 24f),
                "colored" to ts(16f, 600, 0xFFDB2777, lh = 24f),
                " text in one wrapped paragraph with a custom line height." to ts(16f, 400, 0xFF374151, lh = 24f),
            ), TextAutoSize.AUTO_HEIGHT)
        val center = textNode(tCenter, textFrameId, 32f, 214f, 456f, 72f,
            rich("Centered in a fixed box" to ts(22f, 600, 0xFF4338CA)),
            TextAutoSize.FIXED, TextAlignH.CENTER, TextAlignV.CENTER)
        val spaced = textNode(tSpaced, textFrameId, 32f, 312f, 400f, 20f,
            rich("LETTER SPACING" to ts(13f, 700, 0xFF6B7280, ls = 4f)), TextAutoSize.AUTO_WIDTH)
        val deco = textNode(tDeco, textFrameId, 32f, 350f, 456f, 26f,
            rich(
                "Underlined" to ts(18f, underline = true),
                "   " to ts(18f),
                "Strikethrough" to ts(18f, 400, 0xFF9CA3AF, strike = true),
            ), TextAutoSize.AUTO_WIDTH)
        val right = textNode(tRight, textFrameId, 32f, 404f, 456f, 24f,
            rich("Right aligned" to ts(16f, 500, 0xFF059669)),
            TextAutoSize.FIXED, TextAlignH.RIGHT)
        val mixed = textNode(tMixed, textFrameId, 32f, 450f, 456f, 60f,
            rich(
                "Big" to ts(44f, 800, 0xFFEF4444),
                " and small" to ts(14f, 400, 0xFF6B7280),
            ), TextAutoSize.AUTO_WIDTH)

        // ───── Auto layout demos ─────
        val alA = NodeId(22); val a1 = NodeId(23); val a2 = NodeId(24); val a3 = NodeId(25)
        val alB = NodeId(26); val b1 = NodeId(27); val b2 = NodeId(28); val b3 = NodeId(29)

        val autoA = FrameNode(
            id = alA, parent = null, name = "Auto layout · hug",
            transform = Affine2D(tx = 80f, ty = 680f), width = 100f, height = 100f,
            cornerRadii = CornerRadii.uniform(16f), fills = listOf(solid(0xFFFFFFFF)),
            autoLayout = AutoLayout(
                mode = LayoutMode.HORIZONTAL, gap = 12f, padding = Padding(16f, 16f, 16f, 16f),
                counterAlign = CounterAlign.CENTER,
            ),
            layoutChild = LayoutChild(widthSizing = AxisSizing.HugContent, heightSizing = AxisSizing.HugContent),
            children = persistentListOf(a1, a2, a3),
        )
        fun box(id: NodeId, parent: NodeId, w: Float, h: Float, color: Long, lc: LayoutChild = LayoutChild()) = ShapeNode(
            id = id, parent = parent, name = "Box", kind = ShapeKind.RECTANGLE, width = w, height = h,
            cornerRadii = CornerRadii.uniform(8f), fills = listOf(solid(color)), layoutChild = lc,
        )
        val boxA1 = box(a1, alA, 60f, 60f, 0xFF6366F1)
        val boxA2 = box(a2, alA, 80f, 40f, 0xFFEC4899)
        val boxA3 = box(a3, alA, 50f, 90f, 0xFFF59E0B)

        val autoB = FrameNode(
            id = alB, parent = null, name = "Auto layout · fill",
            transform = Affine2D(tx = 500f, ty = 680f), width = 240f, height = 100f,
            cornerRadii = CornerRadii.uniform(16f), fills = listOf(solid(0xFFFFFFFF)),
            autoLayout = AutoLayout(mode = LayoutMode.VERTICAL, gap = 8f, padding = Padding(12f, 12f, 12f, 12f)),
            layoutChild = LayoutChild(widthSizing = AxisSizing.Fixed(240f), heightSizing = AxisSizing.HugContent),
            children = persistentListOf(b1, b2, b3),
        )
        val fillW = AxisSizing.FillContainer(1f)
        val boxB1 = box(b1, alB, 10f, 40f, 0xFF10B981, LayoutChild(widthSizing = fillW, heightSizing = AxisSizing.Fixed(40f)))
        val boxB2 = box(b2, alB, 10f, 24f, 0xFF3B82F6, LayoutChild(widthSizing = fillW, heightSizing = AxisSizing.Fixed(24f)))
        val boxB3 = box(b3, alB, 10f, 56f, 0xFF8B5CF6, LayoutChild(widthSizing = fillW, heightSizing = AxisSizing.Fixed(56f)))

        val all: List<SceneNode> = listOf(
            frame, grad, sunNode, starNode, hexNode, lineNode, insetNode, donutNode,
            multiplyBase, multiplyTop, blurNode, clippedFrame,
            textFrame, centerBg, title, para, center, spaced, deco, right, mixed,
            autoA, boxA1, boxA2, boxA3, autoB, boxB1, boxB2, boxB3,
        )
        return Canvas(
            nodes = all.associateBy { it.id }.toPersistentMap(),
            roots = persistentListOf(frameId, textFrameId, alA, alB),
        )
    }
}
