package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.model.TransitCommute
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable

/**
 * Widżet "Przejazdy": najbliższe kursy - do połowy dnia lekcji dojazd do szkoły, potem powrót.
 * Dotknięcie otwiera zakładkę Przejazdy (bez zakładki na pasku - stronę główną), ikona odświeżania
 * (i komunikat bez kursów) sprawdza od razu.
 */
class TransitWidget : GlanceAppWidget() {

    // Exact: od wysokości zależy, ile kursów się zmieści.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val load: suspend () -> Pair<TransitWidgetData.State, WidgetPalette> = {
            val state = loadState(context)
            TransitWidgetData.nextTick(state, AppClock.now())?.let { WidgetDataLoader.entryPoint(context).widgetUpdater().scheduleTick(it) }
            state to WidgetDataLoader.palette(context)
        }
        val initial = loadSnapshot(load)
        provideContent {
            val (state, palette) = rememberLiveWidgetData(initial, load) { _, fresh -> fresh }
            CompositionLocalProvider(LocalWidgetPalette provides palette) { Content(state) }
        }
    }

    private suspend fun loadState(context: Context): TransitWidgetData.State = runCatchingCancellable {
        val ep = WidgetDataLoader.entryPoint(context)
        val prefs = ep.transitPreferences().preferences.first()
        val lessons = ep.transitSchoolDay().lessons.first()
        val now = AppClock.now()
        val state = TransitWidgetData.build(prefs, lessons, ep.transitJourneys().loadLast(), now)
        // W porze dojazdu nieświeży wynik odświeża się w tle (z limitem częstotliwości w odświeżaczu).
        if (state is TransitWidgetData.State.Ready && TransitCommute.dashboardDirection(lessons, now) != null)
            ep.transitWidgetRefresher().refreshInBackground()
        state
    }.getOrDefault(TransitWidgetData.State.Failed)

    @Composable
    private fun Content(state: TransitWidgetData.State) {
        when (state) {
            TransitWidgetData.State.Failed -> WidgetContainer(target = "dashboard") {
                WidgetMessage("Nie udało się odczytać danych. Otwórz aplikację i spróbuj ponownie.")
            }
            is TransitWidgetData.State.Message -> WidgetContainer(target = state.target) { WidgetMessage(state.text) }
            is TransitWidgetData.State.Ready -> WidgetContainer(target = state.target) { Ready(state) }
        }
    }

    @Composable
    private fun Ready(state: TransitWidgetData.State.Ready) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(state.title, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                // Ikona odświeżania jak w zakładce Przejazdy (wektor zamiast znaku "↻"), obszar dotyku 40 dp.
                Box(GlanceModifier.size(40.dp).clickable(actionRunCallback<TransitWidgetRefreshAction>()),
                    contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_widget_refresh), contentDescription = "Sprawdź połączenia",
                        colorFilter = ColorFilter.tint(WidgetColors.accent), modifier = GlanceModifier.size(22.dp))
                }
            }
            state.checked?.let {
                Text(it, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            if (state.rows.isEmpty()) {
                // Komunikat bez kursów sprawdza połączenia po dotknięciu (jak ikona odświeżania).
                Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionRunCallback<TransitWidgetRefreshAction>()),
                    contentAlignment = Alignment.Center) {
                    Text(state.empty.orEmpty(), style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center))
                }
            } else {
                // Tyle całych kursów, ile się mieści: nagłówek z ikoną odświeżania, "Sprawdzono" i marginesy ok. 90 dp, kurs ok. 48 dp
                // (większa czcionka - więcej). Dawniej ostatni wiersz bywał ucięty u dołu.
                val fontScale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
                val available = LocalSize.current.height.value - 90f * fontScale
                val count = (available / (48f * fontScale)).toInt().coerceIn(1, state.rows.size)
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    state.rows.take(count).forEach { RideRow(it) }
                }
            }
        }
    }

    @Composable
    private fun RideRow(row: TransitWidgetData.Row) {
        Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).semantics { contentDescription = row.description }) {
            Column(GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Box(GlanceModifier.background(WidgetColors.accentContainer).cornerRadius(6.dp).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(row.line, style = TextStyle(color = WidgetColors.onAccentContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(6.dp))
                    // Strzałka jak w karcie Przejazdów (ikona, nie znak "→").
                    Image(ImageProvider(R.drawable.ic_widget_arrow), contentDescription = null,
                        colorFilter = ColorFilter.tint(WidgetColors.textSecondary), modifier = GlanceModifier.size(14.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(row.toName, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = WidgetColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(row.until, style = TextStyle(color = WidgetColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Text(row.detail, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class TransitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransitWidget()
}
