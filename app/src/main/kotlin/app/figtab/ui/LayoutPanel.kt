package app.figtab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figtab.domain.scene.AutoLayout
import app.figtab.domain.scene.AxisConstraint
import app.figtab.domain.scene.AxisSizing
import app.figtab.domain.scene.ContainerNode
import app.figtab.domain.scene.CounterAlign
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.scene.GroupNode
import app.figtab.domain.scene.LayoutMode
import app.figtab.domain.scene.Padding
import app.figtab.domain.scene.PrimaryAlign
import app.figtab.domain.scene.SceneNode
import app.figtab.domain.scene.TextNode
import app.figtab.domain.scene.autoLayoutOrNull
import app.figtab.domain.scene.clipsOrNull
import app.figtab.domain.scene.withAutoLayout
import app.figtab.domain.scene.withClips
import app.figtab.domain.scene.withLayoutChild
import kotlin.math.max

private val rowMod = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)

private class Preset(val name: String, val w: Float, val h: Float)

private val presets = listOf(
    Preset("iPhone 15", 393f, 852f),
    Preset("Android compact", 412f, 917f),
    Preset("iPad Pro 11\"", 834f, 1194f),
    Preset("Android tablet", 800f, 1280f),
    Preset("MacBook Air", 1440f, 900f),
    Preset("Desktop", 1440f, 1024f),
    Preset("Apple Watch 45mm", 198f, 242f),
    Preset("Presentation 16:9", 1920f, 1080f),
)

/** Device and presentation frame presets, dropped at the center of the view. */
@Composable
fun FramePresets(controller: DocumentController) {
    SectionHeader("Frame presets")
    for (p in presets) {
        Row(
            Modifier.fillMaxWidth().clickable { controller.createFrame(p.name, p.w, p.h) }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.name, color = Ui.TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${p.w.toInt()} × ${p.h.toInt()}", color = Ui.TextLo, fontSize = 11.sp)
        }
    }
}

/** Auto layout, resizing rules and constraints for the selection. */
@Composable
fun LayoutSections(controller: DocumentController, first: SceneNode) {
    val parent = first.parent?.let { controller.canvas[it] }
    val parentAuto = (parent?.autoLayoutOrNull()?.mode ?: LayoutMode.NONE) != LayoutMode.NONE
    val al = first.autoLayoutOrNull()

    if (al != null) AutoLayoutSection(controller, first, al)
    ResizingSection(controller, first, parentAuto)
    if (parent is ContainerNode && parent !is GroupNode && !parentAuto) ConstraintsSection(controller, first)
}

private fun editAL(controller: DocumentController, f: (AutoLayout) -> AutoLayout) {
    controller.editSelection { n ->
        val a = n.autoLayoutOrNull()
        if (a == null) n else n.withAutoLayout(f(a))
    }
}

@Composable
private fun AutoLayoutSection(controller: DocumentController, first: SceneNode, al: AutoLayout) {
    PanelDivider()
    if (al.mode == LayoutMode.NONE) {
        SectionHeader("Auto layout", "+") {
            editAL(controller) { AutoLayout(mode = LayoutMode.VERTICAL, gap = 10f, padding = Padding(10f, 10f, 10f, 10f)) }
        }
        return
    }
    SectionHeader("Auto layout", "−") { editAL(controller) { a -> a.copy(mode = LayoutMode.NONE) } }

    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("↓ Vertical", active = al.mode == LayoutMode.VERTICAL) { editAL(controller) { a -> a.copy(mode = LayoutMode.VERTICAL) } }
        SmallButton("→ Horizontal", active = al.mode == LayoutMode.HORIZONTAL) { editAL(controller) { a -> a.copy(mode = LayoutMode.HORIZONTAL) } }
        SmallButton("Wrap", active = al.mode == LayoutMode.WRAP) { editAL(controller) { a -> a.copy(mode = LayoutMode.WRAP) } }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("G", al.gap, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(gap = max(v, 0f)) } }
        if (al.mode == LayoutMode.WRAP) {
            LabeledNum("CG", al.crossGap, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(crossGap = max(v, 0f)) } }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("L", al.padding.left, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(padding = a.padding.copy(left = max(v, 0f))) } }
        LabeledNum("T", al.padding.top, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(padding = a.padding.copy(top = max(v, 0f))) } }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("R", al.padding.right, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(padding = a.padding.copy(right = max(v, 0f))) } }
        LabeledNum("B", al.padding.bottom, Modifier.weight(1f)) { v -> editAL(controller) { a -> a.copy(padding = a.padding.copy(bottom = max(v, 0f))) } }
    }
    Text("Main axis", color = Ui.TextLo, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Start", active = al.primaryAlign == PrimaryAlign.START) { editAL(controller) { a -> a.copy(primaryAlign = PrimaryAlign.START) } }
        SmallButton("Center", active = al.primaryAlign == PrimaryAlign.CENTER) { editAL(controller) { a -> a.copy(primaryAlign = PrimaryAlign.CENTER) } }
        SmallButton("End", active = al.primaryAlign == PrimaryAlign.END) { editAL(controller) { a -> a.copy(primaryAlign = PrimaryAlign.END) } }
        SmallButton("Space", active = al.primaryAlign == PrimaryAlign.SPACE_BETWEEN) { editAL(controller) { a -> a.copy(primaryAlign = PrimaryAlign.SPACE_BETWEEN) } }
    }
    Text("Cross axis", color = Ui.TextLo, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Start", active = al.counterAlign == CounterAlign.START) { editAL(controller) { a -> a.copy(counterAlign = CounterAlign.START) } }
        SmallButton("Center", active = al.counterAlign == CounterAlign.CENTER) { editAL(controller) { a -> a.copy(counterAlign = CounterAlign.CENTER) } }
        SmallButton("End", active = al.counterAlign == CounterAlign.END) { editAL(controller) { a -> a.copy(counterAlign = CounterAlign.END) } }
        SmallButton("Stretch", active = al.counterAlign == CounterAlign.STRETCH) { editAL(controller) { a -> a.copy(counterAlign = CounterAlign.STRETCH) } }
    }
    val clips = first.clipsOrNull() ?: true
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton(if (al.reverseZ) "First on top" else "Last on top", active = al.reverseZ) {
            editAL(controller) { a -> a.copy(reverseZ = !a.reverseZ) }
        }
        SmallButton(if (clips) "Clip: ON" else "Clip: OFF", active = clips) {
            controller.editSelection { n -> n.withClips(!(n.clipsOrNull() ?: true)) }
        }
    }
}

