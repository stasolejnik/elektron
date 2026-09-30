package pl.zse.bydgoszcz.elektron.presentation.timetable

import pl.zse.bydgoszcz.elektron.domain.model.JointGroups
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import pl.zse.bydgoszcz.elektron.presentation.common.findActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.runtime.produceState
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
import java.time.Duration
import kotlinx.coroutines.delay
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
    val plLocale = Locale("pl", "PL")
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM", plLocale)
    val scope = rememberCoroutineScope()
    // Zegar dla plakietek "Trwa teraz" / "Za X min".
    val clock by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }

    // Pager stron (dzień albo tydzień). Nowy stan pagera przy zmianie trybu — strona
    // startowa odpowiada bieżącej dacie, więc przełączenie Dzień/Tydzień nie gubi miejsca.
    val base = viewModel.baseDate
    val pagerState = key(state.mode) {
        val initial = when (state.mode) {
            TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, viewModel.currentAnchor)
            TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, viewModel.currentAnchor)
        }
        rememberPagerState(initialPage = initial) { TimetableViewModel.PAGE_COUNT }
    }
    LaunchedEffect(pagerState, state.mode) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            when (state.mode) {
                TimetableViewModel.ViewMode.DAY -> viewModel.onDaySettled(TimetableViewModel.dayForPage(base, page))
                TimetableViewModel.ViewMode.WEEK -> viewModel.onWeekSettled(TimetableViewModel.mondayForPage(base, page))
            }
        }
    }
    // Nagłówek podąża za stroną w trakcie przesuwania (targetPage), nie dopiero po zatrzymaniu.
    val shownPage = pagerState.targetPage
    val headerText = when (state.mode) {
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
    val todayPage = when (state.mode) {
        TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, today)
        TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, today)
    }
    // Przy wejściu do aplikacji: dziś, a po ostatniej dzisiejszej lekcji - następny dzień.
    // Sprawdzane przy każdym powrocie (po >= 3 min poza aplikacją), nie tylko przy pierwszym
    // uruchomieniu - aplikacja zwykle zostaje w pamięci. Obrót ekranu nie resetuje dnia.
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var openTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> openTick++
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) viewModel.onLeftApp()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Wejście w zakładkę planu i wyjście z niej też ustawia dzień startowy. Przy wyjściu -
    // w tle, więc po powrocie plan już stoi na właściwym dniu (bez widocznego przeskoku);
    // przy wejściu sprawdzamy jeszcze raz (mógł minąć koniec lekcji).
    var wasShown by remember { mutableStateOf(isShown) }
    LaunchedEffect(isShown) {
        if (isShown != wasShown) {
            wasShown = isShown
            viewModel.requestOpeningDay()
            openTick++
        }
    }
    // Widok tygodnia przewija listę do dnia startowego (np. po lekcjach w środę - do czwartku).
    var weekFocus by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
    // Aktualny pager i tryb (tryb wczytuje się z ustawień asynchronicznie i tworzy nowy pager).
    val currentPager by rememberUpdatedState(pagerState)
    val currentMode by rememberUpdatedState(state.mode)
    LaunchedEffect(openTick) {
        if (openTick == 0 || !viewModel.consumeOpeningDay()) return@LaunchedEffect
        val day = viewModel.preferredDay()
        val target = when (currentMode) {
            TimetableViewModel.ViewMode.DAY -> TimetableViewModel.pageForDay(base, day)
            TimetableViewModel.ViewMode.WEEK -> TimetableViewModel.pageForWeek(base, day)
        }
        if (target != currentPager.currentPage) currentPager.scrollToPage(target)
        viewModel.onOpeningDay(day)
        weekFocus = day to ((weekFocus?.second ?: 0) + 1)
    }
    var lastSeenDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    LaunchedEffect(today) {
        if (today.toEpochDay() != lastSeenDay) {
            lastSeenDay = today.toEpochDay()
            pagerState.scrollToPage(todayPage)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    val dayMode = state.mode == TimetableViewModel.ViewMode.DAY
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
                        selected = state.mode == TimetableViewModel.ViewMode.DAY,
                        onClick = { viewModel.setMode(TimetableViewModel.ViewMode.DAY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) { Text("Dzień") }
                    SegmentedButton(
                        selected = state.mode == TimetableViewModel.ViewMode.WEEK,
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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageModifier = Modifier.fillMaxSize().nestedScroll(edgePaging)
                    when (state.mode) {
                        TimetableViewModel.ViewMode.DAY -> {
                            val date = TimetableViewModel.dayForPage(base, page)
                            val week by remember(date) { viewModel.week(date.with(java.time.DayOfWeek.MONDAY)) }
                                .collectAsStateWithLifecycle()
                            val monday = date.with(java.time.DayOfWeek.MONDAY)
                            val loading = week == null || syncingWeek == monday || state.isRefreshing
                            DayPage(date, week, clock, loading, pageModifier)
                        }
                        TimetableViewModel.ViewMode.WEEK -> {
                            val monday = TimetableViewModel.mondayForPage(base, page)
                            val week by remember(monday) { viewModel.week(monday) }.collectAsStateWithLifecycle()
                            val loading = week == null || syncingWeek == monday || state.isRefreshing
                            WeekPage(week, plLocale, dateFmt, loading, pageModifier, weekFocus)
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
    modifier: Modifier
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
            item { if (weekHasLessons) NoLessonsCard() else EmptyOrLoading(loading) }
        } else {
            val isToday = date == LocalDate.now()
            val now = clock
            val current = if (isToday) lessons.firstOrNull { now in it.timeFrom..it.timeTo } else null
            val next = if (isToday) lessons.firstOrNull { it.timeFrom > now } else null
            val hasEarlier = isToday && lessons.any { it.timeTo <= now }
            items(lessons, key = { it.id }) { lesson ->
                val badge = when {
                    !isToday -> null
                    current?.id == lesson.id -> "Trwa teraz"
                    next?.id == lesson.id -> {
                        val mins = Duration.between(now, lesson.timeFrom).toMinutes()
                        if (mins <= 30 || hasEarlier) "Za $mins min" else null
                    }
                    else -> null
                }
                LessonRow(lesson, badge)
            }
        }
    }
}

@Composable
private fun WeekPage(
    week: List<TimetableViewModel.DayColumn>?,
    plLocale: Locale,
    dateFmt: DateTimeFormatter,
    loading: Boolean,
    modifier: Modifier,
    /** Dzień startowy (data, numer żądania) - lista tygodnia przewija się do jego nagłówka. */
    focus: Pair<LocalDate, Int>? = null
) {
    val listState = rememberLazyListState()
    var handledFocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(focus, week) {
        val (date, tick) = focus ?: return@LaunchedEffect
        if (week == null || tick == handledFocus) return@LaunchedEffect
        handledFocus = tick
        weekHeaderIndex(week, date)?.let { listState.scrollToItem(it) }
    }
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (week == null || week.all { it.lessons.isEmpty() }) {
            item { EmptyOrLoading(loading) }
            return@LazyColumn
        }
        week.forEach { day ->
            val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, plLocale)
                .replaceFirstChar { it.titlecase(plLocale) }
            val isToday = day.date == LocalDate.now()
            item(key = "h_${day.date}") {
                Text(
                    (if (isToday) "Dziś · " else "") + "$dayName • ${day.date.format(dateFmt)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() }
                )
            }
            if (day.lessons.isEmpty()) {
                item(key = "e_${day.date}") {
                    Text("Brak lekcji",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            } else {
                items(day.lessons, key = { it.id }) { LessonRow(it, null) }
            }
        }
    }
}


/**
 * Indeks nagłówka dnia na liście tygodnia (musi odpowiadać układowi WeekPage: nagłówek +
 * lekcje albo "Brak lekcji"). null - dnia nie ma w tym tygodniu albo tydzień jest pusty.
 */
internal fun weekHeaderIndex(week: List<TimetableViewModel.DayColumn>, date: LocalDate): Int? {
    if (week.all { it.lessons.isEmpty() }) return null
    var index = 0
    for (day in week) {
        if (day.date == date) return index
        index += 1 + maxOf(day.lessons.size, 1)
    }
    return null
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
private fun EmptyOrLoading(loading: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dawniej zawsze kółko — bez internetu kręciło się w nieskończoność.
            if (loading) {
                CircularProgressIndicator()
                Text("Ładuję plan…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Brak zapisanego planu na ten okres.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Odśwież przyciskiem u góry, gdy będzie internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, badge: String?, modifier: Modifier = Modifier) {
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

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 1.dp)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg)) {
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
            }
        }
    }
}
