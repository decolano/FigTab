package app.figtab.domain.scene

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

// ───────────────────────── Identity & Geometry ─────────────────────────

@JvmInline value class NodeId(val raw: Long)
@JvmInline value class PageId(val raw: Long)

data class Vec2(val x: Float, val y: Float)
data class Rect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    fun union(o: Rect): Rect {
        val l = minOf(x, o.x); val t = minOf(y, o.y)
        return Rect(l, t, maxOf(right, o.right) - l, maxOf(bottom, o.bottom) - t)
    }
}

/**
 * 2D affine, Figma/SVG convention (column vector):
 *   x' = a*x + c*y + tx
 *   y' = b*x + d*y + ty
 * These 6 floats are exactly what crosses JNI and expands to a 3x3 SkMatrix natively.
 */
data class Affine2D(
    val a: Float = 1f, val b: Float = 0f,
    val c: Float = 0f, val d: Float = 1f,
    val tx: Float = 0f, val ty: Float = 0f,
) {
    /** this ∘ o : apply [o] first, then this. */
    operator fun times(o: Affine2D) = Affine2D(
        a * o.a + c * o.b, b * o.a + d * o.b,
        a * o.c + c * o.d, b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx, b * o.tx + d * o.ty + ty,
    )
    fun map(p: Vec2) = Vec2(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)
    fun invert(): Affine2D? {
        val det = a * d - b * c
        if (det == 0f) return null
        val ia = d / det; val ib = -b / det; val ic = -c / det; val id = a / det
        return Affine2D(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }
    companion object { val Identity = Affine2D() }
}

// ───────────────────────── Style primitives ─────────────────────────

typealias ArgbColor = Int

data class GradientStop(val position: Float, val color: ArgbColor)
enum class ScaleMode { FILL, FIT, CROP, TILE }

sealed interface Paint {
    val visible: Boolean
    val opacity: Float
    data class Solid(val color: ArgbColor, override val visible: Boolean = true, override val opacity: Float = 1f) : Paint
    data class Linear(val stops: List<GradientStop>, val transform: Affine2D, override val visible: Boolean = true, override val opacity: Float = 1f) : Paint
    data class Radial(val stops: List<GradientStop>, val transform: Affine2D, override val visible: Boolean = true, override val opacity: Float = 1f) : Paint
    data class Image(val assetHash: String, val scaleMode: ScaleMode, override val visible: Boolean = true, override val opacity: Float = 1f) : Paint
}

enum class StrokeAlign { INSIDE, CENTER, OUTSIDE }
enum class StrokeCap { NONE, ROUND, SQUARE, ARROW }
enum class StrokeJoin { MITER, BEVEL, ROUND }

data class Stroke(
    val paints: List<Paint>,
    val weight: Float = 1f,
    val align: StrokeAlign = StrokeAlign.INSIDE,
    val cap: StrokeCap = StrokeCap.NONE,
    val join: StrokeJoin = StrokeJoin.MITER,
    val miterLimit: Float = 4f,
    val dash: List<Float> = emptyList(),
)

enum class BlendMode {
    PASS_THROUGH, NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
    HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY
}

sealed interface Effect {
    val visible: Boolean
    data class DropShadow(val color: ArgbColor, val offset: Vec2, val blur: Float, val spread: Float, override val visible: Boolean = true) : Effect
    data class InnerShadow(val color: ArgbColor, val offset: Vec2, val blur: Float, val spread: Float, override val visible: Boolean = true) : Effect
    data class LayerBlur(val radius: Float, override val visible: Boolean = true) : Effect
    data class BackgroundBlur(val radius: Float, override val visible: Boolean = true) : Effect
}

/** Independent corner radii (TL, TR, BR, BL) plus Figma-style smoothing. */
data class CornerRadii(val tl: Float = 0f, val tr: Float = 0f, val br: Float = 0f, val bl: Float = 0f, val smoothing: Float = 0f) {
    companion object {
        val Zero = CornerRadii()
        fun uniform(r: Float) = CornerRadii(r, r, r, r)
    }
}

enum class ExportFormat { PNG, SVG, PDF, JPG }
data class ExportSetting(val format: ExportFormat, val scale: Float, val suffix: String = "")

// ───────────────────────── Layout descriptors (Module B) ─────────────────────────

enum class AxisConstraint { MIN, MAX, STRETCH, CENTER, SCALE }
data class Constraints(val horizontal: AxisConstraint = AxisConstraint.MIN, val vertical: AxisConstraint = AxisConstraint.MIN)

enum class LayoutMode { NONE, HORIZONTAL, VERTICAL, WRAP }
enum class PrimaryAlign { START, CENTER, END, SPACE_BETWEEN }
enum class CounterAlign { START, CENTER, END, BASELINE, STRETCH }

sealed interface AxisSizing {
    data class Fixed(val px: Float) : AxisSizing
    data object HugContent : AxisSizing
    /** Leftover space is shared among FILL siblings proportionally to [flex]. */
    data class FillContainer(val flex: Float = 1f) : AxisSizing
}

data class Padding(val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f, val left: Float = 0f)

data class AutoLayout(
    val mode: LayoutMode = LayoutMode.NONE,
    val gap: Float = 0f,
    val crossGap: Float = 0f,            // WRAP row/column gap
    val padding: Padding = Padding(),
    val primaryAlign: PrimaryAlign = PrimaryAlign.START,
    val counterAlign: CounterAlign = CounterAlign.START,
    val reverseZ: Boolean = false,
)

/** Per-child participation in the parent's layout (lives on the child, like Figma). */
data class LayoutChild(
    val widthSizing: AxisSizing = AxisSizing.Fixed(100f),
    val heightSizing: AxisSizing = AxisSizing.Fixed(100f),
    val absolute: Boolean = false,       // opts out of flow; positioned via [constraints]
    val constraints: Constraints = Constraints(),
    val minW: Float? = null, val maxW: Float? = null,
    val minH: Float? = null, val maxH: Float? = null,
)

// ───────────────────────── Scene nodes (Module A) ─────────────────────────

/** Shared by every node. [transform] is RELATIVE to the parent. */
sealed interface SceneNode {
    val id: NodeId
    val name: String
    val parent: NodeId?
    val transform: Affine2D
    val width: Float
    val height: Float
    val visible: Boolean
    val locked: Boolean
    val opacity: Float
    val blendMode: BlendMode
    val effects: List<Effect>
    val layoutChild: LayoutChild
    val exports: List<ExportSetting>
}

/** Nodes owning an ordered child list (back → front). */
sealed interface ContainerNode : SceneNode {
    val children: PersistentList<NodeId>
}

/** Nodes that can be filled/stroked. */
sealed interface PaintableNode : SceneNode {
    val fills: List<Paint>
    val stroke: Stroke?
}

data class SectionNode(
    override val id: NodeId, override val name: String = "Section", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val children: PersistentList<NodeId> = persistentListOf(),
    override val fills: List<Paint> = emptyList(), override val stroke: Stroke? = null,
) : ContainerNode, PaintableNode

data class FrameNode(
    override val id: NodeId, override val name: String = "Frame", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val children: PersistentList<NodeId> = persistentListOf(),
    override val fills: List<Paint> = emptyList(), override val stroke: Stroke? = null,
    val cornerRadii: CornerRadii = CornerRadii.Zero,
    val clipsContent: Boolean = true,
    val autoLayout: AutoLayout = AutoLayout(),   // mode == NONE → legacy constraints solver
) : ContainerNode, PaintableNode

/** Bounds are DERIVED from children by the layout pass; width/height are the cached result. */
data class GroupNode(
    override val id: NodeId, override val name: String = "Group", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float = 0f, override val height: Float = 0f,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val children: PersistentList<NodeId> = persistentListOf(),
) : ContainerNode

enum class ShapeKind { RECTANGLE, ELLIPSE, STAR, POLYGON, LINE }
data class ArcData(val startDeg: Float, val sweepDeg: Float, val innerRatio: Float)

data class ShapeNode(
    override val id: NodeId, override val name: String = "Shape", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val fills: List<Paint> = emptyList(), override val stroke: Stroke? = null,
    val kind: ShapeKind,
    val cornerRadii: CornerRadii = CornerRadii.Zero,   // RECTANGLE
    val pointCount: Int = 5,                            // STAR / POLYGON
    val innerRadiusRatio: Float = 0.382f,               // STAR
    val arc: ArcData? = null,                           // ELLIPSE arcs / donuts
) : PaintableNode

// ── Vector network: a vertex may have any number of incident segments (branching) ──

data class NetVertex(val position: Vec2, val strokeCap: StrokeCap? = null, val cornerRadius: Float = 0f)

/** Cubic tangents are offsets relative to their own endpoint; (0,0) ⇒ straight at that end. */
data class NetSegment(
    val start: Int, val end: Int,
    val tangentStart: Vec2 = Vec2(0f, 0f), val tangentEnd: Vec2 = Vec2(0f, 0f),
)
enum class WindingRule { NONZERO, EVENODD }

/** A filled region = closed loops, each an ordered list of segment indices. null fills ⇒ inherit node fills. */
data class NetRegion(val windingRule: WindingRule, val loops: List<List<Int>>, val fills: List<Paint>? = null)

data class VectorNetwork(
    val vertices: List<NetVertex> = emptyList(),
    val segments: List<NetSegment> = emptyList(),
    val regions: List<NetRegion> = emptyList(),
)

data class VectorNetworkNode(
    override val id: NodeId, override val name: String = "Vector", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val fills: List<Paint> = emptyList(), override val stroke: Stroke? = null,
    val network: VectorNetwork,
) : PaintableNode

// ── Text ──

enum class TextAutoSize { AUTO_WIDTH, AUTO_HEIGHT, FIXED }
enum class TextAlignH { LEFT, CENTER, RIGHT, JUSTIFIED }
enum class TextAlignV { TOP, CENTER, BOTTOM }
data class OpenTypeFeature(val tag: String, val enabled: Boolean)   // "liga", "ss01", "tnum" …

data class TextStyle(
    val fontFamily: String, val fontStyle: String = "Regular", val fontWeight: Int = 400,
    val fontSize: Float = 14f, val lineHeightPx: Float? = null, val letterSpacingPx: Float = 0f,
    val features: List<OpenTypeFeature> = emptyList(),
    val fills: List<Paint> = listOf(Paint.Solid(0xFF000000.toInt())),
    val underline: Boolean = false, val strikethrough: Boolean = false,
)
/** Half-open UTF-16 range [start, end) into [TextNode.characters]. */
data class TextRun(val start: Int, val end: Int, val style: TextStyle)

data class TextNode(
    override val id: NodeId, override val name: String = "Text", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    val characters: String,
    val runs: List<TextRun>,
    val autoSize: TextAutoSize = TextAutoSize.AUTO_WIDTH,
    val alignH: TextAlignH = TextAlignH.LEFT, val alignV: TextAlignV = TextAlignV.TOP,
    val paragraphSpacing: Float = 0f, val maxLines: Int? = null,
) : SceneNode

// ── Components & instances ──

/** Sparse override of one master descendant; null = inherit from master. */
data class NodePatch(
    val fills: List<Paint>? = null, val stroke: Stroke? = null, val characters: String? = null,
    val textRuns: List<TextRun>? = null, val visible: Boolean? = null, val opacity: Float? = null,
    val effects: List<Effect>? = null, val width: Float? = null, val height: Float? = null,
    val transform: Affine2D? = null, val swapComponent: NodeId? = null,
)

data class ComponentNode(
    override val id: NodeId, override val name: String = "Component", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    override val children: PersistentList<NodeId> = persistentListOf(),
    override val fills: List<Paint> = emptyList(), override val stroke: Stroke? = null,
    val cornerRadii: CornerRadii = CornerRadii.Zero, val clipsContent: Boolean = true,
    val autoLayout: AutoLayout = AutoLayout(),
    val description: String = "",
) : ContainerNode, PaintableNode

/**
 * Instances own no children. The resolver expands [componentId]'s subtree at read time and applies
 * [overrides], keyed by the MASTER descendant's NodeId — master edits propagate with zero copying.
 */
data class InstanceNode(
    override val id: NodeId, override val name: String = "Instance", override val parent: NodeId?,
    override val transform: Affine2D = Affine2D.Identity, override val width: Float, override val height: Float,
    override val visible: Boolean = true, override val locked: Boolean = false, override val opacity: Float = 1f,
    override val blendMode: BlendMode = BlendMode.PASS_THROUGH, override val effects: List<Effect> = emptyList(),
    override val layoutChild: LayoutChild = LayoutChild(), override val exports: List<ExportSetting> = emptyList(),
    val componentId: NodeId,
    val overrides: PersistentMap<NodeId, NodePatch> = persistentMapOf(),
    val rootOverride: NodePatch? = null,
) : SceneNode

// ───────────────────────── Document hierarchy ─────────────────────────

/**
 * Persistent (structurally shared) node table. Every mutation returns a new Canvas sharing unchanged
 * entries: O(log n) updates, cheap undo snapshots, safe hand-off across threads.
 */
data class Canvas(
    val nodes: PersistentMap<NodeId, SceneNode> = persistentMapOf(),
    val roots: PersistentList<NodeId> = persistentListOf(),     // top-level nodes on the infinite canvas
    val background: ArgbColor = 0xFFE5E5E5.toInt(),
) {
    operator fun get(id: NodeId): SceneNode? = nodes[id]

    /** World transform = product of relative transforms up the parent chain. */
    fun worldTransform(id: NodeId): Affine2D {
        var m = Affine2D.Identity
        var cur: SceneNode? = nodes[id]
        while (cur != null) {
            m = cur.transform * m
            cur = cur.parent?.let { nodes[it] }
        }
        return m
    }
}

data class Page(val id: PageId, val name: String, val canvas: Canvas = Canvas())

data class Document(
    val id: String,
    val name: String,
    val pages: PersistentList<Page> = persistentListOf(),
    val schemaVersion: Int = 1,
    val nextNodeId: Long = 1L,
)
