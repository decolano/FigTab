package app.figtab.domain.scene

import kotlin.math.max

/** Active tool. MOVE selects/moves/resizes; the others draw a new node. */
enum class Tool { MOVE, FRAME, RECT, ELLIPSE, LINE, STAR, POLYGON, TEXT }

// ───────── structure edits ─────────

/** Appends [n] on top of [parent]'s children (or the page roots when [parent] is null). */
fun Canvas.addNode(n: SceneNode, parent: NodeId?): Canvas {
    val withNode = nodes.put(n.id, n)
    if (parent == null) return copy(nodes = withNode, roots = roots.add(n.id))
    val p = withNode[parent]
    return if (p is ContainerNode) {
        copy(nodes = withNode.put(parent, p.withChildren(p.children.add(n.id))))
    } else {
        copy(nodes = withNode, roots = roots.add(n.id))
    }
}

// ───────── property edits (all return a new node) ─────────

fun SceneNode.withCommon(
    name: String? = null, visible: Boolean? = null, locked: Boolean? = null, opacity: Float? = null,
    effects: List<Effect>? = null, blendMode: BlendMode? = null,
): SceneNode = when (this) {
    is SectionNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is FrameNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is GroupNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is ShapeNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is VectorNetworkNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is TextNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is ComponentNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
    is InstanceNode -> copy(name = name ?: this.name, visible = visible ?: this.visible, locked = locked ?: this.locked,
        opacity = opacity ?: this.opacity, effects = effects ?: this.effects, blendMode = blendMode ?: this.blendMode)
}

fun SceneNode.fillsOrNull(): List<Paint>? = when (this) {
    is SectionNode -> fills
    is FrameNode -> fills
    is ShapeNode -> fills
    is VectorNetworkNode -> fills
    is ComponentNode -> fills
    else -> null
}

fun SceneNode.strokeOrNull(): Stroke? = when (this) {
    is SectionNode -> stroke
    is FrameNode -> stroke
    is ShapeNode -> stroke
    is VectorNetworkNode -> stroke
    is ComponentNode -> stroke
    else -> null
}

fun SceneNode.withFills(fills: List<Paint>): SceneNode = when (this) {
    is SectionNode -> copy(fills = fills)
    is FrameNode -> copy(fills = fills)
    is ShapeNode -> copy(fills = fills)
    is VectorNetworkNode -> copy(fills = fills)
    is ComponentNode -> copy(fills = fills)
    else -> this
}

fun SceneNode.withStroke(stroke: Stroke?): SceneNode = when (this) {
    is SectionNode -> copy(stroke = stroke)
    is FrameNode -> copy(stroke = stroke)
    is ShapeNode -> copy(stroke = stroke)
    is VectorNetworkNode -> copy(stroke = stroke)
    is ComponentNode -> copy(stroke = stroke)
    else -> this
}

fun SceneNode.cornerRadiiOrNull(): CornerRadii? = when (this) {
    is FrameNode -> cornerRadii
    is ComponentNode -> cornerRadii
    is ShapeNode -> if (kind == ShapeKind.RECTANGLE) cornerRadii else null
    else -> null
}

fun SceneNode.withCornerRadii(r: CornerRadii): SceneNode = when (this) {
    is FrameNode -> copy(cornerRadii = r)
    is ComponentNode -> copy(cornerRadii = r)
    is ShapeNode -> if (kind == ShapeKind.RECTANGLE) copy(cornerRadii = r) else this
    else -> this
}

// ───────── text edits ─────────

/**
 * There is no text measurement on the Kotlin side yet, so auto-sized boxes get an estimated size
 * (used for selection boxes and hit testing). The renderer always lays the text out exactly.
 */
