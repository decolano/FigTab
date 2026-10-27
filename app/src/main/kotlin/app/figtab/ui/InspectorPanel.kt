package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.scene.Effect
import app.figtab.domain.scene.Paint
import app.figtab.domain.scene.SceneNode
import app.figtab.domain.scene.ShapeKind
import app.figtab.domain.scene.ShapeNode
import app.figtab.domain.scene.Stroke
import app.figtab.domain.scene.StrokeAlign
import app.figtab.domain.scene.TextAlignH
import app.figtab.domain.scene.TextAutoSize
import app.figtab.domain.scene.TextNode
import app.figtab.domain.scene.Vec2
import app.figtab.domain.scene.fillsOrNull
import app.figtab.domain.scene.strokeOrNull
import app.figtab.domain.scene.withCommon
import app.figtab.domain.scene.withFills
import app.figtab.domain.scene.withStroke
import app.figtab.domain.scene.withText
import app.figtab.domain.scene.withTextAlign
import app.figtab.domain.scene.withTextAutoSize
import app.figtab.domain.scene.withTextStyle
import kotlin.math.max

private fun <T> replaceAt(list: List<T>, index: Int, value: T): List<T> =
    list.mapIndexed { j, x -> if (j == index) value else x }

private val rowPad = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)

/** Right panel: the "Design" tab with properties of the selection (or of the page). */
@Composable
fun InspectorPanel(
    controller: DocumentController,
    @Suppress("UNUSED_PARAMETER") version: Int,
    zoomPercent: Int,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val rev = rememberRevision(controller).value
    val nodes = controller.selectedNodes()
    Column(
        modifier.background(Ui.PanelBg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Design", color = Ui.TextHi, fontSize = 13.sp,
                modifier = Modifier.background(Ui.Field, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text("Prototype", color = Ui.TextOff, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Spacer(Modifier.weight(1f))
            Text("$zoomPercent%", color = Ui.TextLo, fontSize = 13.sp)
        }
        PanelDivider()
        if (nodes.isEmpty()) PageSection(controller) else NodeSections(controller, nodes)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PageSection(controller: DocumentController) {
    @Suppress("UNUSED_VARIABLE") val rev = rememberRevision(controller).value
    SectionHeader("Page")
    val bg = controller.canvas.background
    Row(
        rowPad, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Swatch(bg)
        HexField(bg, Modifier.weight(1f)) { c -> controller.setBackground(c) }
    }
    PresetSwatches { c -> controller.setBackground(c) }
    PanelDivider()
    FramePresets(controller)
    PanelDivider()
    Text(
        "Pick a tool in the bar below and drag on the canvas to draw. Tap a layer to edit it here.",
        color = Ui.TextLo, fontSize = 11.sp, modifier = Modifier.padding(12.dp),
    )
}

@Composable
private fun NodeSections(controller: DocumentController, nodes: List<SceneNode>) {
    @Suppress("UNUSED_VARIABLE") val rev = rememberRevision(controller).value
    val first = nodes.first()

    // ── Layer name ──
    if (nodes.size == 1) {
        SectionHeader("Layer")
        Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            TextEditField(first.name, first.id, Modifier.fillMaxWidth(), singleLine = true) { v ->
                controller.editNode(first.id) { n -> n.withCommon(name = v.ifBlank { n.name }) }
            }
        }
    } else {
        Text("${nodes.size} layers selected", color = Ui.TextHi, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
    }

    // ── Position & size ──
    PanelDivider()
    SectionHeader("Position")
    val b = controller.selectionBounds()
    if (b != null) {
        Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledNum("X", b.x, Modifier.weight(1f)) { v -> controller.setSelectionBounds(v, null, null, null) }
            LabeledNum("Y", b.y, Modifier.weight(1f)) { v -> controller.setSelectionBounds(null, v, null, null) }
        }
        Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledNum("W", b.w, Modifier.weight(1f)) { v -> controller.setSelectionBounds(null, null, v, null) }
            LabeledNum("H", b.h, Modifier.weight(1f)) { v -> controller.setSelectionBounds(null, null, null, v) }
        }
    }
    val rotation = controller.rotationOfSelection()
    val radius = controller.cornerRadiusOfSelection()
    if (rotation != null || radius != null) {
        Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rotation != null) LabeledNum("∠", rotation, Modifier.weight(1f)) { v -> controller.setRotation(v) }
            else Spacer(Modifier.weight(1f))
            if (radius != null) LabeledNum("R", radius, Modifier.weight(1f)) { v -> controller.setCornerRadius(v) }
            else Spacer(Modifier.weight(1f))
        }
    }

    // ── Auto layout, resizing rules, constraints ──
    LayoutSections(controller, first)

    // ── Appearance ──
    PanelDivider()
    SectionHeader("Appearance")
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("%", first.opacity * 100f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withCommon(opacity = (v / 100f).coerceIn(0f, 1f)) }
        }
        Spacer(Modifier.weight(1f))
    }

    // ── Text ──
    if (first is TextNode) TextSection(controller, first)

    // ── Fill & stroke ──
    val fills = first.fillsOrNull()
    val isLine = first is ShapeNode && first.kind == ShapeKind.LINE
    if (fills != null && !isLine) {
        PanelDivider()
        SectionHeader("Fill", "+") {
            controller.editSelection { n -> n.withFills(n.fillsOrNull().orEmpty() + Paint.Solid(0xFFD9D9D9.toInt())) }
        }
        fills.forEachIndexed { i, p ->
            Row(
                rowPad, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (p is Paint.Solid) {
                    Swatch(p.color)
                    HexField(p.color, Modifier.weight(1f)) { c ->
                        controller.editSelection { n -> n.withFills(replaceAt(fills, i, p.copy(color = c))) }
                    }
                    NumField(p.opacity * 100f, Modifier.width(52.dp)) { v ->
                        controller.editSelection { n -> n.withFills(replaceAt(fills, i, p.copy(opacity = (v / 100f).coerceIn(0f, 1f)))) }
                    }
                } else {
                    Text(if (p is Paint.Image) "Image" else "Gradient", color = Ui.TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Text(
                    "−", color = Ui.TextHi, fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        controller.editSelection { n -> n.withFills(fills.filterIndexed { j, _ -> j != i }) }
                    }.padding(horizontal = 8.dp),
                )
            }
        }
        PresetSwatches { c ->
            controller.editSelection { n ->
                val cur = n.fillsOrNull().orEmpty()
                val next: List<Paint> =
                    if (cur.isEmpty()) listOf(Paint.Solid(c))
                    else replaceAt(cur, 0, Paint.Solid(c, cur[0].visible, cur[0].opacity))
                n.withFills(next)
            }
        }
    }
    if (fills != null) StrokeSection(controller, first)

    // ── Effects ──
    PanelDivider()
    SectionHeader("Effects")
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton("+ Drop shadow") { addEffect(controller, Effect.DropShadow(0x40000000, Vec2(0f, 4f), 8f, 0f)) }
        SmallButton("+ Layer blur") { addEffect(controller, Effect.LayerBlur(8f)) }
    }
    first.effects.forEachIndexed { i, e -> EffectRow(controller, i, e) }

    // ── Arrange ──
    PanelDivider()
    SectionHeader("Arrange")
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton("To front") { controller.arrange(true) }
        SmallButton("To back") { controller.arrange(false) }
        SmallButton("Delete") { controller.deleteSelection() }
    }
}

