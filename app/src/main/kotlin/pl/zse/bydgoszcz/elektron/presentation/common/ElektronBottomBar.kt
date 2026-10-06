package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.outlined.DirectionsTransit
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
import androidx.compose.ui.unit.sp
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(ElektronRoutes.DASHBOARD, "Strona główna", Icons.Outlined.Home, Icons.Filled.Home),
    BottomDestination(ElektronRoutes.TIMETABLE, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomDestination(ElektronRoutes.SUBSTITUTIONS, "Zastępstwa", Icons.Outlined.EventBusy, Icons.Filled.EventBusy),
    BottomDestination(ElektronRoutes.ANNOUNCEMENTS, "Ogłoszenia", Icons.Outlined.Campaign, Icons.Filled.Campaign),
    BottomDestination(ElektronRoutes.SETTINGS, "Ustawienia", Icons.Outlined.Settings, Icons.Filled.Settings)
)

fun bottomDestinationsForTransit(enabled: Boolean): List<BottomDestination> =
    if (!enabled) bottomDestinations else bottomDestinations.toMutableList().apply {
        add(3, BottomDestination(ElektronRoutes.TRANSIT, "Odjazdy", Icons.Outlined.DirectionsTransit, Icons.Filled.DirectionsTransit))
    }

/** Hiding an icon keeps the page available through swipes and deep links. */
fun visibleBottomDestinations(hidden: Set<String>, showTransit: Boolean): List<BottomDestination> =
    bottomDestinationsForTransit(true).filter { destination ->
        destination.route == ElektronRoutes.SETTINGS ||
            (destination.route !in hidden && (destination.route != ElektronRoutes.TRANSIT || showTransit))
    }

/**
 * Pasek zakładek w stylu iOS: cienka linia nad paskiem, aktywna zakładka wyróżniona
 * kolorem akcentu i wypełnioną ikoną (bez materiałowej "pigułki" pod ikoną).
 */
@Composable
fun ElektronBottomBar(currentRoute: String?, onNavigate: (String) -> Unit, destinations: List<BottomDestination> = bottomDestinations) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            destinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(dest.route) },
                    icon = {
                        Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) // etykieta niżej — bez dublowania w TalkBack
                    },
                    label = {
                        // Rozmiar dopasowany do miejsca: przy większej czcionce systemowej etykiety
                        // były ucinane ("Zastępstw", "Ogłoszeni").
                        FitText(if (dest.route == ElektronRoutes.DASHBOARD) "Start" else dest.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = (-0.2).sp))
                    },
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
