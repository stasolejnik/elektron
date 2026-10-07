package pl.zse.bydgoszcz.elektron.presentation.substitutions

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionsShare
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.TextButton
import pl.zse.bydgoszcz.elektron.presentation.common.DelayedLoading
import pl.zse.bydgoszcz.elektron.presentation.common.revealWhen
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionDisplay
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronCard
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstitutionsScreen(
    /** Dotknięcie wiersza: plan na dzień zastępstwa i szczegóły tej lekcji (NavHost). */
    onOpenLesson: (LessonTarget) -> Unit = {},
    viewModel: SubstitutionsViewModel = hiltViewModel()
) {
    val visible = pl.zse.bydgoszcz.elektron.presentation.common.LocalScreenVisible.current
    androidx.compose.runtime.DisposableEffect(viewModel, visible) {
        viewModel.setVisible(visible)
        onDispose { viewModel.setVisible(false) }
    }

    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val lastSuccess by viewModel.lastSuccess.collectAsStateWithLifecycle()
    val wasLoaded by viewModel.wasLoaded.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(refreshMessage) {
        refreshMessage?.let { snackbar.showSnackbar(it); viewModel.refreshMessage.value = null }
    }
    val groups by viewModel.days.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val todayHidden by viewModel.todayHidden.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val otherGroups by viewModel.otherGroups.collectAsStateWithLifecycle()
    val showOtherGroups by viewModel.showOtherGroups.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    fun share(items: List<Substitution>) {
        val text = SubstitutionsShare.text(items)
        if (text.isBlank()) return
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Udostępnij zastępstwa"))
    }
    val visibleItems = groups.flatMap { it.items } + if (showOtherGroups) otherGroups else emptyList()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = { LargeTitleBar(title = "Zastępstwa", scrollBehavior = scrollBehavior, actions = {
            IconButton(onClick = { share(visibleItems) }, enabled = visibleItems.isNotEmpty(), colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                Icon(Icons.Filled.Share, contentDescription = "Udostępnij wszystkie widoczne zastępstwa")
            }
        }) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Pusty stan też jest elementem LazyColumn — tylko przewijalna treść
            // pozwala pociągnąć w dół, żeby odświeżyć.
            LazyColumn(
                Modifier.fillMaxSize().revealWhen(ready),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                lastError?.let { error -> item(key = "sync_error") {
                    Text(error + if (wasLoaded) " Wyświetlam zapisane dane." else "", color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp))
                } }
                lastSuccess?.let { at -> item(key = "freshness") {
                    Text("Zastępstwa sprawdzono: " + at.atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d.MM, HH:mm")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                if (groups.isEmpty() && isLoading && !isRefreshing) {
                    item(key = "loading") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            DelayedLoading { CircularProgressIndicator() }
                        }
                    }
                } else if (groups.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(if (otherGroups.isEmpty()) 1f else 0.7f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.EventAvailable, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Text(if (!wasLoaded || lastError != null) "Brak potwierdzonych danych" else "Brak zastępstw", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (!wasLoaded || lastError != null) "Nie udało się potwierdzić braku zmian. Pociągnij w dół, aby spróbować ponownie."
                                    else if (todayHidden) "Dzisiejsze zastępstwa, które już minęły, są w planie lekcji."
                                    else if (otherGroups.isNotEmpty()) "Dla Twoich grup nie ma zaplanowanych zmian."
                                    else "Dla Twojej klasy nie ma zaplanowanych zmian.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                }
                groups.forEach { group ->
                    item(key = "header_${group.date}") {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp)
                        )
                    }
                    items(group.items, key = { it.id }) {
                        SubstitutionRow(it, onOpenLesson, { share(listOf(it)) }, Modifier.animateItem().semantics(mergeDescendants = true) {})
                    }
                }
                // Zastępstwa innych grup klasy: domyślnie ukryte, na żądanie przygaszone.
                if (otherGroups.isNotEmpty()) {
                    item(key = "other_toggle") {
                        TextButton(
                            onClick = viewModel::toggleOtherGroups,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).animateItem()
                        ) {
                            Text(
                                if (showOtherGroups) "Ukryj zastępstwa innych grup"
                                else "Pokaż zastępstwa innych grup (${otherGroups.size})"
                            )
                        }
                    }
                    if (showOtherGroups) {
                        items(otherGroups, key = { "other_${it.id}" }) {
                            // Okno szczegółów otworzy się tylko, jeśli ta lekcja jest w Twoim planie
                            // (po filtrze grup) - inaczej sam plan na ten dzień.
                            SubstitutionRow(it, onOpenLesson, { share(listOf(it)) }, Modifier.animateItem().alpha(0.55f).semantics(mergeDescendants = true) {
                                contentDescription = "Zastępstwo innej grupy"
                            })
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SubstitutionRow(
    s: Substitution,
    onOpenLesson: (LessonTarget) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier.semantics(mergeDescendants = true) {}
) {
    ElektronCard(
        modifier = modifier,
        onClick = { onOpenLesson(LessonTarget(s.date, s.lessonNumber)) },
        onClickLabel = "Otwórz szczegóły lekcji",
        onLongClick = onShare, onLongClickLabel = "Udostępnij zastępstwo"
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("${s.lessonNumber}",
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // Nagłówek: nauczyciel zastępujący (bez zastępcy - informacja), pod nim sala.
                Text(SubstitutionDisplay.headline(s), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                SubstitutionDisplay.place(s)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val forLine = buildString {
                    append("Za: ${s.originalTeacher}")
                    s.groupNumber?.let { append(" · grupa $it") }
                }
                Text(forLine, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SubstitutionDisplay.notes(s)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}
