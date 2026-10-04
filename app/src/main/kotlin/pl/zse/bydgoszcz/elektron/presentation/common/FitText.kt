package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Dopasowanie w jednym przebiegu kompozycji; ponowne obliczenie po zmianie szerokości. */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = 12.sp,
    minSize: TextUnit = 8.sp
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val size = remember(text, style, minSize, maxSize, width, density, direction, measurer) {
            var low = 0
            var high = ((maxSize.value - minSize.value).coerceAtLeast(0f) * 2).roundToInt()
            var best = 0
            while (low <= high) {
                val mid = (low + high) / 2
                val candidate = (minSize.value + mid * 0.5f).coerceAtMost(maxSize.value).sp
                val result = measurer.measure(text, style.copy(fontSize = candidate),
                    softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = width))
                if (result.hasVisualOverflow) high = mid - 1 else { best = mid; low = mid + 1 }
            }
            (minSize.value + best * 0.5f).coerceAtMost(maxSize.value).sp
        }
        Text(text = text, style = style.copy(fontSize = size), maxLines = 1, softWrap = false)
    }
}
