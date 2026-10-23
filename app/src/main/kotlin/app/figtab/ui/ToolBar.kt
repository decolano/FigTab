package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.scene.Tool

/** Floating bottom tool bar, like Figma's. */
@Composable
fun ToolBar(controller: DocumentController, @Suppress("UNUSED_PARAMETER") version: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Ui.PanelBg, RoundedCornerShape(12.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("↖", "Move", Tool.MOVE, controller)
        ToolButton("#", "Frame", Tool.FRAME, controller)
        ToolButton("□", "Rect", Tool.RECT, controller)
        ToolButton("○", "Ellipse", Tool.ELLIPSE, controller)
        ToolButton("/", "Line", Tool.LINE, controller)
        ToolButton("☆", "Star", Tool.STAR, controller)
        ToolButton("△", "Polygon", Tool.POLYGON, controller)
        ToolButton("T", "Text", Tool.TEXT, controller)
        DisabledTool("✎", "Pen")
    }
}

@Composable
private fun ToolButton(glyph: String, caption: String, tool: Tool, controller: DocumentController) {
    val active = controller.tool == tool
    Column(
        Modifier.width(58.dp)
            .background(if (active) Ui.Accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { controller.tool = tool }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, color = Ui.TextHi, fontSize = 18.sp)
        Text(caption, color = if (active) Ui.TextHi else Ui.TextLo, fontSize = 9.sp)
    }
}

@Composable
private fun DisabledTool(glyph: String, caption: String) {
    Column(Modifier.width(58.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = Ui.TextOff, fontSize = 18.sp)
        Text(caption, color = Ui.TextOff, fontSize = 9.sp)
    }
}
