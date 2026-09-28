package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Jednoliniowy tekst, który zmniejsza czcionkę, aż zmieści się w całości (od [maxSize] do
 * [minSize]) — zamiast ucinać słowo. Przed dopasowaniem tekst nie jest rysowany, więc nie
 * widać "przeskakiwania" rozmiaru.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = 12.sp,
    minSize: TextUnit = 8.sp
) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    var ready by remember(text, maxSize) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = size),
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size.value > minSize.value) {
                size = (size.value - 0.5f).sp
            } else {
                ready = true
            }
        }
    )
}
