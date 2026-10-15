package app.figtab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.figtab.render.NativeCanvas
import app.figtab.ui.WorkspaceScreen

class FigTabActivity : ComponentActivity() {
    private lateinit var nativeCanvas: NativeCanvas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nativeCanvas = NativeCanvas()
        setContent { WorkspaceScreen(nativeCanvas) }
    }

    override fun onDestroy() {
        nativeCanvas.close()
        super.onDestroy()
    }
}
