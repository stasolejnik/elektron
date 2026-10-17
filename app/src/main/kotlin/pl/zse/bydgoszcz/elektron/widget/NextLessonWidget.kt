package pl.zse.bydgoszcz.elektron.widget

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.background
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import java.time.Duration
import pl.zse.bydgoszcz.elektron.R

/**
 * Widżet "Następna lekcja": trwająca albo najbliższa lekcja (z uwzględnieniem wybranych grup
 * i zastępstw). W wersji szerokiej dodatkowo lekcja po niej.
 */
class NextLessonWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val load: suspend () -> Pair<WidgetState, WidgetPalette> = {
            val state = WidgetDataLoader.load(context)
            widgetTickAt(state, AppClock.now())?.let {
                WidgetDataLoader.entryPoint(context).widgetUpdater().scheduleTick(it)
            }
            state to WidgetDataLoader.palette(context)
        }
        val initial = loadSnapshot(load)
        provideContent {
            val (state, palette) = rememberLiveWidgetData(initial, load) { old, fresh -> retainWidgetData(old.first, fresh.first) to fresh.second }
            CompositionLocalProvider(LocalWidgetPalette provides palette) { Content(state) }
        }
    }

    @Composable
    private fun Content(state: WidgetState) {
        // Pokazywana lekcja (także jutrzejsza - data TEGO dnia): dotknięcie całego widżetu
        // otwiera Plan. Dotknięcie konkretnej lekcji zachowuje jej własny cel.
        val target = WidgetTargets.focus(state)
        val ready = state as? WidgetState.Ready
        LaunchedEffect(target) {
            Log.i(TAG, "Cel: ${target?.let { "${it.date}/${it.lessonNumber}" } ?: "brak lekcji (plan lekcji)"}" +
                (ready?.let { ", lekcji w stanie: ${it.lessons.size}" } ?: ""))
        }
        WidgetContainer(
            target = "timetable",
            description = ready?.let { r ->
                val l = r.focus
                listOfNotNull("${r.dayLabel}, lekcja ${l.number}", l.title, l.timeRange, l.room?.let { "sala $it" }, l.detail, l.note,
                    widgetTimeStatus(r, AppClock.time()), l.userNote?.let { "Notatka: $it" },
                    r.following?.let { f -> listOfNotNull("Potem lekcja ${f.number}", f.title, f.timeRange, f.room?.let { "sala $it" }, f.note).joinToString(", ") })
                    .joinToString(", ") + ". Otwórz plan lekcji"
            }
        ) {
            when (state) {
                WidgetState.Failed -> WidgetMessage("Nie udało się odczytać danych. Otwórz aplikację i spróbuj ponownie.")
                WidgetState.NoClass -> WidgetMessage("Otwórz eLektron i wybierz klasę.")
                is WidgetState.NoLessons -> WidgetMessage("Brak lekcji w najbliższych dniach.")
                is WidgetState.Ready -> {
                    val wide = LocalSize.current.width >= WIDE.width
                    val following = state.following
                    if (wide && following != null) {
                        Row(GlanceModifier.fillMaxSize()) {
                            Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Focus(state, showFollowing = false) }
                            Spacer(GlanceModifier.width(12.dp))
                            Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Following(following, state.date) }
                        }
                    } else {
                        // Wąski widżet: kolejna lekcja pod główną (skrót w jednej linii albo nic, gdy brak miejsca).
                        Focus(state, showFollowing = true)
                    }
                }
            }
        }
    }

    @Composable
    private fun Focus(state: WidgetState.Ready, showFollowing: Boolean) {
        val lesson = state.focus
        val accent = if (lesson.isSubstitution) WidgetColors.substitution else WidgetColors.accent
        // Numer i pasek postępu w kolorze przedmiotu (Ustawienia -> Przedmioty), jak w planie.
        val lessonColor = if (lesson.isSubstitution) accent
            else lesson.color?.let { ColorProvider(Color(it)) } ?: accent
        val label = when {
            state.focusIsNow -> "TERAZ"
            state.breakFrom != null -> "PRZERWA"
            state.isToday -> "NASTĘPNA"
            else -> state.dayLabel.uppercase()
        }
        val scale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
        val height = LocalSize.current.height.value
        val compact = height < 210f * scale
        val titleLines = if (compact) 1 else 2
        val showTimeStatus = height >= 150f * scale
        val showDetail = lesson.detail != null && height >= 180f * scale &&
            ((!state.focusIsNow && state.breakFrom == null) || lesson.isSubstitution)
        val reserved = 30f + scale * (18f + 20f * titleLines + 16f +
            (if (compact) 0f else 36f) + (if (showDetail) 16f else 0f) +
            (if (lesson.note != null) 18f else 0f) + (if (showTimeStatus) 28f else 0f))
        val following = state.following?.takeIf { showFollowing }
        val followingLines = if (following == null) 0 else WidgetNoteLayout.followingLines(height, reserved, scale)
        val noteLines = WidgetNoteLayout.lines(height, reserved + followingLines * 16f * scale + (if (followingLines > 0) 4f else 0f), scale)
        val now = AppClock.time()
        // Bez wiersza czasu (mały widżet) odliczanie trafia do nagłówka - "Za X min" jak na stronie głównej.
        val header = when {
            state.readError -> "BŁĄD ODCZYTU · ZAPISANY PLAN"
            !showTimeStatus -> widgetCompactLabel(state, now, label)
            else -> label
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(header, style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (!compact) Text("${lesson.number}", style = TextStyle(color = lessonColor, fontSize = 30.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(if (compact) "${lesson.number}. ${lesson.title}" else lesson.title,
                modifier = GlanceModifier.clickable(openLessonAction(LocalContext.current, pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(state.date, lesson.number))),
                style = TextStyle(color = WidgetColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = titleLines)
            Spacer(GlanceModifier.height(2.dp))
            val where = listOfNotNull(lesson.timeRange, lesson.room?.let { "s. $it" }).joinToString(" · ")
            Text(where, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            // Przy trwającej lekcji miejsce zajmuje pasek postępu — nazwisko tylko przed lekcją
            // (i zawsze przy zastępstwie, bo to kluczowa informacja).
            if (showDetail) lesson.detail?.let {
                Text(it, style = TextStyle(color = if (lesson.isSubstitution) accent else WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            }
            lesson.note?.let {
                Text(it, style = TextStyle(color = WidgetColors.substitution, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            if (noteLines > 0) lesson.userNote?.let {
                Text("Notatka: $it", style = TextStyle(color = WidgetColors.textPrimary, fontSize = 12.sp), maxLines = noteLines)
            }
            // Czas: w trakcie lekcji i przerwy pasek postępu, tekst ze wspólnej reguły (widgetTimeStatus).
            val status = if (showTimeStatus) widgetTimeStatus(state, now) else null
            val progressFrom = when {
                state.focusIsNow -> lesson.timeFrom
                else -> state.breakFrom
            }
            val progressTo = if (state.focusIsNow) lesson.timeTo else lesson.timeFrom
            if (status != null && progressFrom != null) {
                val total = Duration.between(progressFrom, progressTo).seconds.coerceAtLeast(1)
                val done = Duration.between(progressFrom, now).seconds.coerceIn(0, total)
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = done.toFloat() / total,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = lessonColor,
                    backgroundColor = WidgetColors.accentContainer
                )
            }
            status?.let {
                Text(it, style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            // Kolejna lekcja (wąski widżet): dwie linie, gdy jest miejsce, inaczej jedna albo wcale.
            if (following != null && followingLines > 0) {
                val color = if (following.isSubstitution) WidgetColors.substitution else WidgetColors.textSecondary
                Spacer(GlanceModifier.height(4.dp))
                val click = GlanceModifier.clickable(openLessonAction(LocalContext.current,
                    pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(state.date, following.number)))
                if (followingLines >= 2) {
                    Text("Potem: ${following.number}. ${following.title}", modifier = click,
                        style = TextStyle(color = if (following.isSubstitution) WidgetColors.substitution else WidgetColors.textPrimary,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(listOfNotNull(following.timeRange, following.room?.let { "s. $it" }, following.note).joinToString(" · "),
                        style = TextStyle(color = color, fontSize = 11.sp), maxLines = 1)
                } else {
                    Text(followingShort(following), modifier = click, style = TextStyle(color = color, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Following(lesson: WidgetLesson, date: java.time.LocalDate) {
        val scale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
        val height = LocalSize.current.height.value
        val titleLines = if (height >= 180f * scale) 2 else 1
        val reserved = 28f + scale * (18f + titleLines * 20f + 16f +
            (if (lesson.room != null) 16f else 0f) + (if (lesson.note != null) 18f else 0f))
        val noteLines = WidgetNoteLayout.lines(height, reserved, scale)
        Column(GlanceModifier.fillMaxSize()) {
            Text("POTEM", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            val color = if (lesson.isSubstitution) WidgetColors.substitution else WidgetColors.textPrimary
            Text("${lesson.number}. ${lesson.title}", modifier = GlanceModifier.clickable(openLessonAction(LocalContext.current,
                pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(date, lesson.number))), style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = titleLines)
            Text(lesson.timeRange, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            lesson.room?.let {
                Text("s. $it", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            }
            lesson.note?.let {
                Text(it, style = TextStyle(color = WidgetColors.substitution, fontSize = 11.sp), maxLines = 1)
            }
            if (noteLines > 0) lesson.userNote?.let {
                Text("Notatka: $it", style = TextStyle(color = WidgetColors.textPrimary, fontSize = 12.sp), maxLines = noteLines)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

private const val TAG = "NextLessonWidget"

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}
