package pl.zse.bydgoszcz.elektron.presentation.substitutions

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
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionDisplay
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronCard
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstitutionsScreen(viewModel: SubstitutionsViewModel = hiltViewModel()) {
    val groups by viewModel.days.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val todayHidden by viewModel.todayHidden.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Zastępstwa", scrollBehavior = scrollBehavior) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Pusty stan też jest elementem LazyColumn — tylko przewijalna treść
            // pozwala pociągnąć w dół, żeby odświeżyć.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (groups.isEmpty() && isLoading && !isRefreshing) {
                    item(key = "loading") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (groups.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.EventAvailable, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Text("Brak zastępstw", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (todayHidden) "Lekcje na dziś się skończyły - dzisiejsze zastępstwa są w planie lekcji."
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
                    items(group.items, key = { it.id }) { SubstitutionRow(it, Modifier.animateItem().semantics(mergeDescendants = true) {}) }
                }
            }
        }
    }
}


@Composable
private fun SubstitutionRow(s: Substitution, modifier: Modifier = Modifier.semantics(mergeDescendants = true) {}) {
    ElektronCard(modifier = modifier) {
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
