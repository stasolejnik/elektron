package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.domain.model.AccentColor
import pl.zse.bydgoszcz.elektron.domain.model.AccentMath
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting
import pl.zse.bydgoszcz.elektron.presentation.common.theme.AccentTones
import pl.zse.bydgoszcz.elektron.presentation.common.theme.Accents

private val ACCENT_LABELS = mapOf(
    AccentColor.BLUE to "Niebieski",
    AccentColor.PURPLE to "Fioletowy",
    AccentColor.PINK to "Różowy",
    AccentColor.RED to "Czerwony",
    AccentColor.GREEN to "Zielony",
    AccentColor.CUSTOM to "Własny"
)

/** Koło barw do kółka "własny kolor" i paska odcienia. */
private val RAINBOW = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(AccentMath.fromHsl(it, 0.85f, 0.55f)) }

/**
 * Kółka palet akcentu pod Auto/Jasny/Ciemny: pięć popularnych kolorów w kolejności koła barw
 * i na szóstym miejscu własny kolor (kółko z plusem; po wybraniu - wybrany kolor, ponowne
 * stuknięcie otwiera próbnik).
 */
@Composable
internal fun AccentPicker(current: AccentSetting, onChange: (AccentSetting) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kolor akcentu", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(ACCENT_LABELS[current.color].orEmpty(), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AccentColor.entries.forEach { accent ->
                val selected = accent == current.color
                val isCustom = accent == AccentColor.CUSTOM
                val swatch = Accents.swatch(accent, current.custom)
                // Własny kolor przed wybraniem: spokojne kółko z obwódką i plusem (bez tęczy -
                // nie odciąga uwagi od palet); po wybraniu wygląda jak pozostałe.
                val neutral = isCustom && !selected
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(if (neutral) MaterialTheme.colorScheme.surfaceContainerHighest else swatch)
                        .then(if (neutral) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (isCustom) editing = true else onChange(current.copy(color = accent))
                        }
                        .semantics {
                            contentDescription = if (isCustom && selected) "Własny kolor, stuknij, aby zmienić"
                                else ACCENT_LABELS[accent].orEmpty()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        neutral -> Icon(Icons.Filled.Add, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        selected -> Icon(Icons.Filled.Check, contentDescription = null,
                            tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (editing) {
        CustomAccentDialog(
            initial = current.custom,
            onSave = { onChange(AccentSetting(AccentColor.CUSTOM, it)); editing = false },
            onDismiss = { editing = false }
        )
    }
}

/**
 * Próbnik własnego koloru: odcień i nasycenie (jasność dobiera aplikacja - kolor zawsze jest
 * czytelny w obu motywach), z podglądem na przykładowej lekcji i przycisku.
 */
@Composable
private fun CustomAccentDialog(initial: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { AccentMath.toHsl(initial) }
    var hue by rememberSaveable { mutableFloatStateOf(start.h) }
    var sat by rememberSaveable { mutableFloatStateOf(start.s.coerceIn(0f, 1f)) }
    val seed = AccentMath.fromHsl(hue, sat, 0.5f)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val setting = AccentSetting(AccentColor.CUSTOM, seed)
    val tones = if (dark) Accents.dark(setting) else Accents.light(setting)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Własny kolor") },
        text = {
            Column {
                Preview(tones)
                Spacer(Modifier.height(20.dp))
                Text("Odcień", style = MaterialTheme.typography.labelLarge)
                GradientSlider(
                    value = hue / 360f,
                    onValueChange = { hue = it * 360f },
                    brush = Brush.horizontalGradient(RAINBOW),
                    thumb = Color(AccentMath.fromHsl(hue, 0.85f, 0.55f)),
                    label = "Odcień"
                )
                Spacer(Modifier.height(8.dp))
                Text("Nasycenie", style = MaterialTheme.typography.labelLarge)
                GradientSlider(
                    value = sat,
                    onValueChange = { sat = it },
                    brush = Brush.horizontalGradient(listOf(
                        Color(AccentMath.fromHsl(hue, 0f, 0.55f)),
                        Color(AccentMath.fromHsl(hue, 1f, 0.5f))
                    )),
                    thumb = Color(seed),
                    label = "Nasycenie"
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(seed) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/** Podgląd: karta lekcji w stylu planu (tło wyróżnienia) i przycisk w kolorze akcentu. */
@Composable
private fun Preview(tones: AccentTones) {
    val primary by animateColorAsState(tones.primary, label = "primary")
    val container by animateColorAsState(tones.container, label = "container")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(container)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Następna lekcja", style = MaterialTheme.typography.titleSmall, color = tones.onContainer)
                Text("09:50–10:35 · s. 105", style = MaterialTheme.typography.bodySmall,
                    color = tones.onContainer.copy(alpha = 0.75f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(primary).padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text("Przycisk", style = MaterialTheme.typography.labelLarge, color = tones.onPrimary) }
            Spacer(Modifier.width(14.dp))
            Text("Link i ikony", style = MaterialTheme.typography.bodyMedium, color = primary)
        }
    }
}

/** Suwak z gradientem jako torem (odcień, nasycenie) i okrągłym uchwytem w wybranym kolorze. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(value: Float, onValueChange: (Float) -> Unit, brush: Brush, thumb: Color, label: String) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
        thumb = {
            Box(
                Modifier.size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                    .background(Color.White).padding(4.dp).clip(CircleShape).background(thumb)
            )
        },
        track = {
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(brush).border(1.dp, outline, RoundedCornerShape(6.dp))
            )
        }
    )
}
