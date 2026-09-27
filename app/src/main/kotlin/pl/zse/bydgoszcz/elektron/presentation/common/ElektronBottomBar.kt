package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(ElektronRoutes.DASHBOARD, "Start", Icons.Outlined.Home, Icons.Filled.Home),
    BottomDestination(ElektronRoutes.TIMETABLE, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomDestination(ElektronRoutes.SUBSTITUTIONS, "Zastępstwa", Icons.Outlined.EventBusy, Icons.Filled.EventBusy),
    BottomDestination(ElektronRoutes.ANNOUNCEMENTS, "Ogłoszenia", Icons.Outlined.Campaign, Icons.Filled.Campaign),
    BottomDestination(ElektronRoutes.SETTINGS, "Ustawienia", Icons.Outlined.Settings, Icons.Filled.Settings)
)

/**
 * Pasek zakładek w stylu iOS: cienka linia nad paskiem, aktywna zakładka wyróżniona
 * kolorem akcentu i wypełnioną ikoną (bez materiałowej "pigułki" pod ikoną).
 */
@Composable
fun ElektronBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            bottomDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(dest.route) },
                    icon = {
                        Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = dest.label)
                    },
                    label = { Text(dest.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
