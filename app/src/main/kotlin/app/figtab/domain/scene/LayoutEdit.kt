package app.figtab.domain.scene

// ───────── layout-related node edits ─────────

fun SceneNode.autoLayoutOrNull(): AutoLayout? = when (this) {
    is FrameNode -> autoLayout
    is ComponentNode -> autoLayout
    else -> null
}

fun SceneNode.withAutoLayout(al: AutoLayout): SceneNode = when (this) {
    is FrameNode -> copy(autoLayout = al)
    is ComponentNode -> copy(autoLayout = al)
    else -> this
}

fun SceneNode.clipsOrNull(): Boolean? = when (this) {
    is FrameNode -> clipsContent
    is ComponentNode -> clipsContent
    else -> null
}

fun SceneNode.withClips(v: Boolean): SceneNode = when (this) {
    is FrameNode -> copy(clipsContent = v)
    is ComponentNode -> copy(clipsContent = v)
    else -> this
}

fun SceneNode.withLayoutChild(lc: LayoutChild): SceneNode = when (this) {
    is SectionNode -> copy(layoutChild = lc)
    is FrameNode -> copy(layoutChild = lc)
    is GroupNode -> copy(layoutChild = lc)
    is ShapeNode -> copy(layoutChild = lc)
    is VectorNetworkNode -> copy(layoutChild = lc)
    is TextNode -> copy(layoutChild = lc)
    is ComponentNode -> copy(layoutChild = lc)
    is InstanceNode -> copy(layoutChild = lc)
}
