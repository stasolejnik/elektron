package pl.zse.bydgoszcz.elektron.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.zse.bydgoszcz.elektron.presentation.announcements.AnnouncementsScreen
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronBottomBar
import pl.zse.bydgoszcz.elektron.presentation.dashboard.DashboardScreen
import pl.zse.bydgoszcz.elektron.presentation.groups.GroupsScreen
import pl.zse.bydgoszcz.elektron.presentation.settings.SettingsScreen
import pl.zse.bydgoszcz.elektron.presentation.substitutions.SubstitutionsScreen
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableScreen

// Przełączanie zakładek jak w iOS: szybkie przenikanie z minimalnym "wyłonieniem",
// bez przesuwania ekranów w bok (zakładki to równorzędne sekcje, nie kolejne kroki).
private const val FADE_MS = 200
private val tabEnter: EnterTransition =
    fadeIn(tween(FADE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(FADE_MS, easing = FastOutSlowInEasing), initialScale = 0.985f)
private val tabExit: ExitTransition = fadeOut(tween(FADE_MS / 2))

@Composable
fun ElektronNavHost(
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    // Wspólna nawigacja do zakładek: dolny pasek, deep linki z powiadomień i skrótów.
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(ElektronRoutes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(deepLink) {
        deepLink?.let { link ->
            val route = when (link) {
                "timetable" -> ElektronRoutes.TIMETABLE
                "substitutions" -> ElektronRoutes.SUBSTITUTIONS
                "announcements" -> ElektronRoutes.ANNOUNCEMENTS
                "settings" -> ElektronRoutes.SETTINGS
                else -> null
            }
            if (route != null) runCatching { navigateToTab(route) }
            onDeepLinkConsumed()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = { ElektronBottomBar(currentRoute, navigateToTab) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ElektronRoutes.DASHBOARD,
            // consumeWindowInsets: ekrany mają własne Scaffoldy — bez tego górne wcięcie
            // (pasek stanu) liczyłoby się podwójnie.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { tabEnter },
            exitTransition = { tabExit },
            popEnterTransition = { tabEnter },
            popExitTransition = { tabExit }
        ) {
            composable(ElektronRoutes.DASHBOARD) { DashboardScreen() }
            composable(ElektronRoutes.TIMETABLE) { TimetableScreen() }
            composable(ElektronRoutes.SUBSTITUTIONS) { SubstitutionsScreen() }
            composable(ElektronRoutes.ANNOUNCEMENTS) { AnnouncementsScreen() }
            composable(ElektronRoutes.SETTINGS) {
                SettingsScreen(onOpenGroups = {
                    navController.navigate(ElektronRoutes.GROUPS) { launchSingleTop = true }
                })
            }
            composable(ElektronRoutes.GROUPS) {
                GroupsScreen(asSetupStep = false, onClose = { navController.popBackStack() })
            }
        }
    }
}
