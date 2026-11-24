package pl.zse.bydgoszcz.elektron.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.unit.ColorProvider
import org.json.JSONArray
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.ElektronActivity
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.model.LessonLinks
import pl.zse.bydgoszcz.elektron.domain.model.LessonTarget
import pl.zse.bydgoszcz.elektron.domain.model.Rooms
import pl.zse.bydgoszcz.elektron.domain.util.AppClock

/**
 * Widżet Plan dnia bez Glance - na Androidzie 17+. Lista lekcji Glance (`LazyColumn`, wiersze dołączone do widoku
 * jako `RemoteCollectionItems`) była tam pusta: nagłówek tak, lekcji nie (Xiaomi z HyperOS, także po Glance 1.2.0).
 * Tu lista przewija się palcem jak dotąd, ale wiersze launcher pobiera sam z usługi aplikacji ([DayPlanListService],
 * klasyczny `RemoteViewsService` - działa od Androida 3, np. w widżetach kalendarzy). Taka lista musi być w widoku
 * głównym widżetu - osadzona w Glance (`AndroidRemoteViews`) launcher ją odrzuca ("setRemoteAdapter can only be used
 * for AppWidgets"), stąd cały widżet bez Glance. Na starszych Androidach zostaje [DayPlanWidget].
 *
 * Ten sam odbiornik ([DayPlanWidgetReceiver]) i te same dane ([WidgetDataLoader]); wygląd jak w wersji Glance.
 */
internal object DayPlanClassic {
    /** Android 17+. Na starszym tylko po wymuszeniu do sprawdzania (DEWELOPER.md: prefs "widget_dev"). */
    fun enabled(context: Context): Boolean = Build.VERSION.SDK_INT >= 37 ||
        context.getSharedPreferences("widget_dev", Context.MODE_PRIVATE).getBoolean("day_plan_classic", false)

