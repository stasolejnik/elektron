package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import java.time.LocalDateTime
import pl.zse.bydgoszcz.elektron.presentation.common.rememberNow
import pl.zse.bydgoszcz.elektron.domain.model.LessonClock
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.BorderStroke
import pl.zse.bydgoszcz.elektron.presentation.common.DelayedLoading
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import pl.zse.bydgoszcz.elektron.domain.model.JointGroups
import pl.zse.bydgoszcz.elektron.presentation.common.findActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import pl.zse.bydgoszcz.elektron.presentation.common.LocalPersonalization
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import pl.zse.bydgoszcz.elektron.presentation.common.pressable
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import pl.zse.bydgoszcz.elektron.presentation.common.currentDateFlow
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import androidx.compose.foundation.layout.widthIn
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionDisplay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    /** Plan jest aktualnie wybraną zakładką (zmiana -> dzień startowy, patrz niżej). */
    isShown: Boolean = true,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Zapamiętany tryb (Dzień/Tydzień) znany od pierwszej klatki - bez mignięcia widoku dnia.
    val savedWeekView = LocalPersonalization.current.look.weekView
    remember { viewModel.initSavedMode(savedWeekView) }
    val mode by viewModel.viewMode.collectAsStateWithLifecycle()
    val plLocale = Locale("pl", "PL")
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM", plLocale)
    val scope = rememberCoroutineScope()
    // Szczegóły lekcji po dotknięciu (widok tygodnia i dnia).
    var detailsLesson by remember { mutableStateOf<Lesson?>(null) }
    detailsLesson?.let { LessonDetailsSheet(it) { detailsLesson = null } }

    // Zegar dla plakietek "Trwa teraz" / "Za X min" - aktualny od razu po powrocie do aplikacji.
    val clock = rememberNow().value.toLocalTime()

    // Pager stron (dzień albo tydzień). Nowy stan pagera przy zmianie trybu — strona
    // startowa odpowiada bieżącej dacie, więc przełączenie Dzień/Tydzień nie gubi miejsca.
    val base = viewModel.baseDate
    val pagerState = key(mode) {
        val initial = when (mode) {
            TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, viewModel.currentAnchor)
            TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, viewModel.currentAnchor)
        }
        rememberPagerState(initialPage = initial) { TimetableViewModel.PAGE_COUNT }
    }
    LaunchedEffect(pagerState, mode) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            when (mode) {
                TimetableViewModel.ViewMode.DAY -> viewModel.onDaySettled(TimetableViewModel.dayForPage(base, page))
                TimetableViewModel.ViewMode.WEEK -> viewModel.onWeekSettled(TimetableViewModel.mondayForPage(base, page))
            }
        }
    }
    // Nagłówek podąża za stroną w trakcie przesuwania (targetPage), nie dopiero po zatrzymaniu.
    val shownPage = pagerState.targetPage
    val headerText = when (mode) {
        TimetableViewModel.ViewMode.DAY -> {
            val d = TimetableViewModel.dayForPage(base, shownPage)
            val dn = d.dayOfWeek.getDisplayName(TextStyle.FULL, plLocale).replaceFirstChar { it.titlecase(plLocale) }
            "$dn • ${d.format(dateFmt)}"
        }
        TimetableViewModel.ViewMode.WEEK -> {
            val monday = TimetableViewModel.mondayForPage(base, shownPage)
            "Tydzień ${monday.format(dateFmt)}–${monday.plusDays(4).format(dateFmt)}"
        }
    }
    // Bieżąca data (zmienia się po północy). Aplikacja potrafi wisieć w pamięci całymi dniami,
    // a pager pamięta ostatnio oglądaną stronę — po zmianie daty wracamy do dziś.
    val today by remember { currentDateFlow() }.collectAsStateWithLifecycle(initialValue = LocalDate.now())
    val todayPage = when (mode) {
        TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, today)
        TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, today)
    }
    // Powrót do aplikacji po dłuższej nieobecności - dzień startowy od nowa (ViewModel).
    // Obrót ekranu to nie wyjście z aplikacji.
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppStart()
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) viewModel.onLeftApp()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Dzień startowy liczy ViewModel (patrz OpeningJump). Pager tworzony później startuje już
    // od właściwego dnia; istniejący pager przewija się - niewidocznie, gdy plan nie jest na
    // ekranie, a na ekranie przez krótkie przejście zamiast skoku.
    val jump by viewModel.openingJump.collectAsStateWithLifecycle()
    val fade = remember { Animatable(1f) }
    val shown by rememberUpdatedState(isShown)
    LaunchedEffect(jump?.id, pagerState) {
        val j = jump ?: return@LaunchedEffect
        if (j.id <= viewModel.handledJumpId) return@LaunchedEffect
        val target = when (mode) {
            TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, j.day)
            TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, j.day)
        }
        if (target != pagerState.currentPage) {
            if (shown) {
                fade.snapTo(0f)
                pagerState.scrollToPage(target)
                fade.animateTo(1f, tween(180))
            } else {
                pagerState.scrollToPage(target)
            }
        }
        viewModel.markJumpHandled(j.id)
    }
    var lastSeenDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    LaunchedEffect(today) {
        if (today.toEpochDay() != lastSeenDay) {
            lastSeenDay = today.toEpochDay()
            pagerState.scrollToPage(todayPage)
        }
    }

    // Komunikat po nieudanym odświeżeniu (brak internetu, strona szkoły nie odpowiada).
    val snackbar = remember { SnackbarHostState() }
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            viewModel.consumeRefreshMessage()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    // Dotknięcie tytułu wraca do dziś.
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pressable { scope.launch { pagerState.animateScrollToPage(todayPage) } }
                    )
                },
                actions = {
                    val dayMode = mode == TimetableViewModel.ViewMode.DAY
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = if (dayMode) "Poprzedni dzień" else "Poprzedni tydzień") }
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (dayMode) "Następny dzień" else "Następny tydzień") }
                    // Odświeżanie przyciskiem (zawsze skrajnie po prawej): przeciągnięcie w dół
                    // na górze listy przewija do poprzedniego dnia, więc pull-to-refresh tu nie pasuje.
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.selectedClassId == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Wybierz klasę w Ustawieniach", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val edgePaging = rememberEdgePagingConnection(pagerState)
        val syncingWeek by viewModel.syncingWeek.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    SegmentedButton(
                        selected = mode == TimetableViewModel.ViewMode.DAY,
                        onClick = { viewModel.setMode(TimetableViewModel.ViewMode.DAY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) { Text("Dzień") }
                    SegmentedButton(
                        selected = mode == TimetableViewModel.ViewMode.WEEK,
                        onClick = { viewModel.setMode(TimetableViewModel.ViewMode.WEEK) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) { Text("Tydzień") }
                }

                // Dni/tygodnie jako strony pionowe: przewijasz listę lekcji, a po dojechaniu
                // do końca dalsze przeciąganie przynosi następny dzień (na początku - poprzedni).
                // Strony obok są wyrenderowane z wyprzedzeniem.
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false,
                    key = { it },
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
                ) { page ->
                    val pageModifier = Modifier.fillMaxSize().nestedScroll(edgePaging)
                    when (mode) {
                        TimetableViewModel.ViewMode.DAY -> {
                            val date = TimetableViewModel.dayForPage(base, page)
                            val week by remember(date) { viewModel.week(date.with(java.time.DayOfWeek.MONDAY)) }
                                .collectAsStateWithLifecycle()
                            val monday = date.with(java.time.DayOfWeek.MONDAY)
                            val loading = week == null || syncingWeek == monday || state.isRefreshing
                            val pastWeek = TimetableViewModel.isPastWeek(monday, today)
                            DayPage(date, week, clock, loading, pastWeek, pageModifier) { detailsLesson = it }
                        }
                        TimetableViewModel.ViewMode.WEEK -> {
                            val monday = TimetableViewModel.mondayForPage(base, page)
                            val week by remember(monday) { viewModel.week(monday) }.collectAsStateWithLifecycle()
                            val loading = week == null || syncingWeek == monday || state.isRefreshing
                            val pastWeek = TimetableViewModel.isPastWeek(monday, today)
                            WeekPage(week, loading, pastWeek, pageModifier) { detailsLesson = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    week: List<TimetableViewModel.DayColumn>?,
    clock: LocalTime,
    loading: Boolean,
    pastWeek: Boolean,
    modifier: Modifier,
    onLessonClick: (Lesson) -> Unit
) {
    val lessons = week?.firstOrNull { it.date == date }?.lessons.orEmpty()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (lessons.isEmpty()) {
            // Plan tygodnia jest w bazie, a ten dzień po prostu nie ma lekcji — to nie ładowanie.
            val weekHasLessons = week?.any { it.lessons.isNotEmpty() } == true
            item { if (weekHasLessons) NoLessonsCard() else EmptyOrLoading(loading, pastWeek) }
        } else {
            val isToday = date == LocalDate.now()
            // Ta sama reguła co strona główna i widżety (LessonClock): "Za X min" tylko przy
            // następnej lekcji w przerwie albo w ostatnich 30 min przed nią. Dawniej każda
            // następna lekcja po wcześniejszej dostawała odliczanie - w trakcie lekcji 3. przy
            // lekcji 4. widniało np. "Za 52 min".
            val status = if (isToday) LessonClock.status(lessons, LocalDateTime.of(date, clock)) else null
            items(lessons, key = { it.id }) { lesson ->
                val badge = when (status) {
                    is LessonClock.During -> if (status.lesson.id == lesson.id) "Trwa teraz" else null
                    is LessonClock.Break -> if (status.next.id == lesson.id) "Za ${status.minutesUntil} min" else null
                    is LessonClock.Before -> if (status.next.id == lesson.id) status.minutesUntil?.let { "Za $it min" } else null
                    else -> null
                }
                LessonRow(lesson, badge, onClick = { onLessonClick(lesson) })
            }
        }
    }
}

@Composable
private fun WeekPage(
    week: List<TimetableViewModel.DayColumn>?,
    loading: Boolean,
    pastWeek: Boolean,
    modifier: Modifier,
    onLessonClick: (Lesson) -> Unit
) {
    if (week == null || week.all { it.lessons.isEmpty() }) {
        Box(modifier.padding(12.dp)) { EmptyOrLoading(loading, pastWeek) }
        return
    }
    // Siatka jak w eduVulcan: godziny po lewej, 5 dni, same nazwy przedmiotów.
    WeekGrid(week, modifier, onLessonClick)
}


/** Mała plakietka grupy przy nazwie przedmiotu, np. "Grupa 2". */
@Composable
private fun GroupBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun NoLessonsCard() {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text("Brak lekcji w tym dniu",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyOrLoading(loading: Boolean, pastWeek: Boolean) {
    // Ładowanie: karta z kółkiem dopiero po chwili - plan z bazy przychodzi zwykle od razu,
    // a natychmiastowa karta "Ładuję plan…" tylko migała przed lekcjami.
    if (loading) {
        DelayedLoading { MessageCard(loading = true, text = "Ładuję plan…") }
    } else if (pastWeek) {
        // Miniony tydzień, którego nie ma w bazie: strona szkoły publikuje tylko aktualny plan,
        // więc odświeżanie nic tu nie da (dawniej wpisywało bieżący plan jako plan sprzed tygodni).
        MessageCard(loading = false, text = "Brak planu z tego tygodnia.",
            hint = "Strona szkoły publikuje tylko aktualny plan. eLektron pamięta minione tygodnie " +
                "(do 8 wstecz) tylko wtedy, gdy zdążył je wcześniej zapisać.")
    } else {
        MessageCard(loading = false, text = "Brak zapisanego planu na ten okres.",
            hint = "Odśwież przyciskiem u góry, gdy będzie internet.")
    }
}

@Composable
private fun MessageCard(loading: Boolean, text: String, hint: String? = null) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loading) CircularProgressIndicator()
            Text(text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) {
                Text(hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, badge: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val sub = lesson.substitution
    val bg = if (sub != null) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (sub != null) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onSurface
    val originalSubject = lesson.groups.firstOrNull()?.subject
    val personal = LocalPersonalization.current
    val look = personal.look
    // Kolor przedmiotu (Ustawienia -> Przedmioty): pasek przy lewej krawędzi karty i numer lekcji.
    val subjectColor = if (sub == null) personal.subjectColor(originalSubject) else null

    // Trwająca lekcja (tylko dzisiejsze karty mają zegar): obramowanie, "zostało X min"
    // i pasek postępu - jak na stronie głównej i w widżecie.
    val now = if (lesson.date == LocalDate.now()) rememberNow().value.toLocalTime() else null
    val ongoing = now != null && now >= lesson.timeFrom && now < lesson.timeTo
    val accent = MaterialTheme.colorScheme.primary

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 1.dp)
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = if (ongoing) BorderStroke(1.5.dp, if (sub != null) fg else accent) else null) {
      Column {
        Row(
            Modifier
                .drawBehind {
                    subjectColor?.let { drawRect(it, size = Size(5.dp.toPx(), size.height)) }
                }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${lesson.number}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (sub != null) fg else subjectColor ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                if (sub != null) {
                    // Zastępstwo: najważniejszy jest nauczyciel, który zastępuje (nie sala).
                    Text(SubstitutionDisplay.headline(sub), style = MaterialTheme.typography.titleMedium, color = fg,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SubstitutionDisplay.place(sub)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = fg)
                    }
                    val forLine = buildString {
                        append("Za: ")
                        personal.subjectName(originalSubject)?.let { append("$it · ") }
                        append(sub.originalTeacher)
                    }
                    Text(forLine, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
                    // Uwagi ze strony zastępstw ("za ostatnią lekcję", "historia").
                    SubstitutionDisplay.notes(sub)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = fg,
                            fontWeight = FontWeight.Medium)
                    }
                } else {
                    // Każda grupa: pogrubiona nazwa przedmiotu (bez sufiksu grupy z Optivum)
                    // + plakietka grupy, pod spodem sala i nauczyciel szarą czcionką.
                    lesson.groups.forEachIndexed { i, g ->
                        if (i > 0) Spacer(Modifier.height(3.dp))
                        val parsed = LessonGroups.parse(g.subject)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(personal.subjectName(g.subject) ?: "Lekcja",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            parsed?.label?.let { GroupBadge(LessonGroups.displayLabel(it)) }
                        }
                        // Zajęcia łączone ("wf-j2 #1AF") nie mają na stronie nauczyciela -
                        // zamiast niego "łączona z 1A".
                        val teacherOrJoint = (g.teacherFullName ?: g.teacherCode)
                            ?: JointGroups.describe(g.classRef, lesson.className)
                        val meta = listOfNotNull(
                            g.room?.takeIf { look.showRoom }?.let { "s. $it" },
                            teacherOrJoint?.takeIf { look.showTeacher }
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    lesson.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                badge?.let {
                    Spacer(Modifier.padding(vertical = 2.dp))
                    Text(it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            // Szerokość według treści (min. 52 dp). Dawniej sztywne 52 dp: przy większej czcionce
            // systemowej godziny były ucinane ("09:5…").
            Column(
                Modifier.widthIn(min = 52.dp).padding(start = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                val timeColor = if (sub != null) fg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                // Obie godziny w jednej linii, np. "08:00–08:45".
                Text("${lesson.timeFrom}–${lesson.timeTo}",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false,
                    color = timeColor)
                if (ongoing && now != null) {
                    Text("zostało ${LessonClock.minutesCeil(now, lesson.timeTo)} min",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, softWrap = false,
                        color = if (sub != null) fg else accent)
                }
            }
        }
        if (ongoing && now != null) {
            LinearProgressIndicator(
                progress = { LessonClock.progress(lesson.timeFrom, lesson.timeTo, now) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = if (sub != null) fg else accent,
                trackColor = Color.Transparent,
                drawStopIndicator = {}
            )
        }
      }
    }
}
