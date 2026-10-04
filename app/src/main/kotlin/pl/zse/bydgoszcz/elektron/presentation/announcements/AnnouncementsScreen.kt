package pl.zse.bydgoszcz.elektron.presentation.announcements

import pl.zse.bydgoszcz.elektron.presentation.common.DelayedLoading
import pl.zse.bydgoszcz.elektron.presentation.common.revealWhen
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import coil.request.ImageRequest
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.presentation.common.ElektronCard
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnnouncementsScreen(viewModel: AnnouncementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val article by viewModel.article.collectAsStateWithLifecycle()
    val articleLoading by viewModel.articleLoading.collectAsStateWithLifecycle()
    val articleError by viewModel.articleError.collectAsStateWithLifecycle()
    val clearingFavorites by viewModel.clearingFavorites.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Usunąć wszystkie z ulubionych?") },
            text = { Text("Usuniesz wszystkie zakładki, również te ukryte przez wyszukiwanie. Ogłoszenia i pobrana treść pozostaną w aplikacji.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearFavorites() },
                enabled = !clearingFavorites) { Text("Usuń wszystkie") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } }
        )
    }
    article?.let { AnnouncementArticleSheet(it, articleLoading, articleError,
        onRetry = { viewModel.openArticle(it.id) },
        onFavorite = { viewModel.toggleFavorite(it.id) }, onDismiss = viewModel::closeArticle) }
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage(it) } }
    val fmt = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { LargeTitleBar(title = "Ogłoszenia", scrollBehavior = scrollBehavior, actions = {
            if (state.favoritesOnly) {
                IconButton(onClick = { confirmClear = true }, enabled = state.favoriteCount > 0 && !clearingFavorites) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Usuń wszystkie z ulubionych")
                }
            }
        }) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().revealWhen(state.ready),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "search") {
                    Column {
                        SearchField(state.query, viewModel::setQuery)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !state.favoritesOnly, onClick = { viewModel.setFavoritesOnly(false) }, label = { Text("Wszystkie") })
                            FilterChip(selected = state.favoritesOnly, onClick = { viewModel.setFavoritesOnly(true) }, label = { Text("Ulubione (${state.favoriteCount})") })

                        }
                    }
                }
                if (state.isSearching && state.items.isEmpty()) {
                    item(key = "no_results") {
                        Text("Brak wyników dla „${state.query.trim()}” w pobranych ogłoszeniach.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            textAlign = TextAlign.Center)
                    }
                } else if (state.items.isEmpty() && state.isInitialLoading && !state.isRefreshing) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            DelayedLoading { CircularProgressIndicator() }
                        }
                    }
                } else if (state.items.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Campaign, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Text(if (state.favoritesOnly) "Brak ulubionych ogłoszeń" else "Brak ogłoszeń", style = MaterialTheme.typography.titleMedium)
                                if (state.favoritesOnly) Text("Dotknij zakładki przy ogłoszeniu, aby je tutaj zapisać.",
                                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
                items(state.items, key = { it.id }) { a ->
                    AnnouncementCard(a, fmt, Modifier.animateItem(),
                        onFavorite = { viewModel.toggleFavorite(a.id) }) { viewModel.openArticle(a.id) }
                }
                if (state.hasMore && (state.items.isNotEmpty() || state.isSearching)) {
                    item(key = "more") {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                Text("Pobieram starsze ogłoszenia…",
                                    Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                if (state.loadMoreError) {
                                    Text("Nie udało się pobrać starszych ogłoszeń.",
                                        Modifier.padding(bottom = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                FilledTonalButton(onClick = viewModel::loadMore) {
                                    Text(when {
                                        state.loadMoreError -> "Spróbuj ponownie"
                                        state.isSearching -> "Szukaj w starszych ogłoszeniach"
                                        else -> "Pokaż więcej"
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    a: Announcement,
    fmt: DateTimeFormatter,
    modifier: Modifier = Modifier,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    ElektronCard(modifier = modifier, onClick = onClick) {
        a.coverImageUrl?.let { url ->
            // Łagodne pojawienie się obrazka + tło zamiast pustego miejsca podczas wczytywania.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(250).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onFavorite) {
                    Icon(if (a.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (a.isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(a.publishedAt.atZone(ZoneId.systemDefault()).format(fmt), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            a.excerpt?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Pole wyszukiwania w stylu iOS: zaokrąglone, szare tło, lupa i przycisk czyszczenia. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Szukaj w ogłoszeniach") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery(""); focus.clearFocus() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Wyczyść wyszukiwanie")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
}