    const val ACTION_TOGGLE_EARLIER = "pl.zse.bydgoszcz.elektron.widget.DAY_PLAN_EARLIER"
    private const val EXTRA_KEY = "day_plan_earlier_key"
    private const val PREFS = "widget_day_plan_classic"
    private const val TAG = "DayPlanWidget"

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, DayPlanWidgetReceiver::class.java)) ?: IntArray(0)

    /** Wszystkie widżety Plan dnia: dane z bazy, widok, wiersze dla usługi i alarm następnego odświeżenia. */
    suspend fun updateAll(context: Context, ids: IntArray = widgetIds(context)) {
        if (ids.isEmpty()) return
        val state = WidgetDataLoader.load(context)
        widgetTickAt(state, AppClock.now())?.let { WidgetDataLoader.entryPoint(context).widgetUpdater().scheduleTick(it) }
        val palette = WidgetDataLoader.palette(context)
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (id in ids) runCatching {
            manager.updateAppWidget(id, build(context, id, state, palette, manager))
            manager.notifyAppWidgetViewDataChanged(id, R.id.day_plan_list)
        }.onFailure { Log.w(TAG, "Nie udało się narysować widżetu Plan dnia $id", it) }
    }

    /** Dotknięcie "N lekcji za Tobą": pokaż albo schowaj minione lekcje (tylko dla tej bieżącej lekcji). */
    suspend fun toggleEarlier(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply { if (prefs.getString("expanded_$id", null) == key) remove("expanded_$id") else putString("expanded_$id", key) }.apply()
        updateAll(context, intArrayOf(id))
    }

    fun forget(context: Context, ids: IntArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { ids.forEach { remove("expanded_$it") } }.apply()
        DayPlanRowStore.remove(context, ids)
    }

    private fun build(context: Context, id: Int, state: WidgetState, palette: WidgetPalette, manager: AppWidgetManager): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_plan_classic)
        views.tintImage(R.id.day_plan_bg, palette.background.dayNight(context))
        val timetable = sectionIntent(context, id, "timetable")
        views.setOnClickPendingIntent(R.id.day_plan_root, timetable)
        views.setOnClickPendingIntent(R.id.day_plan_header, timetable)
        views.color(R.id.day_plan_label, palette.textPrimary.dayNight(context))
        views.color(R.id.day_plan_class, palette.textSecondary.dayNight(context))
        views.color(R.id.day_plan_message, palette.textSecondary.dayNight(context))
        // Adapter zawsze (także przy komunikacie) - launcher wiąże listę z usługą raz na widok.
        views.bindList(context, id)
        val ready = state as? WidgetState.Ready
        // Chwilowy błąd odczytu: ostatnie wiersze zostają (jak retainWidgetData w wersji Glance), nie sam komunikat.
        if (state == WidgetState.Failed && DayPlanRowStore.load(context, id).isNotEmpty()) {
            views.setTextViewText(R.id.day_plan_label, "Błąd odczytu · zapisany plan")
            views.setViewVisibility(R.id.day_plan_class, View.GONE)
            views.setViewVisibility(R.id.day_plan_day_end, View.GONE)
            views.setViewVisibility(R.id.day_plan_past, View.GONE)
            views.setViewVisibility(R.id.day_plan_list, View.VISIBLE)
            views.setViewVisibility(R.id.day_plan_message, View.GONE)
            return views
        }
        if (ready == null) {
            views.setTextViewText(R.id.day_plan_label, "Plan")
            views.setViewVisibility(R.id.day_plan_class, View.GONE)
            views.setViewVisibility(R.id.day_plan_day_end, View.GONE)
            views.setViewVisibility(R.id.day_plan_past, View.GONE)
            views.setViewVisibility(R.id.day_plan_list, View.GONE)
            views.setViewVisibility(R.id.day_plan_message, View.VISIBLE)
            views.setTextViewText(R.id.day_plan_message, when (state) {
                WidgetState.NoClass -> "Otwórz eLektron i wybierz klasę."
                is WidgetState.NoLessons -> "Brak lekcji w najbliższych dniach."
                else -> "Nie udało się odczytać danych. Otwórz aplikację i spróbuj ponownie."
            })
            DayPlanRowStore.save(context, id, emptyList())
            return views
        }
        views.setViewVisibility(R.id.day_plan_list, View.VISIBLE)
        views.setViewVisibility(R.id.day_plan_message, View.GONE)
        views.setTextViewText(R.id.day_plan_label, if (ready.readError) "Błąd odczytu · zapisany plan" else "Plan · ${ready.dayLabel}")
        views.setContentDescription(R.id.day_plan_header, "Plan, ${ready.dayLabel}. Otwórz zakładkę Plan")
        ready.className?.let { views.setTextViewText(R.id.day_plan_class, it); views.setViewVisibility(R.id.day_plan_class, View.VISIBLE) }
            ?: views.setViewVisibility(R.id.day_plan_class, View.GONE)
        val dayEnd = widgetDayEnd(ready, AppClock.time())
        if (dayEnd != null) {
            views.setTextViewText(R.id.day_plan_day_end, dayEnd)
            views.color(R.id.day_plan_day_end, palette.accent.dayNight(context))
            views.setViewVisibility(R.id.day_plan_day_end, View.VISIBLE)
        } else views.setViewVisibility(R.id.day_plan_day_end, View.GONE)

        // Minione lekcje dziś schowane, jak w wersji Glance (licznik "za Tobą" je pokazuje).
        val startIndex = WidgetNoteLayout.dayPlanStart(ready)
        val expandKey = "${ready.date}|$startIndex"
        val expanded = startIndex > 0 &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("expanded_$id", null) == expandKey
        val visible = if (expanded) ready.lessons else ready.lessons.drop(startIndex)
        if (startIndex > 0) {
            views.setTextViewText(R.id.day_plan_past, if (expanded) "Ukryj $startIndex ${lessonsWord(startIndex)} za Tobą"
                else "$startIndex ${lessonsWord(startIndex)} za Tobą · Pokaż")
            views.setContentDescription(R.id.day_plan_past, if (expanded) "Ukryj wcześniejsze lekcje" else "Pokaż wcześniejsze lekcje")
            views.color(R.id.day_plan_past, palette.textSecondary.dayNight(context))
            views.setViewVisibility(R.id.day_plan_past, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.day_plan_past, PendingIntent.getBroadcast(context, TOGGLE_REQUEST + id,
                Intent(context, DayPlanWidgetReceiver::class.java).setAction(ACTION_TOGGLE_EARLIER)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_KEY, expandKey),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else views.setViewVisibility(R.id.day_plan_past, View.GONE)

        val offset = ready.lessons.size - visible.size
        val height = manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.toFloat()?.takeIf { it > 0f } ?: 180f
        val scale = context.resources.configuration.fontScale
        val now = AppClock.time().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val rows = visible.mapIndexed { i, lesson ->
            row(context, palette, ready, lesson, WidgetTargets.lesson(ready, offset + i),
                WidgetNoteLayout.dayPlanLines(height, visible.size, lesson.note != null, scale),
                dayPlanRowId(lesson.number, java.util.Objects.hash(ready.date, lesson, palette, lesson.number == ready.focus.number,
                    ready.isToday && lesson.timeTo <= now, ready.isToday && ready.focusIsNow, now)))
        }
        // Zapis przed wysłaniem widoku: usługa działa w procesie aplikacji i od razu widzi nowe wiersze.
        DayPlanRowStore.save(context, id, rows)
        Log.i(TAG, "Rysuję ${rows.size} z ${ready.lessons.size} lekcji (bez Glance): " +
            visible.indices.joinToString { i -> WidgetTargets.lesson(ready, offset + i)?.let { "${it.date}/${it.lessonNumber}" } ?: "-" })
        return views
    }

    private fun RemoteViews.bindList(context: Context, id: Int) {
        val adapter = Intent(context, DayPlanListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            // Osobna lista (i fabryka) dla każdego widżetu - system rozróżnia adaptery po danych intencji.
            .setData(Uri.parse("elektron-widget://day-plan/$id"))
        @Suppress("DEPRECATION")
        setRemoteAdapter(R.id.day_plan_list, adapter)
        // Szablon dotknięcia wiersza: aktywność aplikacji; wiersz dopisuje lekcję (dane i extra) albo sekcję.
        setPendingIntentTemplate(R.id.day_plan_list, PendingIntent.getActivity(context, TEMPLATE_REQUEST + id,
            Intent(context, ElektronActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
    }

    /** Otwarcie sekcji aplikacji - ten sam extra co akcje Glance (`ShortcutKey` w WidgetCommon). */
    private fun sectionIntent(context: Context, id: Int, section: String): PendingIntent =
        PendingIntent.getActivity(context, SECTION_REQUEST + id,
            Intent(context, ElektronActivity::class.java).putExtra(SHORTCUT_EXTRA, section)
                .setData(Uri.parse("elektron-widget://section/$section/$id")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Wiersz - te same treści, kolory i cel dotknięcia co LessonRow w DayPlanWidget. */
    private fun row(context: Context, palette: WidgetPalette, state: WidgetState.Ready, lesson: WidgetLesson,
        target: LessonTarget?, userNoteLines: Int, rowId: Long): DayPlanRow {
        val highlighted = lesson.number == state.focus.number
        val past = state.isToday && lesson.timeTo <= AppClock.time()
        val remaining = if (highlighted) widgetTimeStatus(state, AppClock.time())?.replaceFirstChar { it.lowercase() } else null
        fun c(provider: ColorProvider) = provider.dayNight(context)
        val number = when {
            past -> palette.textFaded
            lesson.isSubstitution -> palette.substitution
            else -> lesson.color?.let { ColorProvider(Color(it)) } ?: palette.accent
        }
        val title = when {
            past -> palette.textFaded
            lesson.isSubstitution -> palette.substitution
            highlighted -> palette.onAccentContainer
            else -> palette.textPrimary
        }
        val background = when {
            !highlighted -> palette.transparent
            lesson.isSubstitution -> palette.substitutionContainer
            else -> palette.accentContainer
        }
        return DayPlanRow(rowId, "${lesson.number}", lesson.title,
            listOfNotNull(remaining ?: lesson.timeRange, lesson.room?.let { Rooms.label(it) }).joinToString(" · "),
            lesson.note, lesson.userNote?.let { "Notatka: $it" }, userNoteLines,
            c(number), c(title), c(if (past) palette.textFaded else palette.textSecondary), c(palette.substitution),
            c(palette.textPrimary), c(background),
            listOfNotNull("${state.dayLabel}, lekcja ${lesson.number}", lesson.title, lesson.timeRange,
                lesson.room?.let { "sala $it" }, lesson.note, lesson.userNote?.let { "Notatka: $it" }).joinToString(", ") +
                ". Otwórz szczegóły lekcji",
            target?.let { LessonLinks.uri(it) }, target?.let { LessonLinks.deepLink(it) })
    }

    /** Widok wiersza dla usługi (kolory dzień/noc - launcher wybiera według motywu, jak przy Glance). */
    fun rowView(context: Context, row: DayPlanRow): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_day_plan_row).apply {
            setTextViewText(R.id.day_plan_number, row.number)
            setTextViewText(R.id.day_plan_title, row.title)
            setTextViewText(R.id.day_plan_meta, row.meta)
            color(R.id.day_plan_number, row.numberColor)
            color(R.id.day_plan_title, row.titleColor)
            color(R.id.day_plan_meta, row.metaColor)
            if (row.note != null) {
                setViewVisibility(R.id.day_plan_note, View.VISIBLE)
                setTextViewText(R.id.day_plan_note, row.note)
                color(R.id.day_plan_note, row.noteColor)
            } else setViewVisibility(R.id.day_plan_note, View.GONE)
            if (row.userNote != null && row.userNoteLines > 0) {
                setViewVisibility(R.id.day_plan_user_note, View.VISIBLE)
                setTextViewText(R.id.day_plan_user_note, row.userNote)
                setInt(R.id.day_plan_user_note, "setMaxLines", row.userNoteLines)
                color(R.id.day_plan_user_note, row.userNoteColor)
            } else setViewVisibility(R.id.day_plan_user_note, View.GONE)
            // Bez podświetlenia tło przezroczyste - obrazek ukryty.
            if (android.graphics.Color.alpha(row.background.day) == 0 && android.graphics.Color.alpha(row.background.night) == 0)
                setViewVisibility(R.id.day_plan_row_bg, View.GONE)
            else { setViewVisibility(R.id.day_plan_row_bg, View.VISIBLE); tintImage(R.id.day_plan_row_bg, row.background) }
            setContentDescription(R.id.day_plan_row, row.description)
            setOnClickFillInIntent(R.id.day_plan_row, Intent().apply {
                if (row.lessonUri != null && row.lessonLink != null) {
                    data = Uri.parse(row.lessonUri)
                    putExtra(LessonLinks.EXTRA_LESSON, row.lessonLink)
                } else putExtra(SHORTCUT_EXTRA, "timetable")
            })
        }

    /**
     * Kolor i przezroczystość obrazka tła: setColorFilter (bez kanału alfa) i setImageAlpha. Dawniej tint tła widoku
     * (setBackgroundTintList) - launcher Xiaomi na Androidzie 17 go pomijał (biały widżet, bez przezroczystości
     * i bez trybu ciemnego). Krycie bierzemy z koloru dziennego (Ustawienia -> Widżety, takie samo w obu trybach).
     */
    private fun RemoteViews.tintImage(viewId: Int, color: DayNight) {
        val opaqueDay = color.day or (0xFF shl 24)
        val opaqueNight = color.night or (0xFF shl 24)
        if (Build.VERSION.SDK_INT >= 31) setColorInt(viewId, "setColorFilter", opaqueDay, opaqueNight)
        else setInt(viewId, "setColorFilter", opaqueDay)
        setInt(viewId, "setImageAlpha", maxOf(android.graphics.Color.alpha(color.day), android.graphics.Color.alpha(color.night)))
    }

    private fun RemoteViews.color(viewId: Int, color: DayNight) {
        if (Build.VERSION.SDK_INT >= 31) setColorInt(viewId, "setTextColor", color.day, color.night)
        else setTextColor(viewId, color.day)
    }

    /** Kolor w jasnym i ciemnym motywie (publiczne getColor z kontekstem w danym trybie - launcher wybiera sam). */
    private fun ColorProvider.dayNight(context: Context): DayNight =
        DayNight(getColor(modeContext(context, night = false)).toArgb(), getColor(modeContext(context, night = true)).toArgb())

    private fun modeContext(context: Context, night: Boolean): Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(config)
    }

    private const val SHORTCUT_EXTRA = "elektron_shortcut"
    private const val TEMPLATE_REQUEST = 70_000
    private const val SECTION_REQUEST = 71_000
    private const val TOGGLE_REQUEST = 72_000
}

/** Kolor ARGB dla jasnego i ciemnego motywu. */
internal data class DayNight(val day: Int, val night: Int)

/** Wiersz listy Plan dnia gotowy do wyświetlenia przez usługę. */
internal data class DayPlanRow(
    val id: Long,
    val number: String,
    val title: String,
    val meta: String,
    val note: String?,
    val userNote: String?,
    val userNoteLines: Int,
    val numberColor: DayNight,
    val titleColor: DayNight,
    val metaColor: DayNight,
    val noteColor: DayNight,
    val userNoteColor: DayNight,
    val background: DayNight,
    val description: String,
    /** Cel dotknięcia (LessonLinks.uri / deepLink); null - zakładka Plan. */
    val lessonUri: String?,
    val lessonLink: String?
)

/** Wiersze każdego widżetu Plan dnia (JSON w SharedPreferences - przetrwają zamknięcie procesu). */
internal object DayPlanRowStore {
    private const val PREFS = "widget_day_plan_rows"

    fun save(context: Context, appWidgetId: Int, rows: List<DayPlanRow>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(appWidgetId), encode(rows)).apply()
    }

    fun load(context: Context, appWidgetId: Int): List<DayPlanRow> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(appWidgetId), null)
            ?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()

    fun remove(context: Context, appWidgetIds: IntArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { appWidgetIds.forEach { remove(key(it)) } }.apply()
    }

    private fun key(appWidgetId: Int) = "rows_$appWidgetId"

    internal fun encode(rows: List<DayPlanRow>): String = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().put("id", r.id).put("number", r.number).put("title", r.title).put("meta", r.meta)
                .put("note", r.note ?: JSONObject.NULL).put("userNote", r.userNote ?: JSONObject.NULL).put("userNoteLines", r.userNoteLines)
                .put("numberColor", pair(r.numberColor)).put("titleColor", pair(r.titleColor)).put("metaColor", pair(r.metaColor))
                .put("noteColor", pair(r.noteColor)).put("userNoteColor", pair(r.userNoteColor)).put("background", pair(r.background))
                .put("description", r.description).put("lessonUri", r.lessonUri ?: JSONObject.NULL)
                .put("lessonLink", r.lessonLink ?: JSONObject.NULL))
        }
    }.toString()

    internal fun decode(text: String): List<DayPlanRow> {
        val array = JSONArray(text)
        fun JSONObject.optText(name: String) = if (isNull(name)) null else optString(name)
        return (0 until minOf(array.length(), 20)).map { i ->
            val o = array.getJSONObject(i)
            DayPlanRow(o.getLong("id"), o.getString("number"), o.getString("title"), o.getString("meta"),
                o.optText("note"), o.optText("userNote"), o.optInt("userNoteLines"),
                dayNight(o, "numberColor"), dayNight(o, "titleColor"), dayNight(o, "metaColor"),
                dayNight(o, "noteColor"), dayNight(o, "userNoteColor"), dayNight(o, "background"),
                o.getString("description"), o.optText("lessonUri"), o.optText("lessonLink"))
        }
    }

    private fun pair(c: DayNight) = JSONArray().put(c.day).put(c.night)
    private fun dayNight(o: JSONObject, name: String) = o.getJSONArray(name).let { DayNight(it.getInt(0), it.getInt(1)) }
}

/** Usługa, z której launcher pobiera wiersze listy Plan dnia (Android 17+, patrz [DayPlanClassic]). */
class DayPlanListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val appWidgetId: Int) : RemoteViewsFactory {
        private var rows: List<DayPlanRow> = emptyList()
        override fun onCreate() { rows = DayPlanRowStore.load(context, appWidgetId) }
        override fun onDataSetChanged() { rows = DayPlanRowStore.load(context, appWidgetId) }
        override fun onDestroy() { rows = emptyList() }
        override fun getCount(): Int = rows.size
        override fun getViewAt(position: Int): RemoteViews =
            rows.getOrNull(position)?.let { DayPlanClassic.rowView(context, it) } ?: RemoteViews(context.packageName, R.layout.widget_day_plan_row)
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
