package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.figtab.domain.scene.DemoScene
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.viewport.ViewportState
import app.figtab.render.CanvasSurfaceView
import app.figtab.render.NativeCanvas
import kotlin.math.roundToInt

/**
 * Figma-style workspace: layers on the left, canvas in the middle (tool bar down its left edge,
 * edit menu at the bottom right), and the Design inspector on the right.
 */
@Composable
fun WorkspaceScreen(nativeCanvas: NativeCanvas) {
    val viewport = remember { ViewportState() }
    val controller = remember {
        DocumentController(DemoScene.create().copy(background = 0xFF1E1E1E.toInt()), viewport)
    }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var touchEdit by remember { mutableStateOf(false) }
    var showPanels by remember { mutableStateOf(true) }

    Row(Modifier.fillMaxSize().background(Ui.PanelBg)) {
        if (showPanels) LayersPanel(controller, 0, Modifier.width(220.dp).fillMaxHeight())

        Box(Modifier.weight(1f).fillMaxHeight()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CanvasSurfaceView(ctx, nativeCanvas, viewport, controller).also { view ->
                        view.onViewportChanged = { zoomPercent = (it.scale * 100f).roundToInt() }
                    }
                },
                update = { view -> view.fingerEditing = touchEdit },
            )
            ToolBar(
                controller, 0,
                Modifier.align(Alignment.CenterStart).statusBarsPadding().navigationBarsPadding().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
            EditMenu(
                controller = controller, zoomPercent = zoomPercent,
                touchEdit = touchEdit, onToggleTouch = { touchEdit = !touchEdit },
                showPanels = showPanels, onTogglePanels = { showPanels = !showPanels },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (showPanels) InspectorPanel(controller, 0, zoomPercent, Modifier.width(250.dp).fillMaxHeight())
    }
}

/** Undo / redo, input-mode toggles, panel toggle and the selection readout. */
@Composable
private fun EditMenu(
    controller: DocumentController,
    zoomPercent: Int,
    touchEdit: Boolean,
    onToggleTouch: () -> Unit,
    showPanels: Boolean,
    onTogglePanels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val rev = rememberRevision(controller).value
    Row(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .background(Color(0xCC1E1E1E), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FigTab · $zoomPercent%", color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp))
        Chip("Undo", enabled = controller.canUndo) { controller.undo() }
        Chip("Redo", enabled = controller.canRedo) { controller.redo() }
        Chip(if (touchEdit) "Touch edit: ON" else "Touch edit: OFF", active = touchEdit, onClick = onToggleTouch)
        Chip(if (controller.multiSelectMode) "Multi: ON" else "Multi: OFF", active = controller.multiSelectMode) {
            controller.multiSelectMode = !controller.multiSelectMode
        }
        Chip(if (showPanels) "Panels: ON" else "Panels: OFF", active = showPanels, onClick = onTogglePanels)
        Text(
            controller.selectionSummary(), color = Color(0xFFB3B3B3), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun Chip(label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = when {
        !enabled -> Color(0x66FFFFFF)
        active -> Color(0xFF0D99FF)
        else -> Color.White
    }
    Text(
        text = label,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
