package pl.zse.bydgoszcz.elektron.presentation.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionColor
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionColors

/**
 * Kolor zastępstw (Ustawienia -> Wygląd): kółka kolorów i podgląd przykładowego zastępstwa. Podgląd korzysta
 * z motywu aplikacji, więc po wyborze od razu pokazuje kolor, jaki zastępstwa mają w planie, na stronie głównej,
 * w zakładce Zastępstwa i w widżetach. "Domyślny" - pomarańczowy albo kolor z tapety; na końcu własny kolor
 * (kółko z plusem, próbnik jak przy własnym akcencie).
 */
@Composable
internal fun SubstitutionColorPicker(current: AccentSetting, wallpaperActive: Boolean, onChange: (AccentSetting) -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // "Domyślny" przy kolorach z tapety: kolor zastępstw z tapety (tertiary), inaczej pomarańczowy.
    val defaultSwatch = when {
        wallpaperActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context).tertiary else dynamicLightColorScheme(context).tertiary
        else -> Color((if (dark) SubstitutionColors.ORANGE_DARK else SubstitutionColors.ORANGE_LIGHT).color)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kolor zastępstw", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(if (current.substitution == SubstitutionColor.DEFAULT && wallpaperActive) "Z tapety" else current.substitution.label,
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            SubstitutionColor.entries.forEach { color ->
                val selected = color == current.substitution
                val isCustom = color == SubstitutionColor.CUSTOM
                // Odcień z bieżącego motywu (w ciemnym jaśniejszy) - taki, jaki zastępstwa będą miały w aplikacji.
                val tones = if (dark) SubstitutionColors.dark(color, current.substitutionCustom)
                    else SubstitutionColors.light(color, current.substitutionCustom)
                val swatch = tones?.let { Color(it.color) } ?: defaultSwatch
                // Własny kolor przed wybraniem: spokojne kółko z obwódką i plusem, jak przy akcencie.
                val neutral = isCustom && !selected
                val onSwatch = if (swatch.luminance() > 0.5f) Color.Black else Color.White
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (neutral) MaterialTheme.colorScheme.surfaceContainerHighest else swatch)
                        .then(if (neutral) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (isCustom) editing = true else onChange(current.copy(substitution = color))
                        }
                        .semantics {
                            contentDescription = if (isCustom && selected) "Własny kolor zastępstw, dotknij, aby zmienić"
                                else "${color.label} kolor zastępstw"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        neutral -> Icon(Icons.Filled.Add, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = onSwatch, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        val colors = MaterialTheme.colorScheme
        SubstitutionPreview(colors.tertiary, colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.padding(top = 14.dp))
    }
    if (editing) {
        CustomAccentDialog(
            initial = current.substitutionCustom,
            title = "Własny kolor zastępstw",
            preview = { tones -> SubstitutionPreview(tones.primary, tones.container, tones.onContainer) },
            onSave = { onChange(current.copy(substitution = SubstitutionColor.CUSTOM, substitutionCustom = it)); editing = false },
            onDismiss = { editing = false }
        )
    }
}

/** Przykładowe zastępstwo jak w zakładce Zastępstwa, w podanym kolorze zastępstw. */
@Composable
private fun SubstitutionPreview(color: Color, container: Color, onContainer: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Podgląd zastępstwa" },
        shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = container) {
                Text("3", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = onContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("A. Nowak", style = MaterialTheme.typography.titleMedium)
                Text("s. 105", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text("Za: matematyka · J. Kowalska", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text("za nieobecny oddział", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
            }
        }
    }
}
