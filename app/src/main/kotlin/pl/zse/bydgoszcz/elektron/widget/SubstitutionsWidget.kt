package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import pl.zse.bydgoszcz.elektron.R

/**
 * Widżet "Zastępstwa": nadchodzące zastępstwa klasy i grup użytkownika.
 * Dotknięcie wiersza otwiera tę lekcję w planie (szczegóły), reszty widżetu - stronę główną.
 * Odświeżany po każdym syncu i pushu.
 */
class SubstitutionsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val load: suspend () -> Pair<SubsWidgetState, WidgetPalette> = {
            WidgetDataLoader.loadSubstitutions(context) to WidgetDataLoader.palette(context)
        }
        val initial = loadSnapshot(load)
        provideContent {
            val (state, palette) = rememberLiveWidgetData(initial, load)
            CompositionLocalProvider(LocalWidgetPalette provides palette) { Content(state) }
        }
    }

    @Composable
    private fun Content(state: SubsWidgetState) {
        WidgetContainer {
            when (state) {
                SubsWidgetState.NoClass -> WidgetMessage("Otwórz eLektron i wybierz klasę.")
                is SubsWidgetState.Ready -> Subs(state)
            }
        }
    }

    @Composable
    private fun Subs(state: SubsWidgetState.Ready) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text("Zastępstwa", modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                state.className?.let {
                    Text(it, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (state.items.isEmpty()) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Text("Brak zastępstw dla Twojej klasy.",
                        style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center))
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(state.items, itemId = { (it.dayLabel + it.lessonNumber + it.title).hashCode().toLong() }) { sub ->
                        SubRow(sub)
                    }
                }
            }
        }
    }

    @Composable
    private fun SubRow(sub: WidgetSubstitution) {
        // Dotknięcie: plan na ten dzień i szczegóły lekcji. Opis dla czytnika ekranu zawiera
        // treść wiersza (opis kontenera zastępuje odczyt tekstów w środku).
        val description = buildString {
            append("Lekcja ${sub.lessonNumber}, ${sub.title}, ${sub.dayLabel}, ${sub.detail}")
            sub.note?.let { append(", $it") }
            append(". Otwórz szczegóły lekcji")
        }
        Column(
            GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)
                .clickable(openLessonAction(LocalContext.current, sub.target))
                .semantics { contentDescription = description }
        ) {
            Row(
                // Wyższy wiersz (7 dp) - obszar dotyku ok. 48 dp.
                GlanceModifier.fillMaxWidth().background(WidgetColors.substitutionContainer).cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text("${sub.lessonNumber}", modifier = GlanceModifier.width(22.dp),
                    style = TextStyle(color = WidgetColors.substitution, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Column(GlanceModifier.defaultWeight()) {
                    Text(sub.title, style = TextStyle(color = WidgetColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text("${sub.dayLabel} · ${sub.detail}",
                        style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.sp), maxLines = 1)
                    sub.note?.let {
                        Text(it, style = TextStyle(color = WidgetColors.substitution, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}

class SubstitutionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubstitutionsWidget()
}
