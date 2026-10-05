package pl.zse.bydgoszcz.elektron.presentation.navigation

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import pl.zse.bydgoszcz.elektron.presentation.common.bottomDestinationsForTransit
import pl.zse.bydgoszcz.elektron.presentation.transit.TransitViewModel
import pl.zse.bydgoszcz.elektron.presentation.transit.TransitScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.presentation.dashboard.DashboardScreen
import pl.zse.bydgoszcz.elektron.presentation.groups.GroupsScreen
import pl.zse.bydgoszcz.elektron.presentation.settings.SettingsScreen
import pl.zse.bydgoszcz.elektron.presentation.substitutions.SubstitutionsScreen
import pl.zse.bydgoszcz.elektron.presentation.timetable.TimetableScreen
import pl.zse.bydgoszcz.elektron.domain.model.LessonLinks
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget
import java.time.LocalDate
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
    val transitViewModel: TransitViewModel = hiltViewModel()
    val transitSettings by transitViewModel.settings.collectAsStateWithLifecycle()
    // Restore pager indices only after knowing whether the extra tab exists.
    // Otherwise a rotation on Odjazdy can restore the Ogłoszenia page instead.
    if (!transitSettings.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    val bottomDestinations = bottomDestinationsForTransit(transitSettings.preferences.visible)
    // Strona startowa tylko przy pierwszym utworzeniu (stan pagera jest zapisywany, więc obrót
    // ekranu czy powrót z tła nie przeskakują z powrotem).
    // Deep link (widżet, powiadomienie, skrót) ma pierwszeństwo przed inteligentnym startem —
    // inaczej zimny start z widżetu w godzinach lekcji pokazywał Plan i przeskakiwał dalej.
    val initialRoute = routeForDeepLink(deepLink) ?: startRoute
    val startPage = bottomDestinations.indexOfFirst { it.route == initialRoute }.coerceAtLeast(0)
    // Pager may still invoke callbacks from the previous composition while page count changes.
    // Count and keys must read the same current list, with safe access for retained pages.
    val currentDestinations by androidx.compose.runtime.rememberUpdatedState(bottomDestinations)
    val pagerState = rememberPagerState(initialPage = startPage) { currentDestinations.size }
    val previousDestinations = remember { mutableStateOf(bottomDestinations) }
    val preservedRoute = previousDestinations.value.getOrNull(pagerState.currentPage)?.route ?: ElektronRoutes.DASHBOARD
    LaunchedEffect(bottomDestinations) {
        if (previousDestinations.value != bottomDestinations) {
            val index = bottomDestinations.indexOfFirst { it.route == preservedRoute }.coerceAtLeast(0)
            pagerState.scrollToPage(index)
            previousDestinations.value = bottomDestinations
        }
    }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var showSubjects by rememberSaveable { mutableStateOf(false) }
    val overlayOpen = showGroups || showSubjects

    // Zmiana sekcji: do sąsiedniej - przesunięcie; do dalszej - krótkie przenikanie.
    // Dawniej do dalszej sekcji pager skakał najpierw na stronę obok celu i stamtąd się
    // przesuwał, więc przez ułamek sekundy widać było zupełnie inną zakładkę.
    val sectionAlpha = remember { Animatable(1f) }
    val animateTo: suspend (Int) -> Unit = { index ->
        val current = pagerState.currentPage
        if (abs(index - current) > 1) {
            try {
                sectionAlpha.animateTo(0f, tween(90))
                pagerState.scrollToPage(index)
                sectionAlpha.animateTo(1f, tween(160))
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { sectionAlpha.snapTo(1f) }
            }
        } else {
            pagerState.animateScrollToPage(
                index,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    // Dzień startowy planu: liczony przy wyjściu z zakładki planu (w tle - po powrocie plan
    // już stoi na właściwym dniu) i przy wejściu do niej (mógł minąć koniec lekcji).
    val timetableViewModel: TimetableViewModel = hiltViewModel()
    val timetableIndex = bottomDestinations.indexOfFirst { it.route == ElektronRoutes.TIMETABLE }
    LaunchedEffect(pagerState) {
        var last = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != last && (page == timetableIndex || last == timetableIndex)) timetableViewModel.applyOpeningDay()
            last = page
        }
    }

    val goTo: (String) -> Unit = { route ->
        val index = bottomDestinations.indexOfFirst { it.route == route }
        if (index >= 0) scope.launch { animateTo(index) }
    }

    // Dotknięcie zastępstwa (zakładka Zastępstwa, widżet): plan na ten dzień + szczegóły lekcji.
    val openLesson: (LessonTarget) -> Unit = { target ->
        timetableViewModel.openLesson(target)
        goTo(ElektronRoutes.TIMETABLE)
    }

    LaunchedEffect(deepLink) {
        deepLink?.let { link ->
            val route = routeForDeepLink(link)
            if (route != null) {
                showGroups = false
                showSubjects = false
                val lesson = LessonLinks.parseDeepLink(link, LocalDate.now())
                if (lesson != null) openLesson(lesson) else goTo(route)
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
                ElektronBottomBar(bottomDestinations.getOrNull(pagerState.targetPage)?.route, goTo, bottomDestinations)
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
                key = { destinationPageKey(currentDestinations, it) },
                // consumeWindowInsets: sekcje mają własne Scaffoldy — bez tego dolne wcięcie
                // (pasek nawigacji) liczyłoby się podwójnie.
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .graphicsLayer { alpha = sectionAlpha.value }
            ) { page ->
                androidx.compose.runtime.CompositionLocalProvider(
                    pl.zse.bydgoszcz.elektron.presentation.common.LocalScreenVisible provides
                        (pagerState.settledPage == page && !overlayOpen)
                ) {
                when (currentDestinations.getOrNull(page)?.route) {
                    ElektronRoutes.DASHBOARD -> DashboardScreen(
                        onOpenTimetable = { goTo(ElektronRoutes.TIMETABLE) },
                        onOpenSubstitutions = { goTo(ElektronRoutes.SUBSTITUTIONS) }
                    )
                    ElektronRoutes.TIMETABLE -> TimetableScreen(isShown = pagerState.settledPage == page)
                    ElektronRoutes.SUBSTITUTIONS -> SubstitutionsScreen(onOpenLesson = openLesson)
                    ElektronRoutes.TRANSIT -> TransitScreen(transitViewModel) { goTo(ElektronRoutes.SETTINGS) }
                    ElektronRoutes.ANNOUNCEMENTS -> AnnouncementsScreen()
                    ElektronRoutes.SETTINGS -> SettingsScreen(
                        onOpenGroups = { showGroups = true },
                        onOpenSubjects = { showSubjects = true },
                        transitViewModel = transitViewModel
                    )
                }
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

/**
 * Deep link ("dashboard", "timetable", ..., "lesson/<epochDay>/<numer>") -> trasa sekcji;
 * null dla nieznanych/pustych i błędnych linków lekcji.
 */
private fun routeForDeepLink(link: String?): String? = when {
    LessonLinks.isLessonDeepLink(link) ->
        if (LessonLinks.parseDeepLink(link, LocalDate.now()) != null) ElektronRoutes.TIMETABLE else null
    else -> sectionRoute(link)
}

private fun sectionRoute(link: String?): String? = when (link) {
    "dashboard" -> ElektronRoutes.DASHBOARD
    "timetable" -> ElektronRoutes.TIMETABLE
    "substitutions" -> ElektronRoutes.SUBSTITUTIONS
    "announcements" -> ElektronRoutes.ANNOUNCEMENTS
    "settings" -> ElektronRoutes.SETTINGS
    else -> null
}
