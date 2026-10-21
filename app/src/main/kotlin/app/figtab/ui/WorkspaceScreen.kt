package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.figtab.domain.scene.DemoScene
import app.figtab.domain.scene.DocumentController
import app.figtab.domain.viewport.ViewportState
import app.figtab.render.CanvasSurfaceView
import app.figtab.render.NativeCanvas
import kotlin.math.roundToInt

/** Phase 3 shell: canvas + a compact toolbar (undo/redo, input mode toggles, selection readout). */
@Composable
fun WorkspaceScreen(nativeCanvas: NativeCanvas) {
    val viewport = remember { ViewportState() }
    val controller = remember { DocumentController(DemoScene.create(), viewport) }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var tick by remember { mutableIntStateOf(0) }
    var touchEdit by remember { mutableStateOf(false) }

    remember(controller) {
        controller.addListener { tick++ }
        0
    }
    @Suppress("UNUSED_VARIABLE") val observed = tick   // re-read controller state whenever it changes

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CanvasSurfaceView(ctx, nativeCanvas, viewport, controller).also { view ->
                    view.onViewportChanged = { zoomPercent = (it.scale * 100f).roundToInt() }
                    nativeCanvas.setBackground(0xFF2C2C2C.toInt())
                }
            },
            update = { view -> view.fingerEditing = touchEdit },
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .background(Color(0xCC1E1E1E), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FigTab · $zoomPercent%", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp))
            Chip("Undo", enabled = controller.canUndo) { controller.undo() }
            Chip("Redo", enabled = controller.canRedo) { controller.redo() }
            Chip(if (touchEdit) "Touch edit: ON" else "Touch edit: OFF", active = touchEdit) { touchEdit = !touchEdit }
            Chip(if (controller.multiSelectMode) "Multi: ON" else "Multi: OFF", active = controller.multiSelectMode) {
                controller.multiSelectMode = !controller.multiSelectMode
            }
            Text(controller.selectionSummary(), color = Color(0xFFB3B3B3), modifier = Modifier.padding(horizontal = 10.dp))
        }
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
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
