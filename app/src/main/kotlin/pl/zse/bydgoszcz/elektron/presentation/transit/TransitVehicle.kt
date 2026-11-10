package pl.zse.bydgoszcz.elektron.presentation.transit

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.model.VehicleTrack

/**
 * Gdzie jest teraz autobus albo tramwaj kursu (po dotknięciu karty w Przejazdach): mapa z pojazdem, trasą
 * po ulicach i Twoim przystankiem oraz ile przystanków zostało. Dane z BUSearch, odświeżane co kilka sekund,
 * dopóki okno jest otwarte. Pełnoekranowe okno zamiast wysuwanego arkusza: przesuwanie i przybliżanie mapy
 * nie zsuwa już okna (dawniej gest mapy przejmował arkusz).
 */
@Composable
internal fun VehicleSheet(viewModel: TransitViewModel) {
    val view by viewModel.vehicle.collectAsStateWithLifecycle()
    val current = view ?: return
    DisposableEffect(Unit) { onDispose { viewModel.closeVehicle() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = viewModel::closeVehicle,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val track = current.track
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(current.line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (track?.position?.tram == true) "Tramwaj" else if (track != null) "Autobus" else "Pojazd",
                        style = MaterialTheme.typography.titleMedium)
                    track?.position?.destination?.let {
                        Text("Kierunek: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = viewModel::closeVehicle) { Icon(Icons.Filled.Close, "Zamknij") }
            }
            when {
                current.vehicleId == null -> Text("BUSearch jeszcze nie śledzi tego kursu. Pozycja pojawi się zwykle kilkanaście minut przed odjazdem.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                track == null && current.failed -> Text("Nie udało się pobrać pozycji pojazdu. Spróbuję ponownie za chwilę.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                track == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    Text(summary(track, current.platformId), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.fillMaxWidth().weight(1f)) { VehicleMap(track, current.platformId, current.shape) }
                }
            }
        }
      }
    }
}

/** Ile przystanków do Twojego i za ile (albo że pojazd go minął). */
private fun summary(track: VehicleTrack, platformId: String): String {
    val towards = track.towards(platformId)
    val next = track.stops.firstOrNull { !it.passed }
    return when {
        towards == null -> next?.let { "Następny przystanek: ${it.name}" } ?: "Pojazd jest na trasie."
        towards.second < 0 -> "Pojazd minął już Twój przystanek (${towards.first.name})."
        towards.second == 0 -> "Następny przystanek to Twój: ${towards.first.name}" + (towards.first.etaMinutes?.let { " · za ok. $it min" } ?: "")
        else -> "Do Twojego przystanku (${towards.first.name}): ${stopsText(towards.second)}" +
            (towards.first.etaMinutes?.let { " · ok. $it min" } ?: "")
    }
}

/** "1 przystanek", "3 przystanki", "5 przystanków". */
private fun stopsText(n: Int): String = "$n " + when {
    n == 1 -> "przystanek"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "przystanki"
    else -> "przystanków"
}

/** Mapa: trasa po ulicach (przejechana część przygaszona), przystanki, Twój przystanek (pinezka z nazwą) i pojazd. */
@Composable
private fun VehicleMap(track: VehicleTrack, platformId: String, shape: List<Pair<Double, Double>>?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val start = GeoPoint(track.position.latitude, track.position.longitude)
    val map = remember(context) { createOsmMap(context, start, zoom = 15.0) }
    BindMapLifecycle(map)
    val overlay = remember(context, density.density) { VehicleOverlay(context, density.density) }
    DisposableEffect(map, overlay) { map.overlays.add(overlay); onDispose { map.overlays.remove(overlay) } }
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(track, shape, colors.primary) {
        overlay.track = track; overlay.platformId = platformId; overlay.shape = shape
        overlay.primary = colors.primary.toArgb(); overlay.onPrimary = colors.onPrimary.toArgb()
        overlay.muted = colors.outline.toArgb(); overlay.surface = colors.surface.toArgb(); overlay.onSurface = colors.onSurface.toArgb()
        overlay.accent = colors.tertiary.toArgb(); overlay.onAccent = colors.onTertiary.toArgb()
        // Pierwszy raz: pojazd i Twój przystanek na jednym widoku; potem mapa zostaje tam, gdzie ją przesunąłeś.
        if (!fitted) {
            fitted = true
            val stop = track.stops.firstOrNull { it.id == platformId }
            map.post {
                if (stop != null && !stop.passed) {
                    val box = BoundingBox.fromGeoPoints(listOf(start, GeoPoint(stop.latitude, stop.longitude)))
                    map.zoomToBoundingBox(box.increaseByScale(1.8f), false)
                    if (map.zoomLevelDouble > 16.5) map.controller.setZoom(16.5)
                } else map.controller.setCenter(start)
            }
        }
        map.invalidate()
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).semantics {
        contentDescription = "Mapa z pozycją pojazdu linii ${track.position.line}"
    }) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp,
            color = colors.surface) {
            IconButton(onClick = { map.controller.animateTo(GeoPoint(track.position.latitude, track.position.longitude)) }) {
                Icon(Icons.Filled.MyLocation, "Pokaż pojazd", tint = colors.onSurface)
            }
        }
    }
}

