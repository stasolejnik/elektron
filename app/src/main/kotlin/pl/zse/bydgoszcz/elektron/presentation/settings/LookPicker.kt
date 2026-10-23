package pl.zse.bydgoszcz.elektron.presentation.settings

import android.os.Build
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
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

/**
 * Podgląd aplikacji w jasnym i ciemnym motywie obok siebie (Ustawienia -> Wygląd i próbnik
 * własnego wyglądu): tło, karta lekcji z wyróżnieniem w kolorze akcentu i dolny pasek.
 */
@Composable
internal fun ThemePreviewPair(accent: AccentSetting, look: LookSetting, dynamic: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallpaper = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val light = remember(accent, look, wallpaper) {
        if (wallpaper) dynamicLightColorScheme(context) else Looks.scheme(false, accent, look)
    }
    val dark = remember(accent, look, wallpaper) {
        if (wallpaper) dynamicDarkColorScheme(context) else Looks.scheme(true, accent, look)
    }
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Podgląd wyglądu w jasnym i ciemnym motywie" },
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniScreen(light, "Jasny", Modifier.weight(1f))
        MiniScreen(dark, "Ciemny", Modifier.weight(1f))
    }
}

/** Miniatura ekranu: nagłówek, karta z trwającą lekcją i następną, dolny pasek z zaznaczoną zakładką. */
@Composable
private fun MiniScreen(scheme: ColorScheme, label: String, modifier: Modifier) {
    val bg by animateColorAsState(scheme.background, label = "bg")
    val card by animateColorAsState(scheme.surfaceContainerHigh, label = "card")
    val primary by animateColorAsState(scheme.primary, label = "primary")
    val container by animateColorAsState(scheme.primaryContainer, label = "container")
    val bar by animateColorAsState(scheme.surfaceContainer, label = "bar")
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(bg).border(1.dp, scheme.outlineVariant, shape)) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text("Dziś", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onBackground)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(card).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(container).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("3", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primary)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Matematyka", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer, maxLines = 1)
                        Text("s. 105", style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("4", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Fizyka", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
                        Text("s. 12", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(bar).padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { i ->
                Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i == 1) primary else scheme.outline))
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
            Text("Wygląd", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
                        .semantics { contentDescription = if (isCustom && selected) "Własny wygląd, stuknij, aby zmienić" else LOOK_LABELS[look].orEmpty() },
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

/** Próbnik własnego wyglądu: odcień i intensywność, z podglądem jasnego i ciemnego motywu. */
@Composable
private fun CustomLookDialog(initial: LookSetting, accent: AccentSetting, onSave: (LookSetting) -> Unit, onDismiss: () -> Unit) {
    var hue by rememberSaveable { mutableFloatStateOf(initial.hue) }
    var tint by rememberSaveable { mutableFloatStateOf(initial.tint) }
    val setting = LookSetting(BaseLook.CUSTOM, hue, tint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Własny wygląd") },
        text = {
            Column {
                ThemePreviewPair(accent, setting)
                Spacer(Modifier.height(18.dp))
                Text("Odcień", style = MaterialTheme.typography.labelLarge)
                GradientSlider(value = hue / 360f, onValueChange = { hue = it * 360f },
                    brush = Brush.horizontalGradient(RAINBOW), thumb = Color(AccentMath.fromHsl(hue, 0.85f, 0.55f)), label = "Odcień")
                Spacer(Modifier.height(8.dp))
                Text("Intensywność", style = MaterialTheme.typography.labelLarge)
                GradientSlider(value = tint, onValueChange = { tint = it },
                    brush = Brush.horizontalGradient(listOf(Color(AccentMath.fromHsl(hue, 0f, 0.85f)), Color(AccentMath.fromHsl(hue, 0.6f, 0.75f)))),
                    thumb = Color(AccentMath.fromHsl(hue, 0.6f * tint, 0.8f)), label = "Intensywność")
                Text("Od neutralnej szarości (w ciemnym motywie czerń) do wyraźnego koloru tła i kart. Kolor akcentu zostaje osobno.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(setting) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
