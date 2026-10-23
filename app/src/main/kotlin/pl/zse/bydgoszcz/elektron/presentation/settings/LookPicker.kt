package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.domain.model.AccentMath
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting
import pl.zse.bydgoszcz.elektron.domain.model.BaseLook
import pl.zse.bydgoszcz.elektron.domain.model.BaseLooks
import pl.zse.bydgoszcz.elektron.domain.model.LookSetting
import pl.zse.bydgoszcz.elektron.presentation.common.theme.Looks

internal val LOOK_LABELS = mapOf(
    BaseLook.CLASSIC to "Klasyczny",
    BaseLook.GRAPHITE to "Grafit",
    BaseLook.WARM to "Ciepły",
    BaseLook.OCEAN to "Morski",
    BaseLook.FOREST to "Leśny",
    BaseLook.CUSTOM to "Własny"
)

/** Mała próbka w próbniku: tło z kartą lekcji w jasnym i ciemnym motywie. */
@Composable
private fun LookSample(accent: AccentSetting, look: LookSetting) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false, true).forEach { dark ->
            val scheme = remember(accent, look, dark) { Looks.scheme(dark, accent, look) }
            val bg by animateColorAsState(scheme.background, label = "bg")
            val card by animateColorAsState(scheme.surfaceContainerHigh, label = "card")
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(card).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("3", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Lekcja", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
                        Text("s. 105", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Kółka głównego wyglądu (jak kolory akcentu): gotowe wyglądy i na końcu własny. Kółko pokazuje
 * po przekątnej jasną i ciemną wersję. Stuknięcie własnego otwiera próbnik.
 */
@Composable
internal fun LookPicker(current: LookSetting, accent: AccentSetting, wallpaperActive: Boolean = false, onChange: (LookSetting) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kolor tła", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(if (wallpaperActive) "Z tapety" else LOOK_LABELS[current.look].orEmpty(), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaseLook.entries.forEach { look ->
                val selected = !wallpaperActive && look == current.look
                val isCustom = look == BaseLook.CUSTOM
                val neutral = isCustom && !selected
                val swatch = BaseLooks.swatch(current.copy(look = look))
                val outline = MaterialTheme.colorScheme.outline
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .then(if (neutral) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                        .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else outline, CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (isCustom) editing = true else onChange(current.copy(look = look))
                        }
                        .semantics { contentDescription = if (isCustom && selected) "Własny kolor tła, stuknij, aby zmienić" else LOOK_LABELS[look].orEmpty() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!neutral) Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color(swatch.first))
                        val path = Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                        drawPath(path, Color(swatch.second))
                    }
                    when {
                        neutral -> Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        selected -> Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
    if (editing) CustomLookDialog(current, accent, onSave = { onChange(it); editing = false }, onDismiss = { editing = false })
}

/** Próbnik własnego koloru tła: odcień i nasycenie (jak przy kolorze akcentu), z małą próbką jasnego i ciemnego motywu. */
@Composable
private fun CustomLookDialog(initial: LookSetting, accent: AccentSetting, onSave: (LookSetting) -> Unit, onDismiss: () -> Unit) {
    var hue by rememberSaveable { mutableFloatStateOf(initial.hue) }
    var tint by rememberSaveable { mutableFloatStateOf(initial.tint) }
    val setting = LookSetting(BaseLook.CUSTOM, hue, tint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Własny kolor tła") },
        text = {
            Column {
                LookSample(accent, setting)
                Spacer(Modifier.height(18.dp))
                Text("Odcień", style = MaterialTheme.typography.labelLarge)
                GradientSlider(value = hue / 360f, onValueChange = { hue = it * 360f },
                    brush = Brush.horizontalGradient(RAINBOW), thumb = Color(AccentMath.fromHsl(hue, 0.85f, 0.55f)), label = "Odcień")
                Spacer(Modifier.height(8.dp))
                Text("Nasycenie", style = MaterialTheme.typography.labelLarge)
                GradientSlider(value = tint, onValueChange = { tint = it },
                    brush = Brush.horizontalGradient(listOf(Color(AccentMath.fromHsl(hue, 0f, 0.85f)), Color(AccentMath.fromHsl(hue, 0.6f, 0.75f)))),
                    thumb = Color(AccentMath.fromHsl(hue, 0.6f * tint, 0.8f)), label = "Nasycenie")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(setting) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
