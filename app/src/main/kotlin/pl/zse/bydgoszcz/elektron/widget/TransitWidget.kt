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
        val initial = loadSnapshot(load, WidgetDataVersion.transit)
        provideContent {
            val (state, palette) = rememberLiveWidgetData(initial, load, WidgetDataVersion.transit) { _, fresh -> fresh }
            CompositionLocalProvider(LocalWidgetPalette provides palette) { Content(state) }
        }
    }

    private suspend fun loadState(context: Context): TransitWidgetData.State = runCatchingCancellable {
        val ep = WidgetDataLoader.entryPoint(context)
        val prefs = ep.transitPreferences().preferences.first()
        val lessons = ep.transitSchoolDay().lessons.first()
        val now = AppClock.now()
        val result = ep.transitJourneys().loadLast()
        val auto = TransitWidgetData.autoRefresh(lessons, now)
        val state = TransitWidgetData.build(prefs, lessons, result, now, status = ep.transitWidgetRefresher().status(result?.fetchedAt), auto = auto)
        // W ciągu dnia nieświeży wynik odświeża się w tle sam (z limitem częstotliwości w odświeżaczu) - bez dotykania widżetu.
        if (state is TransitWidgetData.State.Ready && auto) ep.transitWidgetRefresher().refreshInBackground()
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
        val context = LocalContext.current
        // Skala czcionki z bieżących ustawień systemu: konfiguracja zasobów w długo żyjącym procesie bywa sprzed zmiany.
        val fontScale = runCatching { android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE) }
            .getOrDefault(context.resources.configuration.fontScale).coerceAtLeast(1f)
        Column(GlanceModifier.fillMaxSize()) {
            Header(state)
            Spacer(GlanceModifier.height(8.dp))
            if (state.rows.isEmpty()) {
                // Bez kursów: komunikat na środku; dotknięcie sprawdza od razu (jak ikona odświeżania).
                Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionRunCallback<TransitWidgetRefreshAction>()),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Image(ImageProvider(R.drawable.ic_map_bus), contentDescription = null,
                            colorFilter = ColorFilter.tint(WidgetColors.textSecondary), modifier = GlanceModifier.size(26.dp))
                        Spacer(GlanceModifier.height(6.dp))
                        Text(state.empty.orEmpty(), style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center))
                    }
                }
            } else {
                // Tyle całych kursów, ile się mieści. Stałe: marginesy widżetu 28 dp, nagłówek z przyciskiem 36 dp, odstęp 8 dp,
                // podtytuł ok. 15 dp x skala; kurs: odstępy 14 dp + dwie linie tekstu ok. 30 dp x skala.
                val available = LocalSize.current.height.value - (72f + 15f * fontScale)
                val count = (available / (14f + 30f * fontScale)).toInt().coerceIn(1, state.rows.size)
                // Kursy rozłożone równo na całą wysokość (bez pustego miejsca na dole), z cienką linią między nimi.
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    state.rows.take(count).forEachIndexed { index, row ->
                        if (index > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.rowSurface)) {}
                        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) { RideRow(row) }
                    }
                }
            }
        }
    }

    /** Logo, kierunek ("Ze szkoły") i okrągły przycisk odświeżania; pod spodem cel i stan sprawdzania. */
    @Composable
    private fun Header(state: TransitWidgetData.State.Ready) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(18.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(state.title, style = TextStyle(color = WidgetColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                // Cel (albo start) i stan: "Do: Dworzec Główny · sprawdzono 15:02", "· sprawdzam…", "· nie udało się odświeżyć".
                Text(state.subtitle, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            // Przycisk odświeżania: kółko w kolorze kart wierszy, obszar dotyku 36 dp.
            Box(GlanceModifier.size(36.dp).background(WidgetColors.rowSurface).cornerRadius(18.dp)
                .clickable(actionRunCallback<TransitWidgetRefreshAction>()), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_widget_refresh), contentDescription = "Sprawdź połączenia",
                    colorFilter = ColorFilter.tint(WidgetColors.accent), modifier = GlanceModifier.size(20.dp))
            }
        }
    }

    /**
     * Kurs w jednym wierszu listy: znaczek linii (jak w aplikacji), nazwa (przystanek albo - ze strzałką - przystanek
     * wysiadki lub kierunek pojazdu), pod nią szczegół ("przyjazd 15:02", "przyjazd 07:40 · zapas 15 min"),
     * po prawej "za 11 min" i godzina odjazdu.
     */
    @Composable
    private fun RideRow(row: TransitWidgetData.Row) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = row.description },
            verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(GlanceModifier.width(38.dp).background(WidgetColors.accentContainer).cornerRadius(8.dp).padding(vertical = 3.dp),
                contentAlignment = Alignment.Center) {
                Text(row.line, style = TextStyle(color = WidgetColors.onAccentContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center), maxLines = 1)
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    if (row.arrow) {
                        Image(ImageProvider(R.drawable.ic_widget_arrow), contentDescription = null,
                            colorFilter = ColorFilter.tint(WidgetColors.textSecondary), modifier = GlanceModifier.size(12.dp))
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(row.stop, style = TextStyle(color = WidgetColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                row.detail?.let {
                    Text(it, style = TextStyle(color = if (row.urgent) WidgetColors.textPrimary else WidgetColors.textSecondary, fontSize = 11.sp,
                        fontWeight = if (row.urgent) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(row.until, style = TextStyle(color = WidgetColors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(row.time, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class TransitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransitWidget()
}
