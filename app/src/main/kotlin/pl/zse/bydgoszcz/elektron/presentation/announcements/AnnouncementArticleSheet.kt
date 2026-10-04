package pl.zse.bydgoszcz.elektron.presentation.announcements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnnouncementArticleSheet(
    article: Announcement, loading: Boolean, error: String?,
    onRetry: () -> Unit, onFavorite: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val textColor = "#%06X".format(colors.onSurface.toArgb() and 0xffffff)
    val linkColor = "#%06X".format(colors.primary.toArgb() and 0xffffff)
    val document by produceState<String?>(null, article.id, article.fullHtml, textColor, linkColor) {
        value = null
        value = withContext(Dispatchers.Default) {
            article.fullHtml?.takeIf { it.isNotBlank() }?.let { ArticleHtml.document(it, article.url, textColor, linkColor) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 20.dp)) {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onFavorite) {
                        Icon(if (article.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (article.isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                            tint = colors.primary)
                    }
                }
                val date = remember(article.publishedAt) {
                    article.publishedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm", Locale("pl", "PL")))
                }
                Text(if (!article.fullHtml.isNullOrBlank()) "$date · Zapisane offline" else date,
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (document != null) {
                ArticleHtmlView(document!!, article.url, Modifier.fillMaxWidth().weight(1f))
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    article.excerpt?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                    if (loading || !article.fullHtml.isNullOrBlank()) CircularProgressIndicator(Modifier.size(24.dp))
                    error?.let {
                        Text("Treść nie została zapisana. $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
                    }
                }
            }
            TextButton(onClick = { SafeUrls.open(context, article.url) }, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Otwórz stronę szkoły")
            }
        }
    }
}
