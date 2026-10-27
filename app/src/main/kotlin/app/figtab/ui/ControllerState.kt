package app.figtab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import app.figtab.domain.scene.DocumentController

/**
 * A counter that ticks every time the document or selection changes. Reading `.value` inside a
 * composable subscribes THAT composable directly, so it redraws immediately and never depends on a
 * parent recomposing (or on Compose deciding a child can be skipped).
 */
@Composable
fun rememberRevision(controller: DocumentController): State<Int> {
    val rev = remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { rev.intValue = rev.intValue + 1 }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    return rev
}
