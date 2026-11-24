package pl.zse.bydgoszcz.elektron.presentation.common

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/**
 * Informacja szkoły ze strony zastępstw (np. o oddawaniu krwi). Zawsze w całości, z obrazkami - bez zwijania.
 * Zwykły tekst do zaznaczenia (linki i numery bez specjalnej obsługi). Długie słowa bez spacji łamane są
 * w środku, zamiast wychodzić poza kartę. Obrazki ze strony szkoły (np. skrócony plan zajęć) pod tekstem;
 * dotknięcie otwiera obrazek w pełnym rozmiarze.
 */
@Composable
fun SchoolNoticeCard(notice: pl.zse.bydgoszcz.elektron.domain.model.SchoolNotice,
    modifier: Modifier = Modifier, compact: Boolean = false, title: String = "Informacja szkoły") {
    val text = notice.displayText
    val context = androidx.compose.ui.platform.LocalContext.current
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    ElektronCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        // Ikona w wierszu tytułu, treść na całą szerokość karty (dawniej wcięta pod ikoną i większa).
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 14.dp)) {
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
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
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
    }
}