private fun addEffect(controller: DocumentController, e: Effect) {
    controller.editSelection { n -> n.withCommon(effects = n.effects + e) }
}

private fun editStroke(controller: DocumentController, f: (Stroke) -> Stroke) {
    controller.editSelection { n ->
        val st = n.strokeOrNull()
        if (st == null) n else n.withStroke(f(st))
    }
}

@Composable
private fun StrokeSection(controller: DocumentController, first: SceneNode) {
    PanelDivider()
    val stroke = first.strokeOrNull()
    if (stroke == null) {
        SectionHeader("Stroke", "+") {
            controller.editSelection { n ->
                n.withStroke(Stroke(paints = listOf(Paint.Solid(0xFF000000.toInt())), weight = 1f, align = StrokeAlign.INSIDE))
            }
        }
        return
    }
    SectionHeader("Stroke", "−") { controller.editSelection { n -> n.withStroke(null) } }
    val sp = stroke.paints.firstOrNull()
    Row(
        rowPad, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sp is Paint.Solid) {
            Swatch(sp.color)
            HexField(sp.color, Modifier.weight(1f)) { c ->
                editStroke(controller) { st -> st.copy(paints = listOf(sp.copy(color = c))) }
            }
        } else {
            Text("Gradient", color = Ui.TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        LabeledNum("W", stroke.weight, Modifier.width(84.dp)) { v ->
            editStroke(controller) { st -> st.copy(weight = max(v, 0f)) }
        }
    }
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Inside", active = stroke.align == StrokeAlign.INSIDE) { editStroke(controller) { st -> st.copy(align = StrokeAlign.INSIDE) } }
        SmallButton("Center", active = stroke.align == StrokeAlign.CENTER) { editStroke(controller) { st -> st.copy(align = StrokeAlign.CENTER) } }
        SmallButton("Outside", active = stroke.align == StrokeAlign.OUTSIDE) { editStroke(controller) { st -> st.copy(align = StrokeAlign.OUTSIDE) } }
    }
    PresetSwatches { c ->
        editStroke(controller) { st -> st.copy(paints = listOf(Paint.Solid(c))) }
    }
}

