package app.figtab.domain.scene

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.cos
import kotlin.math.sin

/** Hard-coded document that exercises the Phase 2 painters. */
object DemoScene {
    private fun solid(argb: Long) = Paint.Solid(argb.toInt())
    private fun stroke(argb: Long, w: Float, align: StrokeAlign = StrokeAlign.CENTER,
                       cap: StrokeCap = StrokeCap.NONE, dash: List<Float> = emptyList()) =
        Stroke(paints = listOf(solid(argb)), weight = w, align = align, cap = cap, dash = dash)

    fun create(): Canvas {
        val ids = (1L..12L).map { NodeId(it) }
        val frameId = ids[0]; val gradRect = ids[1]; val sun = ids[2]; val star = ids[3]
        val hex = ids[4]; val line = ids[5]; val inset = ids[6]; val donut = ids[7]
        val multA = ids[8]; val multB = ids[9]; val blurred = ids[10]; val clipped = ids[11]

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

        return Canvas(
            nodes = persistentMapOf(
                frameId to frame, gradRect to grad, sun to sunNode, star to starNode, hex to hexNode,
                line to lineNode, inset to insetNode, donut to donutNode, multA to multiplyBase,
                multB to multiplyTop, blurred to blurNode, clipped to clippedFrame,
            ),
            roots = persistentListOf(frameId),
        )
    }
}
