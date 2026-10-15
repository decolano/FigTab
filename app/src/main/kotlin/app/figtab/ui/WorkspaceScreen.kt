package app.figtab.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.figtab.domain.scene.DemoScene
import app.figtab.domain.viewport.ViewportState
import app.figtab.render.CanvasSurfaceView
import app.figtab.render.NativeCanvas
import app.figtab.render.PatchBuffer
import app.figtab.render.ScenePatchEncoder
import kotlin.math.roundToInt

/** Phase 1 shell: canvas + minimal top bar. Panels, inspector and tools arrive in later phases. */
@Composable
fun WorkspaceScreen(nativeCanvas: NativeCanvas) {
    val viewport = remember { ViewportState() }
    var zoomPercent by remember { mutableIntStateOf(100) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CanvasSurfaceView(ctx, nativeCanvas, viewport).also { view ->
                    view.onViewportChanged = { zoomPercent = (it.scale * 100f).roundToInt() }
                    val patch = PatchBuffer()
                    ScenePatchEncoder.encodeFull(DemoScene.create(), patch)
                    nativeCanvas.setBackground(0xFF2C2C2C.toInt())
                    nativeCanvas.applyPatch(patch.buffer, patch.length)
                }
            },
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .background(Color(0xCC1E1E1E), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("FigTab  ·  $zoomPercent%", color = Color.White)
        }
    }
}
