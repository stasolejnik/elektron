package pl.zse.bydgoszcz.elektron.presentation.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronCard
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar
import pl.zse.bydgoszcz.elektron.presentation.common.SectionTitle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val openUrl = { url: String ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        Unit
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Start", scrollBehavior = scrollBehavior) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.selectedClassId == null) {
                    item {
                        InfoCard("Witaj w eLektron",
                            "Nie wybrano jeszcze klasy. Przejdź do Ustawień i wybierz swoją klasę.")
                    }
                } else {
                    state.lastSyncError?.let { err ->
                        item(key = "error") { ErrorBanner(err) { viewModel.refresh() } }
                    }

                    item(key = "syncing") {
                        AnimatedVisibility(
                            visible = state.isSyncing && !state.isRefreshing && state.lastSyncError == null,
                            enter = fadeIn(), exit = fadeOut()
                        ) { SyncingIndicator() }
                    }

                    if (state.showNextLesson) {
                        val icon = when (state.nextLessonStatus) {
                            DashboardViewModel.LessonStatus.Now -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.Schedule
                        }
                        val title = when (state.nextLessonStatus) {
                            DashboardViewModel.LessonStatus.Now -> "Trwa teraz"
                            DashboardViewModel.LessonStatus.Next ->
                                state.nextLessonDayLabel?.let { "Następna lekcja · $it" } ?: "Następna lekcja"
                            DashboardViewModel.LessonStatus.None -> "Następna lekcja"
                        }
                        item(key = "h_next") { SectionTitle(title, icon) }
                        item(key = "next") {
                            if (state.loadingTimetable) LoadingCard("Ładowanie planu lekcji…")
                            else NextLessonCard(state)
                        }
                    }

                    if (state.showSubstitutions) {
                        item(key = "h_subs") {
                            val t = if (state.upcomingSubstitutions.isEmpty()) "Zastępstwa"
                                else "Zastępstwa (${state.upcomingSubstitutions.size})"
                            SectionTitle(t, Icons.Filled.EventBusy)
                        }
                        when {
                            state.loadingSubs -> item(key = "subs_loading") { LoadingCard("Ładowanie zastępstw…") }
                            state.upcomingSubstitutions.isEmpty() -> item(key = "subs_empty") { EmptyCard("Brak zastępstw") }
                            else -> items(state.upcomingSubstitutions, key = { "sub_${it.id}" }) {
                                SubstitutionRow(it, Modifier.animateItem())
                            }
                        }
                    }
                }

                if (state.showAnnouncements) {
                    if (state.latestAnnouncements.isNotEmpty()) {
                        item(key = "h_anns") { SectionTitle("Najnowsze ogłoszenia", Icons.Filled.Campaign) }
                        items(state.latestAnnouncements, key = { "ann_${it.id}" }) { ann ->
                            AnnouncementRow(ann, Modifier.animateItem()) { openUrl(ann.url) }
                        }
                    } else if (state.loadingAnns) {
                        item(key = "h_anns") { SectionTitle("Najnowsze ogłoszenia", Icons.Filled.Campaign) }
                        item(key = "anns_loading") { LoadingCard("Ładowanie ogłoszeń…") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncingIndicator() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Synchronizacja…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingCard(text: String) {
    ElektronCard {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    ElektronCard {
        Text(text, Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBanner(text: String, onRetry: () -> Unit) {
    ElektronCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

@Composable
private fun NextLessonCard(state: DashboardViewModel.State) {
    val lesson = state.nextLesson
    val sub = lesson?.substitution
    val container = if (sub != null) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (sub != null) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    ElektronCard(containerColor = container, modifier = Modifier.animateContentSize()) {
        Column(Modifier.padding(18.dp)) {
            if (lesson == null) {
                Text("Brak nadchodzących lekcji", style = MaterialTheme.typography.bodyLarge,
                    color = onContainer)
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        val subject = sub?.roomOrInfo?.takeIf { it.isNotBlank() }
                            ?: lesson.groups.firstOrNull()?.subject ?: "Lekcja"
                        Text(subject, style = MaterialTheme.typography.headlineSmall,
                            color = onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val room = lesson.groups.firstOrNull()?.room ?: "—"
                        Text("${lesson.timeFrom}–${lesson.timeTo} · sala $room",
                            style = MaterialTheme.typography.bodyLarge, color = onContainer)
                        val teacher = sub?.substituteTeacher
                            ?: lesson.groups.firstOrNull()?.teacherFullName
                            ?: lesson.groups.firstOrNull()?.teacherCode
                        teacher?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = onContainer.copy(alpha = 0.8f))
                        }
                    }
                    LessonNumberBadge(lesson.number, onContainer)
                }
                state.countdownMinutes?.let { mins ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = onContainer.copy(alpha = 0.12f)
                    ) {
                        Text("Za $mins min",
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge, color = onContainer)
                    }
                }
                sub?.notes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = onContainer)
                }
            }
        }
    }
}

@Composable
private fun LessonNumberBadge(number: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
        Text("$number", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SubstitutionRow(s: Substitution, modifier: Modifier = Modifier) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM")
    ElektronCard(modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.lessonNumber}", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(s.date.format(fmt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.roomOrInfo, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.substituteTeacher ?: "Bez zastępcy", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Za: ${s.originalTeacher}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    ElektronCard {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnnouncementRow(a: Announcement, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("d.MM.yyyy")
    val date = a.publishedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
    ElektronCard(modifier = modifier, onClick = onClick) {
        a.coverImageUrl?.let { url ->
            // Pełna szerokość, proporcje 16:9, przycięcie — dawniej ContentScale.Fit
            // zostawiał puste pasy po bokach obrazka.
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(date, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
            a.excerpt?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