/** Rysowanie na mapie osmdroid: trasa, przystanki, Twój przystanek i pojazd. */
private class VehicleOverlay(context: android.content.Context, private val dp: Float) : Overlay() {
    var track: VehicleTrack? = null
    var platformId: String = ""
    var shape: List<Pair<Double, Double>>? = null
    var primary = 0; var onPrimary = 0; var muted = 0; var surface = 0; var onSurface = 0; var accent = 0; var onAccent = 0
    private val bus = ContextCompat.getDrawable(context, R.drawable.ic_map_bus)!!.mutate()
    private val tram = ContextCompat.getDrawable(context, R.drawable.ic_map_tram)!!.mutate()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val data = track ?: return
        val projection = mapView.projection
        fun at(lat: Double, lon: Double): Point { projection.toPixels(GeoPoint(lat, lon), point); return Point(point) }
        val vehicle = at(data.position.latitude, data.position.longitude)
        // Trasa: przebieg z planera (po ulicach i torach) albo - bez niego - odcinki między przystankami.
        val route = shape?.map { at(it.first, it.second) } ?: data.stops.map { at(it.latitude, it.longitude) }
        val split = route.indices.minByOrNull { i -> val p = route[i]; (p.x - vehicle.x).toLong().let { it * it } + (p.y - vehicle.y).toLong().let { it * it } } ?: 0
        fun path(points: List<Point>) = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat()) } }
        line.strokeWidth = 5 * dp
        line.color = muted; line.alpha = 140
        canvas.drawPath(path(route.take(split + 1) + vehicle), line)
        line.color = primary; line.alpha = 230
        canvas.drawPath(path(listOf(vehicle) + route.drop(split + 1)), line)
        // Przystanki: małe kropki (minięte przygaszone).
        data.stops.forEach { stop ->
            val p = at(stop.latitude, stop.longitude)
            fill.color = surface; canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 3.5f * dp, fill)
            ring.color = if (stop.passed) muted else primary; ring.strokeWidth = 2 * dp
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 3.5f * dp, ring)
        }
        // Twój przystanek: pinezka z nazwą.
        data.stops.firstOrNull { it.id == platformId }?.let { stop -> drawPin(canvas, at(stop.latitude, stop.longitude), stop.name) }
        drawVehicle(canvas, vehicle, data)
    }

    /** Pinezka (kropla) w kolorze uzupełniającym z białym środkiem i nazwą przystanku w dymku nad nią. */
    private fun drawPin(canvas: Canvas, p: Point, name: String) {
        val x = p.x.toFloat(); val y = p.y.toFloat()
        val r = 9 * dp
        val pin = Path().apply {
            moveTo(x, y)
            cubicTo(x - r * 0.4f, y - r * 0.9f, x - r, y - r * 1.3f, x - r, y - r * 2.1f)
            arcTo(RectF(x - r, y - r * 3.1f, x + r, y - r * 1.1f), 180f, 180f)
            cubicTo(x + r, y - r * 1.3f, x + r * 0.4f, y - r * 0.9f, x, y)
            close()
        }
        fill.color = accent; fill.setShadowLayer(3 * dp, 0f, 1 * dp, 0x55000000)
        canvas.drawPath(pin, fill); fill.clearShadowLayer()
        fill.color = onAccent; canvas.drawCircle(x, y - r * 2.1f, r * 0.42f, fill)
        // Nazwa w dymku.
        text.textSize = 12 * dp; text.textAlign = Paint.Align.CENTER
        val w = text.measureText(name) + 14 * dp
        val top = y - r * 3.1f - 8 * dp - 22 * dp
        val bubble = RectF(x - w / 2, top, x + w / 2, top + 22 * dp)
        fill.color = surface; fill.setShadowLayer(3 * dp, 0f, 1 * dp, 0x44000000)
        canvas.drawRoundRect(bubble, 11 * dp, 11 * dp, fill); fill.clearShadowLayer()
        text.color = onSurface
        canvas.drawText(name, x, bubble.centerY() - (text.descent() + text.ascent()) / 2, text)
    }

    /** Pojazd: zaokrąglona plakietka z ikoną autobusu/tramwaju i numerem linii oraz strzałka kierunku jazdy. */
    private fun drawVehicle(canvas: Canvas, p: Point, data: VehicleTrack) {
        val x = p.x.toFloat(); val y = p.y.toFloat()
        val label = data.position.line
        text.textSize = 13 * dp; text.textAlign = Paint.Align.LEFT
        val icon = 16 * dp
        val height = 28 * dp
        val width = 10 * dp + icon + 4 * dp + text.measureText(label) + 10 * dp
        // Strzałka kierunku (gdy znany) - pod plakietką, obrócona zgodnie z kierunkiem jazdy.
        data.position.bearing?.let { bearing ->
            canvas.save(); canvas.rotate(bearing.toFloat(), x, y)
            val arrow = Path().apply { moveTo(x, y - height / 2 - 9 * dp); lineTo(x - 7 * dp, y - height / 2 + 1 * dp); lineTo(x + 7 * dp, y - height / 2 + 1 * dp); close() }
            fill.color = primary; canvas.drawPath(arrow, fill)
            canvas.restore()
        }
        val body = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
        fill.color = primary; fill.setShadowLayer(4 * dp, 0f, 1.5f * dp, 0x66000000)
        canvas.drawRoundRect(body, height / 2, height / 2, fill); fill.clearShadowLayer()
        ring.color = surface; ring.strokeWidth = 2 * dp
        canvas.drawRoundRect(body, height / 2, height / 2, ring)
        val drawable = if (data.position.tram) tram else bus
        val left = (body.left + 10 * dp).toInt(); val top = (y - icon / 2).toInt()
        drawable.setBounds(left, top, left + icon.toInt(), top + icon.toInt())
        drawable.setTint(onPrimary); drawable.draw(canvas)
        text.color = onPrimary
        canvas.drawText(label, body.left + 10 * dp + icon + 4 * dp, y - (text.descent() + text.ascent()) / 2, text)
    }
}
