package app.figtab.domain.scene

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Small hard-coded document used to verify the pipeline end to end until real tools exist. */
object DemoScene {
    fun create(): Canvas {
        val frameId = NodeId(1); val a = NodeId(2); val b = NodeId(3); val c = NodeId(4)
        val white = listOf<Paint>(Paint.Solid(0xFFFFFFFF.toInt()))
        val frame = FrameNode(
            id = frameId, parent = null, name = "Frame 1",
            transform = Affine2D(tx = 120f, ty = 120f), width = 480f, height = 320f,
            children = persistentListOf(a, b, c), fills = white,
        )
        val rect = ShapeNode(
            id = a, parent = frameId, name = "Rectangle",
            transform = Affine2D(tx = 32f, ty = 32f), width = 160f, height = 100f,
            kind = ShapeKind.RECTANGLE, fills = white,
        )
        val ellipse = ShapeNode(
            id = b, parent = frameId, name = "Ellipse",
            transform = Affine2D(tx = 240f, ty = 64f), width = 120f, height = 120f,
            kind = ShapeKind.ELLIPSE, fills = white,
        )
        val bar = ShapeNode(
            id = c, parent = frameId, name = "Bar",
            transform = Affine2D(tx = 32f, ty = 240f), width = 416f, height = 48f,
            kind = ShapeKind.RECTANGLE, fills = white,
        )
        return Canvas(
            nodes = persistentMapOf(frameId to frame, a to rect, b to ellipse, c to bar),
            roots = persistentListOf(frameId),
        )
    }
}
