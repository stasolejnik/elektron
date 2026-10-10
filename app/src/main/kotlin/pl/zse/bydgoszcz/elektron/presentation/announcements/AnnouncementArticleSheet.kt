package pl.zse.bydgoszcz.elektron.presentation.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ogłoszenie w aplikacji: tytuł, data i cała treść w jednej przewijanej kolumnie, zdjęcia w tym
 * miejscu, w którym są w poście (dotknięcie - przeglądarka zdjęć). Bez WebView: tekst, kolory
 * i odstępy są takie jak w reszcie aplikacji, a puste akapity ze strony nie robią przerw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnnouncementArticleSheet(
    article: Announcement, loading: Boolean, error: String?,
    onRetry: () -> Unit, onFavorite: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val parsed = remember(article.id) { mutableStateOf<List<ArticleBlocks.Block>?>(null) }
    LaunchedEffect(article.id, article.fullHtml) {
        val html = article.fullHtml
        if (!html.isNullOrBlank()) parsed.value = withContext(Dispatchers.Default) {
            runCatching { ArticleBlocks.parse(html, article.url) }.getOrDefault(emptyList())
        }
    }
    val blocks = parsed.value?.takeIf { it.isNotEmpty() }
    val images = remember(blocks) { blocks?.let(ArticleBlocks::images).orEmpty() }
    var viewerIndex by rememberSaveable(article.id) { mutableStateOf(-1) }
    if (viewerIndex in images.indices) ArticleImageViewer(images, viewerIndex) { viewerIndex = -1 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            item(key = "header") { ArticleHeader(article, offline = !article.fullHtml.isNullOrBlank(), onFavorite) }
            if (blocks != null) {
                // Bez okładki: zdjęcia są w treści, w swoim miejscu. Okładka tylko, gdy treść nie ma zdjęć.
                if (images.isEmpty()) pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls.schoolImage(article.coverImageUrl)?.let { cover ->
                    item(key = "cover") { CoverImage(cover) }
                }
                itemsIndexed(blocks, key = { index, _ -> "b$index" }, contentType = { _, block -> block::class }) { index, block ->
                    val previous = blocks.getOrNull(index - 1)
                    Box(Modifier.padding(top = if (index == 0) 0.dp else ArticleBlockViews.gap(previous, block))) {
                        when (block) {
                            is ArticleBlocks.Text -> SelectionContainer {
                                ArticleBlockViews.TextBlock(block) { SafeUrls.open(context, it) }
                            }
                            is ArticleBlocks.Images -> ArticleBlockViews.GalleryBlock(block.images) { image ->
                                viewerIndex = images.indexOf(image)
                            }
                            is ArticleBlocks.Table -> ArticleBlockViews.TableBlock(block) { SafeUrls.open(context, it) }
                            ArticleBlocks.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
                        }
                    }
                }
            } else {
                item(key = "fallback") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls.schoolImage(article.coverImageUrl)?.let { CoverImage(it) }
                        article.excerpt?.let {
                            SelectionContainer { Text(it, style = ArticleBlockViews.bodyStyle(), color = colors.onSurface) }
                        }
                        if (loading || (!article.fullHtml.isNullOrBlank() && parsed.value == null)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Pobieram pełną treść…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                        error?.let {
                            Text("Nie udało się pobrać pełnej treści. $it", style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant)
                            TextButton(onClick = onRetry, enabled = !loading) { Text("Spróbuj ponownie") }
                        }
                    }
                }
            }
            item(key = "footer") {
                ArticleFooter(onOpen = { SafeUrls.open(context, article.url) })
            }
        }
    }
}

@Composable
private fun ArticleHeader(article: Announcement, offline: Boolean, onFavorite: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val date = remember(article.publishedAt) {
        article.publishedAt.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("pl", "PL")))
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(article.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface,
                modifier = Modifier.weight(1f).padding(top = 4.dp).semantics { heading() })
            IconButton(onClick = onFavorite, modifier = Modifier.offset(x = 8.dp)) {
                Icon(if (article.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (article.isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    tint = colors.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false))
            if (offline) {
                Spacer(Modifier.width(10.dp))
                Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) {
                    Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.OfflinePin, contentDescription = null, tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dostępne offline", style = MaterialTheme.typography.labelSmall, color = colors.onSecondaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
private fun CoverImage(url: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

/** Bez "Odśwież": treść zapisana starszą wersją pobiera się sama, a ręczne odświeżanie było zbędne. */
@Composable
private fun ArticleFooter(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Otwórz na stronie szkoły", fontWeight = FontWeight.SemiBold)
        }
    }
}
