package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.presentation.common.pressable


// Wspólne wiersze Ustawień: przełącznik, akcja, linia, stan zgody na powiadomienia.

@Composable
internal fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Etykieta wiersza z opcjonalnym opisem pod spodem (zamiast długich stopek pod sekcją). */
@Composable
internal fun RowLabel(label: String, supporting: String?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
        supporting?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(Modifier.toggleable(value = checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange))
            // Obszar dotyku co najmniej 52 dp (dawniej ok. 44 dp: 32 dp przełącznika + marginesy).
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowLabel(label, supporting,
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent,
                // outline, nie outlineVariant: wyłączony przełącznik był prawie niewidoczny na białej karcie.
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = Color.White
            ),
            thumbContent = null
        )
    }
}

/**
 * Wiersz-akcja. Przejście dalej (strzałka) ma zwykły kolor tekstu, czynność - kolor akcentu,
 * nieodwracalna - czerwony; link do strony - ikona otwarcia na końcu.
 */
@Composable
internal fun ActionRow(
    label: String,
    destructive: Boolean = false,
    trailingIcon: Boolean = false,
    trailingChevron: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when {
            !enabled -> colors.onSurfaceVariant
            destructive -> colors.error
            trailingChevron || trailingIcon -> colors.onSurface
            else -> colors.primary
        }
        RowLabel(label, supporting, color, Modifier.weight(1f).padding(end = 8.dp))
        if (trailingIcon) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (trailingChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun SystemNotificationStatus() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!allowed) {
        Text("Android blokuje powiadomienia eLektrona. Zmiana opcji poniżej nie odblokuje ich w systemie.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        // Z zapasowym ekranem aplikacji - bez awarii na systemach bez ustawień powiadomień.
        ActionRow("Otwórz ustawienia powiadomień", trailingIcon = true) { openNotificationSettings(context) }
        RowDivider()
    }
}
