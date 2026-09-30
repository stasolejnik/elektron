package pl.zse.bydgoszcz.elektron.presentation.navigation

import pl.zse.bydgoszcz.elektron.presentation.subjects.SubjectsScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.presentation.announcements.AnnouncementsScreen
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronBottomBar
import pl.zse.bydgoszcz.elektron.presentation.common.bottomDestinations
import pl.zse.bydgoszcz.elektron.presentation.dashboard.DashboardScreen
import pl.zse.bydgoszcz.elektron.presentation.groups.GroupsScreen
import pl.zse.bydgoszcz.elektron.presentation.settings.SettingsScreen
import pl.zse.bydgoszcz.elektron.presentation.substitutions.SubstitutionsScreen
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableScreen
import kotlin.math.abs

/**
 * Główny układ aplikacji: sekcje jako strony poziomego pagera — przesunięcie palcem
 * w lewo/prawo przełącza Stronę główną, Plan, Zastępstwa, Ogłoszenia i Ustawienia.
 * Dolny pasek podąża za przesunięciem. Sąsiednie sekcje są wyrenderowane z wyprzedzeniem.
 *
 * "Twoje grupy" (z Ustawień) to wysuwana warstwa nad sekcjami, zamykana przyciskiem wstecz.
 */
@Composable
fun ElektronNavHost(
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    startRoute: String = ElektronRoutes.DASHBOARD
) {
    val scope = rememberCoroutineScope()
    // Strona startowa tylko przy pierwszym utworzeniu (stan pagera jest zapisywany, więc obrót
    // ekranu czy powrót z tła nie przeskakują z powrotem).
    // Deep link (widżet, powiadomienie, skrót) ma pierwszeństwo przed inteligentnym startem —
    // inaczej zimny start z widżetu w godzinach lekcji pokazywał Plan i przeskakiwał dalej.
    val initialRoute = routeForDeepLink(deepLink) ?: startRoute
    val startPage = bottomDestinations.indexOfFirst { it.route == initialRoute }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { bottomDestinations.size }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var showSubjects by rememberSaveable { mutableStateOf(false) }
    val overlayOpen = showGroups || showSubjects

    // Zmiana sekcji zawsze z animacją. Do dalszej sekcji: najpierw niewidocznie strona obok
    // celu, potem animacja do celu — płynne przesunięcie bez przelatywania przez pośrednie.
    val animateTo: suspend (Int) -> Unit = { index ->
        val current = pagerState.currentPage
        if (abs(index - current) > 1) pagerState.scrollToPage(if (index > current) index - 1 else index + 1)
        pagerState.animateScrollToPage(
            index,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    val goTo: (String) -> Unit = { route ->
        val index = bottomDestinations.indexOfFirst { it.route == route }
        if (index >= 0) scope.launch { animateTo(index) }
    }

    LaunchedEffect(deepLink) {
        deepLink?.let { link ->
            val route = routeForDeepLink(link)
            if (route != null) {
                showGroups = false
                showSubjects = false
                goTo(route)
            }
            onDeepLinkConsumed()
        }
    }

    // Wstecz: najpierw zamyka "Twoje grupy"/"Przedmioty", potem wraca na stronę główną, dopiero potem wychodzi.
    BackHandler(enabled = !overlayOpen && pagerState.currentPage != 0) {
        scope.launch { animateTo(0) }
    }
    BackHandler(enabled = overlayOpen) { showGroups = false; showSubjects = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                ElektronBottomBar(bottomDestinations[pagerState.targetPage].route, goTo)
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                // Domyślnie przy spokojnym ruchu trzeba było przeciągnąć POŁOWĘ ekranu (próg 0.5),
                // inaczej strona wracała. 20% + szybsze domknięcie = łatwe przełączanie sekcji.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.2f,
                    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ),
                key = { bottomDestinations[it].route },
                // consumeWindowInsets: sekcje mają własne Scaffoldy — bez tego dolne wcięcie
                // (pasek nawigacji) liczyłoby się podwójnie.
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            ) { page ->
                when (bottomDestinations[page].route) {
                    ElektronRoutes.DASHBOARD -> DashboardScreen(
                        onOpenTimetable = { goTo(ElektronRoutes.TIMETABLE) },
                        onOpenSubstitutions = { goTo(ElektronRoutes.SUBSTITUTIONS) }
                    )
                    ElektronRoutes.TIMETABLE -> TimetableScreen(isShown = pagerState.settledPage == page)
                    ElektronRoutes.SUBSTITUTIONS -> SubstitutionsScreen()
                    ElektronRoutes.ANNOUNCEMENTS -> AnnouncementsScreen()
                    ElektronRoutes.SETTINGS -> SettingsScreen(
                        onOpenGroups = { showGroups = true },
                        onOpenSubjects = { showSubjects = true }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showGroups,
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
        ) {
            // Warstwa przechwytuje dotyk na całej powierzchni — inaczej przesunięcie palcem
            // po jej pustych miejscach (np. pasku tytułu) przełączało niewidoczną sekcję pod spodem.
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }) {
                GroupsScreen(asSetupStep = false, onClose = { showGroups = false })
            }
        }

        AnimatedVisibility(
            visible = showSubjects,
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
        ) {
            // Jak wyżej - warstwa przechwytuje dotyk.
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }) {
                SubjectsScreen(onClose = { showSubjects = false })
            }
        }
    }
}

/** Deep link ("dashboard", "timetable", ...) -> trasa sekcji; null dla nieznanych/pustych. */
private fun routeForDeepLink(link: String?): String? = when (link) {
    "dashboard" -> ElektronRoutes.DASHBOARD
    "timetable" -> ElektronRoutes.TIMETABLE
    "substitutions" -> ElektronRoutes.SUBSTITUTIONS
    "announcements" -> ElektronRoutes.ANNOUNCEMENTS
    "settings" -> ElektronRoutes.SETTINGS
    else -> null
}
