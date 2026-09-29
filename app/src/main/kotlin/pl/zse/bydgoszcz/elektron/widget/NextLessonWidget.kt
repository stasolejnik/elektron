package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import java.time.LocalTime
import pl.zse.bydgoszcz.elektron.R

/**
 * Widżet "Następna lekcja": trwająca albo najbliższa lekcja (z uwzględnieniem wybranych grup
 * i zastępstw). W wersji szerokiej dodatkowo lekcja po niej.
 */
class NextLessonWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val load: suspend () -> Pair<WidgetState, WidgetPalette> = {
            val state = WidgetDataLoader.load(context)
            if (state is WidgetState.Ready) {
                WidgetDataLoader.entryPoint(context).widgetUpdater().scheduleTick(state.refreshAt)
            }
            state to WidgetDataLoader.palette(context)
        }
        val initial = loadSnapshot(load)
        provideContent {
            val (state, palette) = rememberLiveWidgetData(initial, load)
            CompositionLocalProvider(LocalWidgetPalette provides palette) { Content(state) }
        }
    }

    @Composable
    private fun Content(state: WidgetState) {
        WidgetContainer {
            when (state) {
                WidgetState.NoClass -> WidgetMessage("Otwórz eLektron i wybierz klasę.")
                is WidgetState.NoLessons -> WidgetMessage("Brak lekcji w najbliższych dniach.")
                is WidgetState.Ready -> {
                    val wide = LocalSize.current.width >= WIDE.width
                    if (wide) {
                        Row(GlanceModifier.fillMaxSize()) {
                            Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Focus(state) }
                            val following = state.following
                            if (following != null) {
                                Spacer(GlanceModifier.width(12.dp))
                                Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Following(following) }
                            }
                        }
                    } else {
                        Focus(state)
                    }
                }
            }
        }
    }

    @Composable
    private fun Focus(state: WidgetState.Ready) {
        val lesson = state.focus
        val accent = if (lesson.isSubstitution) WidgetColors.substitution else WidgetColors.accent
        val label = when {
            state.focusIsNow -> "TERAZ"
            state.breakFrom != null -> "PRZERWA"
            state.isToday -> "NASTĘPNA"
            else -> state.dayLabel.uppercase()
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(label, style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text("${lesson.number}", style = TextStyle(color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(lesson.title, style = TextStyle(color = WidgetColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(GlanceModifier.height(2.dp))
            val where = listOfNotNull(lesson.timeRange, lesson.room?.let { "s. $it" }).joinToString(" · ")
            Text(where, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            // Przy trwającej lekcji miejsce zajmuje pasek postępu — nazwisko tylko przed lekcją
            // (i zawsze przy zastępstwie, bo to kluczowa informacja).
            if ((!state.focusIsNow && state.breakFrom == null) || lesson.isSubstitution) lesson.detail?.let {
                Text(it, style = TextStyle(color = if (lesson.isSubstitution) accent else WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            }
            lesson.note?.let {
                Text(it, style = TextStyle(color = WidgetColors.substitution, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            // Czas: w trakcie lekcji pasek postępu + "Zostało X min", przed lekcją "Za X min".
            val now = LocalTime.now()
            if (state.focusIsNow) {
                val total = Duration.between(lesson.timeFrom, lesson.timeTo).seconds.coerceAtLeast(1)
                val done = Duration.between(lesson.timeFrom, now).seconds.coerceIn(0, total)
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = done.toFloat() / total,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = accent,
                    backgroundColor = WidgetColors.accentContainer
                )
                val left = Duration.between(now, lesson.timeTo).toMinutes().coerceAtLeast(0)
                Text("Zostało $left min", style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            } else if (state.breakFrom != null) {
                // Przerwa: pasek postępu przerwy i ile do następnej lekcji.
                val total = Duration.between(state.breakFrom, lesson.timeFrom).seconds.coerceAtLeast(1)
                val done = Duration.between(state.breakFrom, now).seconds.coerceIn(0, total)
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = done.toFloat() / total,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = accent,
                    backgroundColor = WidgetColors.accentContainer
                )
                val until = Duration.between(now, lesson.timeFrom).toMinutes().coerceAtLeast(0)
                Text("Lekcja za $until min", style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            } else if (state.isToday) {
                val until = Duration.between(now, lesson.timeFrom).toMinutes()
                if (until in 0..60) {
                    Text("Za $until min", style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Following(lesson: WidgetLesson) {
        Column(GlanceModifier.fillMaxSize()) {
            Text("POTEM", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            val color = if (lesson.isSubstitution) WidgetColors.substitution else WidgetColors.textPrimary
            Text("${lesson.number}. ${lesson.title}", style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            Text(lesson.timeRange, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            lesson.room?.let {
                Text("s. $it", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}