@Composable
private fun EffectRow(controller: DocumentController, index: Int, e: Effect) {
    fun replace(ne: Effect) {
        controller.editSelection { n -> n.withCommon(effects = replaceAt(n.effects, index, ne)) }
    }
    fun remove() {
        controller.editSelection { n -> n.withCommon(effects = n.effects.filterIndexed { j, _ -> j != index }) }
    }
    Column(rowPad) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = when (e) {
                is Effect.DropShadow -> "Drop shadow"
                is Effect.InnerShadow -> "Inner shadow"
                is Effect.LayerBlur -> "Layer blur"
                is Effect.BackgroundBlur -> "Background blur"
            }
            Text(title, color = Ui.TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("−", color = Ui.TextHi, fontSize = 16.sp, modifier = Modifier.clickable { remove() }.padding(horizontal = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (e) {
                is Effect.DropShadow -> {
                    LabeledNum("X", e.offset.x, Modifier.weight(1f)) { v -> replace(e.copy(offset = Vec2(v, e.offset.y))) }
                    LabeledNum("Y", e.offset.y, Modifier.weight(1f)) { v -> replace(e.copy(offset = Vec2(e.offset.x, v))) }
                    LabeledNum("B", e.blur, Modifier.weight(1f)) { v -> replace(e.copy(blur = max(v, 0f))) }
                }
                is Effect.InnerShadow -> {
                    LabeledNum("X", e.offset.x, Modifier.weight(1f)) { v -> replace(e.copy(offset = Vec2(v, e.offset.y))) }
                    LabeledNum("Y", e.offset.y, Modifier.weight(1f)) { v -> replace(e.copy(offset = Vec2(e.offset.x, v))) }
                    LabeledNum("B", e.blur, Modifier.weight(1f)) { v -> replace(e.copy(blur = max(v, 0f))) }
                }
                is Effect.LayerBlur -> {
                    LabeledNum("B", e.radius, Modifier.weight(1f)) { v -> replace(e.copy(radius = max(v, 0f))) }
                }
                is Effect.BackgroundBlur -> {
                    LabeledNum("B", e.radius, Modifier.weight(1f)) { v -> replace(e.copy(radius = max(v, 0f))) }
                }
            }
        }
    }
}

@Composable
private fun TextSection(controller: DocumentController, t: TextNode) {
    val style = t.runs.firstOrNull()?.style
    PanelDivider()
    SectionHeader("Text")
    Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        TextEditField(t.characters, t.id, Modifier.fillMaxWidth(), minHeight = 72) { v ->
            controller.editSelection { n -> n.withText(v) }
        }
    }
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("Sz", style?.fontSize ?: 16f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withTextStyle { s -> s.copy(fontSize = max(v, 1f)) } }
        }
        LabeledNum("LH", style?.lineHeightPx ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withTextStyle { s -> s.copy(lineHeightPx = if (v <= 0f) null else v) } }
        }
    }
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNum("LS", style?.letterSpacingPx ?: 0f, Modifier.weight(1f)) { v ->
            controller.editSelection { n -> n.withTextStyle { s -> s.copy(letterSpacingPx = v) } }
        }
        Spacer(Modifier.weight(1f))
    }
    val weight = style?.fontWeight ?: 400
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Regular", active = weight == 400) { controller.editSelection { n -> n.withTextStyle { s -> s.copy(fontWeight = 400) } } }
        SmallButton("Medium", active = weight == 500) { controller.editSelection { n -> n.withTextStyle { s -> s.copy(fontWeight = 500) } } }
        SmallButton("Bold", active = weight == 700) { controller.editSelection { n -> n.withTextStyle { s -> s.copy(fontWeight = 700) } } }
    }
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Left", active = t.alignH == TextAlignH.LEFT) { controller.editSelection { n -> n.withTextAlign(TextAlignH.LEFT) } }
        SmallButton("Center", active = t.alignH == TextAlignH.CENTER) { controller.editSelection { n -> n.withTextAlign(TextAlignH.CENTER) } }
        SmallButton("Right", active = t.alignH == TextAlignH.RIGHT) { controller.editSelection { n -> n.withTextAlign(TextAlignH.RIGHT) } }
    }
    Row(rowPad, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("Auto W", active = t.autoSize == TextAutoSize.AUTO_WIDTH) { controller.editSelection { n -> n.withTextAutoSize(TextAutoSize.AUTO_WIDTH) } }
        SmallButton("Auto H", active = t.autoSize == TextAutoSize.AUTO_HEIGHT) { controller.editSelection { n -> n.withTextAutoSize(TextAutoSize.AUTO_HEIGHT) } }
        SmallButton("Fixed", active = t.autoSize == TextAutoSize.FIXED) { controller.editSelection { n -> n.withTextAutoSize(TextAutoSize.FIXED) } }
    }
    val color = (style?.fills?.firstOrNull() as? Paint.Solid)?.color ?: 0xFF000000.toInt()
    Row(rowPad, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch(color)
        HexField(color, Modifier.weight(1f)) { c ->
            controller.editSelection { n -> n.withTextStyle { s -> s.copy(fills = listOf(Paint.Solid(c))) } }
        }
    }
    PresetSwatches { c ->
        controller.editSelection { n -> n.withTextStyle { s -> s.copy(fills = listOf(Paint.Solid(c))) } }
    }
}