private fun setSizing(controller: DocumentController, isX: Boolean, kind: Int) {
    controller.editSelection { n ->
        val lc = n.layoutChild
        val s: AxisSizing = when (kind) {
            0 -> AxisSizing.Fixed(if (isX) n.width else n.height)
            1 -> AxisSizing.HugContent
            else -> AxisSizing.FillContainer(1f)
        }
        n.withLayoutChild(if (isX) lc.copy(widthSizing = s) else lc.copy(heightSizing = s))
    }
}

@Composable
private fun ResizingSection(controller: DocumentController, first: SceneNode, parentAuto: Boolean) {
    PanelDivider()
    SectionHeader("Resizing")
    val lc = first.layoutChild
    val canHug = first is ContainerNode || first is TextNode
    for (isX in listOf(true, false)) {
        val s = if (isX) lc.widthSizing else lc.heightSizing
        Row(rowMod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (isX) "W" else "H", color = Ui.TextLo, fontSize = 11.sp, modifier = Modifier.width(16.dp))
            SmallButton("Fixed", active = s is AxisSizing.Fixed) { setSizing(controller, isX, 0) }
            SmallButton("Hug", active = s is AxisSizing.HugContent, enabled = canHug) { setSizing(controller, isX, 1) }
            SmallButton("Fill", active = s is AxisSizing.FillContainer, enabled = parentAuto) { setSizing(controller, isX, 2) }
        }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("↧W", lc.minW ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withLayoutChild(n.layoutChild.copy(minW = if (v <= 0f) null else v)) }
        }
        LabeledNum("↥W", lc.maxW ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withLayoutChild(n.layoutChild.copy(maxW = if (v <= 0f) null else v)) }
        }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("↧H", lc.minH ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withLayoutChild(n.layoutChild.copy(minH = if (v <= 0f) null else v)) }
        }
        LabeledNum("↥H", lc.maxH ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withLayoutChild(n.layoutChild.copy(maxH = if (v <= 0f) null else v)) }
        }
    }
    Text("0 = no limit", color = Ui.TextLo, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp))
    if (parentAuto) {
        Row(rowMod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(if (lc.absolute) "Absolute position: ON" else "Absolute position: OFF", active = lc.absolute) {
                controller.editSelection { n -> n.withLayoutChild(n.layoutChild.copy(absolute = !n.layoutChild.absolute)) }
            }
        }
    }
}

private fun setConstraint(controller: DocumentController, isX: Boolean, v: AxisConstraint) {
    controller.editSelection { n ->
        val lc = n.layoutChild
        val k = lc.constraints
        n.withLayoutChild(lc.copy(constraints = if (isX) k.copy(horizontal = v) else k.copy(vertical = v)))
    }
}

@Composable
private fun ConstraintsSection(controller: DocumentController, first: SceneNode) {
    PanelDivider()
    SectionHeader("Constraints")
    val k = first.layoutChild.constraints
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SmallButton("Left", active = k.horizontal == AxisConstraint.MIN) { setConstraint(controller, true, AxisConstraint.MIN) }
        SmallButton("Right", active = k.horizontal == AxisConstraint.MAX) { setConstraint(controller, true, AxisConstraint.MAX) }
        SmallButton("L+R", active = k.horizontal == AxisConstraint.STRETCH) { setConstraint(controller, true, AxisConstraint.STRETCH) }
        SmallButton("Center", active = k.horizontal == AxisConstraint.CENTER) { setConstraint(controller, true, AxisConstraint.CENTER) }
        SmallButton("Scale", active = k.horizontal == AxisConstraint.SCALE) { setConstraint(controller, true, AxisConstraint.SCALE) }
    }
    Row(rowMod, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SmallButton("Top", active = k.vertical == AxisConstraint.MIN) { setConstraint(controller, false, AxisConstraint.MIN) }
        SmallButton("Bottom", active = k.vertical == AxisConstraint.MAX) { setConstraint(controller, false, AxisConstraint.MAX) }
        SmallButton("T+B", active = k.vertical == AxisConstraint.STRETCH) { setConstraint(controller, false, AxisConstraint.STRETCH) }
        SmallButton("Center", active = k.vertical == AxisConstraint.CENTER) { setConstraint(controller, false, AxisConstraint.CENTER) }
        SmallButton("Scale", active = k.vertical == AxisConstraint.SCALE) { setConstraint(controller, false, AxisConstraint.SCALE) }
    }
}
