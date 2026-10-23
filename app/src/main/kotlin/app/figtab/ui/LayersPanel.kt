package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figtab.domain.scene.Canvas
import app.figtab.domain.scene.ContainerNode
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.scene.FrameNode
import app.figtab.domain.scene.GroupNode
import app.figtab.domain.scene.ComponentNode
import app.figtab.domain.scene.InstanceNode
import app.figtab.domain.scene.NodeId
import app.figtab.domain.scene.SceneNode
import app.figtab.domain.scene.SectionNode
import app.figtab.domain.scene.ShapeKind
import app.figtab.domain.scene.ShapeNode
import app.figtab.domain.scene.TextNode
import app.figtab.domain.scene.VectorNetworkNode

private class LayerRow(val id: NodeId, val depth: Int, val hasChildren: Boolean, val node: SceneNode)

private fun flatten(canvas: Canvas, collapsed: Set<NodeId>): List<LayerRow> {
    val out = ArrayList<LayerRow>()
    fun visit(id: NodeId, depth: Int) {
        val n = canvas[id] ?: return
        val kids = (n as? ContainerNode)?.children
        out.add(LayerRow(id, depth, kids != null && kids.isNotEmpty(), n))
        if (kids != null && id !in collapsed) {
            for (c in kids.asReversed()) visit(c, depth + 1)
        }
    }
    for (r in canvas.roots.asReversed()) visit(r, 0)
    return out
}

private fun glyphOf(n: SceneNode): String = when (n) {
    is FrameNode -> "#"
    is SectionNode -> "§"
    is GroupNode -> "G"
    is TextNode -> "T"
    is ComponentNode -> "◆"
    is InstanceNode -> "◇"
    is VectorNetworkNode -> "✎"
    is ShapeNode -> when (n.kind) {
        ShapeKind.RECTANGLE -> "□"
        ShapeKind.ELLIPSE -> "○"
        ShapeKind.STAR -> "★"
        ShapeKind.POLYGON -> "△"
        ShapeKind.LINE -> "/"
    }
}

/** Left panel: file name, pages and the layer tree (top-most layer first). */
@Composable
fun LayersPanel(controller: DocumentController, @Suppress("UNUSED_PARAMETER") version: Int, modifier: Modifier = Modifier) {
    var collapsed by remember { mutableStateOf(setOf<NodeId>()) }
    val rows = flatten(controller.canvas, collapsed)
    val selected = controller.selection

    Column(modifier.background(Ui.PanelBg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Untitled", color = Ui.TextHi, fontSize = 14.sp)
            Text("FigTab · local file", color = Ui.TextLo, fontSize = 11.sp)
        }
        PanelDivider()
        SectionHeader("Pages")
        Text(
            "Page 1", color = Ui.TextHi, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                .background(Ui.Field, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
        PanelDivider()
        SectionHeader("Layers")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.id.raw }) { row ->
                val isSelected = row.id in selected
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSelected) Ui.Accent.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { controller.selectNode(row.id, controller.multiSelectMode) }
                        .padding(start = (6 + row.depth * 12).dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (!row.hasChildren) " " else if (row.id in collapsed) "▸" else "▾",
                        color = Ui.TextLo, fontSize = 12.sp,
                        modifier = Modifier.width(18.dp).clickable(enabled = row.hasChildren) {
                            collapsed = if (row.id in collapsed) collapsed - row.id else collapsed + row.id
                        },
                    )
                    Text(glyphOf(row.node), color = Ui.TextLo, fontSize = 12.sp, modifier = Modifier.width(18.dp))
                    Text(
                        row.node.name, color = if (row.node.visible) Ui.TextHi else Ui.TextOff, fontSize = 12.sp,
                        maxLines = 1, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "🔒", fontSize = 12.sp,
                        modifier = Modifier.alpha(if (row.node.locked) 1f else 0.2f)
                            .clickable { controller.toggleLocked(row.id) }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "👁", fontSize = 12.sp,
                        modifier = Modifier.alpha(if (row.node.visible) 1f else 0.25f)
                            .clickable { controller.toggleVisible(row.id) }.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