fun TextNode.estimated(): TextNode {
    val style = runs.firstOrNull()?.style
    val size = style?.fontSize ?: 14f
    val lineHeight = style?.lineHeightPx ?: (size * 1.2f)
    val charW = size * 0.55f + (style?.letterSpacingPx ?: 0f)
    val lines = characters.split("\n")
    return when (autoSize) {
        TextAutoSize.AUTO_WIDTH -> copy(
            width = max(lines.maxOf { it.length } * charW, 4f),
            height = lines.size * lineHeight,
        )
        TextAutoSize.AUTO_HEIGHT -> {
            val perLine = max(1, (width / charW).toInt())
            val count = lines.sumOf { max(1, (it.length + perLine - 1) / perLine) }
            copy(height = count * lineHeight)
        }
        TextAutoSize.FIXED -> this
    }
}

fun SceneNode.withText(text: String): SceneNode {
    val n = this
    if (n !is TextNode) return n
    val style = n.runs.firstOrNull()?.style ?: TextStyle(fontFamily = "sans-serif")
    val runs = if (text.isEmpty()) emptyList() else listOf(TextRun(0, text.length, style))
    return n.copy(characters = text, runs = runs).estimated()
}

fun SceneNode.withTextStyle(f: (TextStyle) -> TextStyle): SceneNode {
    val n = this
    if (n !is TextNode) return n
    return n.copy(runs = n.runs.map { it.copy(style = f(it.style)) }).estimated()
}

fun SceneNode.withTextAlign(a: TextAlignH): SceneNode {
    val n = this
    return if (n is TextNode) n.copy(alignH = a) else n
}

fun SceneNode.withTextAutoSize(a: TextAutoSize): SceneNode {
    val n = this
    return if (n is TextNode) n.copy(autoSize = a).estimated() else n
}

// ───────── new-node defaults ─────────

object NodeFactory {
    private fun solid(argb: Long) = Paint.Solid(argb.toInt())

    fun baseName(tool: Tool): String = when (tool) {
        Tool.FRAME -> "Frame"
        Tool.RECT -> "Rectangle"
        Tool.ELLIPSE -> "Ellipse"
        Tool.LINE -> "Line"
        Tool.STAR -> "Star"
        Tool.POLYGON -> "Polygon"
        Tool.TEXT -> "Text"
        Tool.MOVE -> "Layer"
    }

    fun create(tool: Tool, id: NodeId, parent: NodeId?, name: String, transform: Affine2D, w: Float, h: Float): SceneNode =
        when (tool) {
            Tool.FRAME -> FrameNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                fills = listOf(solid(0xFFFFFFFF)),
            )
            Tool.RECT -> ShapeNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                kind = ShapeKind.RECTANGLE, fills = listOf(solid(0xFFD9D9D9)),
            )
            Tool.ELLIPSE -> ShapeNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                kind = ShapeKind.ELLIPSE, fills = listOf(solid(0xFFD9D9D9)),
            )
            Tool.STAR -> ShapeNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                kind = ShapeKind.STAR, pointCount = 5, fills = listOf(solid(0xFFD9D9D9)),
            )
            Tool.POLYGON -> ShapeNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                kind = ShapeKind.POLYGON, pointCount = 3, fills = listOf(solid(0xFFD9D9D9)),
            )
            Tool.LINE -> ShapeNode(
                id = id, name = name, parent = parent, transform = transform, width = w, height = 0f,
                kind = ShapeKind.LINE,
                stroke = Stroke(paints = listOf(solid(0xFF000000)), weight = 2f, align = StrokeAlign.CENTER),
            )
            Tool.TEXT -> {
                val content = "Type something"
                val style = TextStyle(fontFamily = "sans-serif", fontSize = 16f, fills = listOf(solid(0xFF000000)))
                TextNode(
                    id = id, name = name, parent = parent, transform = transform, width = w, height = h,
                    characters = content, runs = listOf(TextRun(0, content.length, style)),
                    autoSize = TextAutoSize.AUTO_WIDTH,
                ).estimated()
            }
            Tool.MOVE -> error("MOVE does not create nodes")
        }
}
