package pl.zse.bydgoszcz.elektron.presentation.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plLocale = Locale("pl", "PL")
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM", plLocale)
    // Zegar dla plakietek "Trwa teraz" / "Za X min" — bez niego liczyły się tylko
    // przy rekompozycji z innego powodu i przy otwartym ekranie stały w miejscu.
    val clock by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previous, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Poprzedni")
                        }
                        IconButton(onClick = viewModel::next, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Następny")
                        }
                        Text(
                            text = when (state.mode) {
                                TimetableViewModel.ViewMode.DAY -> {
                                    val dn = state.anchorDate.dayOfWeek
                                        .getDisplayName(TextStyle.FULL, plLocale)
                                        .replaceFirstChar { it.titlecase(plLocale) }
                                    "$dn • ${state.anchorDate.format(dateFmt)}"
                                }
                                TimetableViewModel.ViewMode.WEEK -> {
                                    val monday = state.anchorDate.with(java.time.DayOfWeek.MONDAY)
                                    "Tydzień ${monday.format(dateFmt)}–${monday.plusDays(4).format(dateFmt)}"
                                }
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp)
                        )
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

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
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

            // Przesunięcie palcem w lewo/prawo: następny/poprzedni dzień (albo tydzień).
            SwipePager(
                state = state,
                onPrevious = viewModel::previous,
                onNext = viewModel::next
            ) { s ->
                if (s.mode == TimetableViewModel.ViewMode.DAY) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (s.lessonsToday.isEmpty()) {
                            // Plan tygodnia jest w bazie, a ten dzień po prostu nie ma lekcji —
                            // to nie jest ładowanie (dawniej spinner kręcił się w nieskończoność).
                            val weekHasLessons = s.weekDays.any { it.lessons.isNotEmpty() }
                            item { if (weekHasLessons) NoLessonsCard() else EmptyOrLoading() }
                        } else {
                            val today = LocalDate.now()
                            val now = clock
                            val isToday = s.anchorDate == today
                            val current = if (isToday) s.lessonsToday.firstOrNull { now in it.timeFrom..it.timeTo } else null
                            val next = if (isToday) s.lessonsToday.firstOrNull { it.timeFrom > now } else null
                            val hasEarlier = if (isToday) s.lessonsToday.any { it.timeTo <= now } else false
                            items(s.lessonsToday, key = { it.id }) { lesson ->
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
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        s.weekDays.forEach { day ->
                            val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, plLocale)
                                .replaceFirstChar { it.titlecase(plLocale) }
                            item(key = "h_${day.date}") {
                                Text("$dayName • ${day.date.format(dateFmt)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
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
            }
        }
        }
    }
}

/**
 * Poziomy gest zmiany strony planu. Treść podąża za palcem z oporem (jak w iOS), a po
 * przekroczeniu progu przechodzimy dalej/wstecz z animacją przesunięcia w odpowiednią
 * stronę. Gest przejmujemy dopiero po wyraźnym ruchu w poziomie, więc pionowe przewijanie
 * listy i pull-to-refresh działają jak dotąd.
 */
@Composable
private fun SwipePager(
    state: TimetableViewModel.State,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable (TimetableViewModel.State) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -threshold -> onNext()
                            total >= threshold -> onPrevious()
                        }
                        scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                        scope.launch { offset.snapTo(total * 0.35f) }
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = state,
            // Animujemy tylko zmianę strony (dzień/tydzień/tryb), nie każdą aktualizację danych.
            contentKey = { it.anchorDate to it.mode },
            transitionSpec = {
                val a = initialState.anchorDate
                val b = targetState.anchorDate
                if (a == b) {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                } else {
                    val sign = if (b > a) 1 else -1
                    (slideInHorizontally(tween(260)) { w -> sign * w / 3 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(200)) { w -> -sign * w / 3 } + fadeOut(tween(160)))
                }
            },
            label = "timetablePage",
            modifier = Modifier.fillMaxSize().graphicsLayer { translationX = offset.value }
        ) { s -> content(s) }
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
private fun EmptyOrLoading() {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator()
            Text("Ładuję plan…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, badge: String?) {
    val sub = lesson.substitution
    val bg = if (sub != null) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (sub != null) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onSurface
    val originalSubject = lesson.groups.firstOrNull()?.subject

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${lesson.number}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (sub != null) fg else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                if (sub != null) {
                    Text(sub.roomOrInfo, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = fg)
                    Text(sub.substituteTeacher ?: "Bez zastępcy",
                        style = MaterialTheme.typography.bodyLarge, color = fg)
                    val forLine = buildString {
                        append("Za: ")
                        if (originalSubject != null) append("$originalSubject • ")
                        append(sub.originalTeacher)
                    }
                    Text(forLine, style = MaterialTheme.typography.labelSmall, color = fg)
                } else {
                    lesson.groups.forEach { g ->
                        val line = buildString {
                            g.subject?.let { append(it) }
                            g.groupLabel?.let { append(" [$it]") }
                            g.room?.let { append(" • $it") }
                            g.teacherFullName?.let { append(" • $it") } ?: g.teacherCode?.let { append(" • $it") }
                        }
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                    lesson.note?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
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
            Column(
                Modifier.width(52.dp).padding(start = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(lesson.timeFrom.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
                Text(lesson.timeTo.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}
