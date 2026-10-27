package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.scene.Tool

/** Vertical tool bar on the left edge of the canvas: Move, Frame, shapes, Text and Pen. */
@Composable
fun ToolBar(controller: DocumentController, @Suppress("UNUSED_PARAMETER") version: Int, modifier: Modifier = Modifier) {
    val current = controller.tool
    @Suppress("UNUSED_VARIABLE") val rev = rememberRevision(controller).value
    Column(
        modifier.background(Ui.PanelBg, RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState()).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolButton("↖", "Move", current == Tool.MOVE) { controller.tool = Tool.MOVE }
        ToolButton("#", "Frame", current == Tool.FRAME) { controller.tool = Tool.FRAME }
        ToolButton("□", "Rect", current == Tool.RECT) { controller.tool = Tool.RECT }
        ToolButton("○", "Ellipse", current == Tool.ELLIPSE) { controller.tool = Tool.ELLIPSE }
        ToolButton("/", "Line", current == Tool.LINE) { controller.tool = Tool.LINE }
        ToolButton("☆", "Star", current == Tool.STAR) { controller.tool = Tool.STAR }
        ToolButton("△", "Polygon", current == Tool.POLYGON) { controller.tool = Tool.POLYGON }
        ToolButton("T", "Text", current == Tool.TEXT) { controller.tool = Tool.TEXT }
        DisabledTool("✎", "Pen")
    }
}

@Composable
private fun ToolButton(glyph: String, caption: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(54.dp)
            .background(if (active) Ui.Accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = Ui.TextHi, fontSize = 18.sp)
        Text(caption, color = if (active) Ui.TextHi else Ui.TextLo, fontSize = 9.sp)
    }
}

@Composable
private fun DisabledTool(glyph: String, caption: String) {
    Column(Modifier.width(54.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = Ui.TextOff, fontSize = 18.sp)
        Text(caption, color = Ui.TextOff, fontSize = 9.sp)
    }
}
