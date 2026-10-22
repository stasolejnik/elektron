package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Informacja szkoły ze strony zastępstw (np. o oddawaniu krwi). Zwinięta do [collapsedLines] linii,
 * "Pokaż więcej / Zwiń" tylko wtedy, gdy tekst się nie mieści. Zwykły tekst do zaznaczenia (linki
 * i numery bez specjalnej obsługi). TalkBack czyta całą treść także w zwiniętej karcie - skrót jest
 * tylko wizualny. Długie słowa bez spacji łamane są w środku, zamiast wychodzić poza kartę.
 * Obrazki ze strony szkoły (np. skrócony plan zajęć) - po rozwinięciu albo od razu, gdy nie ma tekstu;
 * dotknięcie otwiera obrazek w pełnym rozmiarze.
 * [key] - przy zmianie (inny dzień albo tekst) karta zaczyna zwinięta.
 */
@Composable
fun SchoolNoticeCard(notice: pl.zse.bydgoszcz.elektron.domain.model.SchoolNotice, collapsedLines: Int, key: String,
    modifier: Modifier = Modifier, compact: Boolean = false, title: String = "Informacja szkoły") {
    val text = notice.text
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var overflows by rememberSaveable(key) { mutableStateOf(false) }
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    ElektronCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        // Ikona w wierszu tytułu, treść na całą szerokość karty (dawniej wcięta pod ikoną i większa).
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = if (compact) 12.dp else 14.dp, bottom = if (overflows || expanded || (notice.images.isNotEmpty() && text.isNotBlank())) 2.dp else if (compact) 12.dp else 14.dp)
                .animateContentSize(tween(durationMillis = 180, easing = FastOutSlowInEasing))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = onColor, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = onColor, modifier = Modifier.semantics { heading() })
            }
            if (text.isNotBlank()) SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                    color = onColor,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (notice.images.isNotEmpty() && (expanded || text.isBlank())) {
                notice.images.forEach { url ->
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = "Obrazek z informacji szkoły. Otwórz w pełnym rozmiarze",
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { SafeUrls.open(context, url) }
                    )
                }
            }
            val more = overflows || (notice.images.isNotEmpty() && text.isNotBlank())
            if (more || expanded) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Zwiń" else "Pokaż więcej")
                }
            }
        }
    }
}
