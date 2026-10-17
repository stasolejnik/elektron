package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * [key] - przy zmianie (inny dzień albo tekst) karta zaczyna zwinięta.
 */
@Composable
fun SchoolNoticeCard(text: String, collapsedLines: Int, key: String, modifier: Modifier = Modifier, compact: Boolean = false,
    title: String = "Informacja szkoły") {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var overflows by rememberSaveable(key) { mutableStateOf(false) }
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    ElektronCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        // Ikona w wierszu tytułu, treść na całą szerokość karty (dawniej wcięta pod ikoną i większa).
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = if (compact) 12.dp else 14.dp, bottom = if (overflows || expanded) 2.dp else if (compact) 12.dp else 14.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = onColor, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = onColor, modifier = Modifier.semantics { heading() })
            }
            SelectionContainer {
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
            if (overflows || expanded) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Zwiń" else "Pokaż więcej")
                }
            }
        }
    }
}
