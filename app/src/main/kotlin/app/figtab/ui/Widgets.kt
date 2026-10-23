package app.figtab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun fmt(v: Float): String =
    if (abs(v - v.roundToInt()) < 0.005f) v.roundToInt().toString() else String.format(Locale.US, "%.2f", v)

@Composable
fun PanelDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.Divider))
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Ui.TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel, color = Ui.TextHi, fontSize = 16.sp,
                modifier = Modifier.clickable { onAction() }.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun SmallButton(label: String, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (active) Ui.Accent else Ui.Field
    Text(
        text = label,
        color = if (enabled) Ui.TextHi else Ui.TextOff,
        fontSize = 11.sp,
        modifier = modifier
            .background(bg, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
fun Swatch(argb: Int) {
    Box(
        Modifier.size(20.dp)
            .background(Color(argb), RoundedCornerShape(4.dp))
            .border(1.dp, Ui.Divider, RoundedCornerShape(4.dp)),
    )
}

private val presetColors = listOf(
    0xFFD9D9D9, 0xFFFFFFFF, 0xFF000000, 0xFFF24822, 0xFFFFA629,
    0xFFFFCD29, 0xFF14AE5C, 0xFF0D99FF, 0xFF9747FF, 0xFFFF24BD,
)

@Composable
fun PresetSwatches(onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in presetColors) {
            Box(
                Modifier.size(20.dp)
                    .background(Color(c.toInt()), RoundedCornerShape(10.dp))
                    .border(1.dp, Ui.Divider, RoundedCornerShape(10.dp))
                    .clickable { onPick(c.toInt()) },
            )
        }
    }
}

/** Single-line numeric field. Commits on Done or when focus leaves. */
@Composable
fun NumField(value: Float, modifier: Modifier = Modifier, onCommit: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(fmt(value)) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val v = text.trim().toFloatOrNull()
        if (v != null && abs(v - value) > 1e-4f) onCommit(v) else text = fmt(value)
    }
    Box(modifier.background(Ui.Field, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 6.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                if (focused && !st.isFocused) commit()
                focused = st.isFocused
            },
        )
    }
}

@Composable
fun LabeledNum(label: String, value: Float, modifier: Modifier = Modifier, onCommit: (Float) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ui.TextLo, fontSize = 11.sp, modifier = Modifier.width(16.dp))
        NumField(value, Modifier.weight(1f), onCommit)
    }
}

/** RGB hex field ("1E1E1E"); accepts 6 or 8 digits. */
@Composable
fun HexField(argb: Int, modifier: Modifier = Modifier, onCommit: (Int) -> Unit) {
    val shown = String.format(Locale.US, "%06X", argb and 0xFFFFFF)
    var text by remember(argb) { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val t = text.trim().removePrefix("#")
        val v = t.toLongOrNull(16)
        if (v != null && (t.length == 6 || t.length == 8)) {
            val c = if (t.length == 6) (0xFF000000L or v).toInt() else v.toInt()
            if (c != argb) onCommit(c) else text = shown
        } else {
            text = shown
        }
    }
    Box(modifier.background(Ui.Field, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 6.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                if (focused && !st.isFocused) commit()
                focused = st.isFocused
            },
        )
    }
}

/** Free-form text field (layer names, text content). Commits when focus leaves or via [onCommit]. */
@Composable
fun TextEditField(value: String, key: Any, modifier: Modifier = Modifier, minHeight: Int = 0, singleLine: Boolean = false, onCommit: (String) -> Unit) {
    var text by remember(key, value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    fun commit() { if (text != value) onCommit(text) }
    Box(modifier.background(Ui.Field, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 8.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = singleLine,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth().height(if (minHeight > 0) minHeight.dp else 20.dp).onFocusChanged { st ->
                if (focused && !st.isFocused) commit()
                focused = st.isFocused
            },
        )
    }
}
