package pl.zse.bydgoszcz.elektron.presentation.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls

/** Widoki bloków treści ogłoszenia (ArticleBlocks) w stylu aplikacji. */
internal object ArticleBlockViews {

    @Composable
    fun bodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp)

    /** Odstęp przed blokiem: mniejszy między punktami listy, większy przed nagłówkiem i wokół zdjęć. */
    fun gap(previous: ArticleBlocks.Block?, block: ArticleBlocks.Block): Dp = when {
        block is ArticleBlocks.Text && block.heading > 0 -> 22.dp
        previous is ArticleBlocks.Text && previous.heading > 0 -> 8.dp
        block is ArticleBlocks.Images || previous is ArticleBlocks.Images -> 16.dp
        block is ArticleBlocks.Text && previous is ArticleBlocks.Text && block.depth > 0 && previous.depth > 0 -> 6.dp
        else -> 12.dp
    }

    @Composable
    fun TextBlock(block: ArticleBlocks.Text, onLink: (String) -> Unit) {
        val colors = MaterialTheme.colorScheme
        val base = when (block.heading) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            else -> bodyStyle()
        }
        // Większy tekst ze strony nie może nachodzić na sąsiednie wiersze.
        val style = if (block.spans.any { it.scale > 1f }) base.copy(lineHeight = TextUnit.Unspecified) else base
        val text = remember(block, colors.primary, base.fontSize) { annotated(block.spans, base.fontSize, colors.primary, onLink) }
        val align = when (block.align) {
            ArticleBlocks.Align.CENTER -> TextAlign.Center
            ArticleBlocks.Align.END -> TextAlign.End
            ArticleBlocks.Align.START -> TextAlign.Start
        }
        val indent = (16 * (block.depth - 1).coerceAtLeast(0)).dp
        Row(Modifier.fillMaxWidth().padding(start = indent).height(IntrinsicSize.Min)) {
            if (block.quote) {
                Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colors.primary.copy(alpha = .6f)))
                Spacer(Modifier.width(12.dp))
            }
            block.marker?.let { marker ->
                Text(marker, style = style, color = colors.onSurfaceVariant, textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = style, color = colors.onSurface, textAlign = align,
                modifier = Modifier.weight(1f).then(if (block.heading > 0) Modifier.semantics { heading() } else Modifier))
        }
    }

    private fun annotated(spans: List<ArticleBlocks.Span>, fontSize: TextUnit, linkColor: Color, onLink: (String) -> Unit): AnnotatedString =
        buildAnnotatedString {
            spans.forEach { span ->
                val decorations = listOfNotNull(
                    TextDecoration.Underline.takeIf { span.underline },
                    TextDecoration.LineThrough.takeIf { span.strike }
                )
                val style = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                    fontSize = if (span.scale != 1f && fontSize != TextUnit.Unspecified) fontSize * span.scale else TextUnit.Unspecified
                )
                val url = span.link
                if (url == null) withStyle(style) { append(span.text) }
                else {
                    val listener = object : LinkInteractionListener {
                        override fun onClick(link: LinkAnnotation) = onLink(url)
                    }
                    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Url(url, styles, listener)) { withStyle(style) { append(span.text) } }
                }
            }
        }

    /** Jedno zdjęcie - cała szerokość w oryginalnych proporcjach; kilka - siatka miniatur. */
    @Composable
    fun GalleryBlock(images: List<ArticleBlocks.Image>, onOpen: (ArticleBlocks.Image) -> Unit) {
        if (images.size == 1) {
            ArticleImage(images.single(), "Zdjęcie", natural = true, modifier = Modifier.fillMaxWidth()) { onOpen(images.single()) }
            return
        }
        val spacing = 6.dp
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            // Nieparzysta liczba: pierwsze zdjęcie szerzej, reszta parami.
            val rest = if (images.size % 2 == 1) {
                ArticleImage(images.first(), "Zdjęcie 1 z ${images.size}", natural = false,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) { onOpen(images.first()) }
                images.drop(1)
            } else images
            rest.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    pair.forEach { image ->
                        ArticleImage(image, "Zdjęcie ${images.indexOf(image) + 1} z ${images.size}", natural = false,
                            modifier = Modifier.weight(1f).aspectRatio(1f)) { onOpen(image) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ArticleImage(image: ArticleBlocks.Image, label: String, natural: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        val context = LocalContext.current
        var state by remember(image.src) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        val loaded = state is AsyncImagePainter.State.Success
        BoxWithConstraints(
            modifier.clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest)
                .clickable(onClickLabel = "Powiększ zdjęcie", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Zdjęcie w oryginalnych proporcjach: rozmiar zapytania tylko po szerokości. Do czasu
            // wczytania stała wysokość - bez wymiarów obrazka Coil zająłby całą dozwoloną wysokość
            // (pusta przerwa) i dopasował do niej rozdzielczość.
            val width = constraints.maxWidth
            val request = remember(image.src, natural, width) {
                ImageRequest.Builder(context).data(image.src).crossfade(200)
                    .apply { if (natural && width > 0) size(Size(Dimension(width), Dimension.Undefined)) }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = image.alt ?: label,
                contentScale = if (natural) ContentScale.FillWidth else ContentScale.Crop,
                onState = { state = it },
                modifier = when {
                    !natural -> Modifier.fillMaxSize()
                    loaded -> Modifier.fillMaxWidth().heightIn(max = 640.dp)
                    else -> Modifier.fillMaxWidth().height(220.dp)
                }
            )
            if (state is AsyncImagePainter.State.Error) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = colors.onSurfaceVariant)
                    Text("Nie wczytano zdjęcia", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }

    @Composable
    fun TableBlock(table: ArticleBlocks.Table, onLink: (String) -> Unit) {
        val colors = MaterialTheme.colorScheme
        val columns = table.rows.maxOf { it.size }
        val wide = columns > 3
        val border = colors.outlineVariant
        val cellFontSize = MaterialTheme.typography.bodyMedium.fontSize
        Box(Modifier.fillMaxWidth().then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier)) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, border, RoundedCornerShape(10.dp))) {
                table.rows.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) HorizontalDivider(color = border)
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        (0 until columns).forEach { column ->
                            if (column > 0) VerticalDivider(color = border)
                            val text = annotated(row.getOrNull(column).orEmpty(), cellFontSize, colors.primary, onLink)
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                                modifier = (if (wide) Modifier.width(140.dp) else Modifier.weight(1f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Pełny ekran: przewijanie między zdjęciami ogłoszenia, przybliżanie dwoma palcami lub podwójnym dotknięciem. */
@Composable
internal fun ArticleImageViewer(images: List<ArticleBlocks.Image>, start: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, images.lastIndex)) { images.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { images[it].full }) { page ->
                ZoomableImage(images[page], onZoom = { if (page == pager.currentPage) zoomed = it })
            }
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f)).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Zamknij", tint = Color.White) }
                Text(if (images.size > 1) "${pager.currentPage + 1} z ${images.size}" else "",
                    color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { SafeUrls.open(context, images[pager.currentPage].full) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Otwórz zdjęcie w przeglądarce", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(image: ArticleBlocks.Image, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember { mutableStateOf(false) }
    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = boxSize.width * (zoom - 1f) / 2f
        val maxY = boxSize.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    fun update(zoom: Float, pan: Offset) {
        scale = zoom.coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else clamp(pan, scale)
        onZoom(scale > 1f)
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) update(1f, Offset.Zero)
                    else update(2.5f, (Offset(boxSize.width / 2f, boxSize.height / 2f) - tap) * 1.5f)
                })
            }
            .pointerInput(Unit) {
                // Dwa palce - przybliżenie; po przybliżeniu jeden palec przesuwa zdjęcie zamiast przewijać galerię.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            update(scale * event.calculateZoom(), offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center
    ) {
        // Półtora raza więcej pikseli niż ekran: przybliżony plakat zostaje czytelny, a pamięć
        // (jedno zdjęcie naraz) w rozsądnych granicach.
        // Zapytanie dopiero po zmierzeniu ekranu - bez podwójnego pobierania.
        val context = LocalContext.current
        if (boxSize.width > 0) {
            val request = remember(image.full, boxSize) {
                ImageRequest.Builder(context).data(image.full).crossfade(200)
                    .size((boxSize.width * 1.5f).toInt(), (boxSize.height * 1.5f).toInt())
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = image.alt ?: "Zdjęcie z ogłoszenia",
                contentScale = ContentScale.Fit,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (failed) Text("Nie udało się wczytać zdjęcia. Sprawdź połączenie albo otwórz je w przeglądarce.",
            color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}
