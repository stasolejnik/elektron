package pl.zse.bydgoszcz.elektron.presentation.transit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import kotlin.math.hypot

/** One overlay instead of a native Marker and info window for every stop. */
internal class TransitStopOverlay(
    private val density: Float,
    textSize: Float,
    private val onSelect: (TransitDestination) -> Unit
) : Overlay() {
    private data class StopPoint(val stop: TransitDestination, val point: GeoPoint)
    private var stopPoints: List<StopPoint> = emptyList()
    var stops: List<TransitDestination> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            stopPoints = value.map { StopPoint(it, GeoPoint(it.latitude, it.longitude)) }
            labelWidths.clear()
        }
    var selectedKey: String? = null
    var bottomInset: Float = 0f
    var primary: Int = Color.rgb(30, 80, 170)
    var foreground: Int = Color.WHITE
    var surface: Int = Color.WHITE
    var textColor: Int = Color.BLACK
    private val schoolPoint = GeoPoint(SchoolTransit.LATITUDE, SchoolTransit.LONGITUDE)
    private val schoolRoof = android.graphics.Path().apply { moveTo(-7f, -2f); lineTo(0f, -8f); lineTo(7f, -2f); close() }
    private val labelWidths = mutableMapOf<String, Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.textSize = textSize }
    private data class Pin(val stop: TransitDestination?, val x: Float, val y: Float) {
        val name get() = stop?.name ?: "Szkoła"
    }

    private fun pins(map: MapView): List<Pin> {
        val point = Point()
        val radius = 22 * density
        return buildList {
            map.projection.toPixels(schoolPoint, point)
            add(Pin(null, point.x.toFloat(), point.y.toFloat()))
            stopPoints.forEach { (stop, position) ->
                map.projection.toPixels(position, point)
                if (point.x >= -radius && point.x <= map.width + radius && point.y >= -radius && point.y <= map.height + radius)
                    add(Pin(stop, point.x.toFloat(), point.y.toFloat()))
            }
        }
    }

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val pins = pins(map)
        pins.forEach { pin -> drawPin(canvas, pin) }
        // Names appear after a small zoom from the initial 14.0 level.
        val labelPins = if (map.zoomLevelDouble < 14.8) pins.filter { it.stop == null } else pins
        val margin = 6 * density
        val viewport = MapLabelRect(margin, margin, map.width - margin, map.height - margin)
        val occupied = pins.mapTo(mutableListOf()) {
            val radius = if (it.stop == null || it.stop.key == selectedKey) 12 * density else 9 * density
            MapLabelRect(it.x - radius, it.y - radius, it.x + radius, it.y + radius)
        }
        // Keep labels away from the school button, selection card, attribution and zoom controls.
        occupied += MapLabelRect(0f, 0f, 64 * density, 64 * density)
        occupied += MapLabelRect(0f, map.height - bottomInset - 40 * density, map.width.toFloat(), map.height.toFloat())
        occupied += MapLabelRect(map.width - 64 * density, 0f, map.width.toFloat(), 120 * density)
        val centerX = map.width / 2f; val centerY = map.height / 2f
        val ordered = labelPins.sortedWith(compareBy<Pin> { if (it.stop?.key == selectedKey) 0 else if (it.stop == null) 1 else 2 }
            .thenBy { hypot(it.x - centerX, it.y - centerY) })
        val metrics = text.fontMetrics
        val height = metrics.descent - metrics.ascent + 8 * density
        ordered.forEach { pin ->
            val width = labelWidths.getOrPut(pin.name) { text.measureText(pin.name) + 12 * density }
            val rect = placeMapLabel(pin.x, pin.y, width, height, 13 * density, viewport, occupied) ?: return@forEach
            paint.style = Paint.Style.FILL; paint.color = surface
            canvas.drawRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom), 5 * density, 5 * density, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = primary
            canvas.drawRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom), 5 * density, 5 * density, paint)
            paint.style = Paint.Style.FILL
            text.color = textColor
            canvas.drawText(pin.name, rect.left + 6 * density, rect.top + 4 * density - metrics.ascent, text)
            // Include padding so adjacent labels remain visually separate.
            occupied += MapLabelRect(rect.left - margin, rect.top - margin, rect.right + margin, rect.bottom + margin)
        }
    }

    private fun drawPin(canvas: Canvas, pin: Pin) {
        val selected = pin.stop != null && pin.stop.key == selectedKey
        val radius = (if (selected || pin.stop == null) 9 else 7) * density
        paint.style = Paint.Style.FILL; paint.color = surface
        canvas.drawCircle(pin.x, pin.y, radius + density, paint)
        paint.color = primary
        canvas.drawCircle(pin.x, pin.y, radius, paint)
        canvas.save()
        canvas.translate(pin.x, pin.y)
        canvas.scale(density * 0.7f, density * 0.7f)
        paint.color = foreground
        if (pin.stop == null) {
            // A distinct school symbol, rather than the same pin as every stop.
            canvas.drawPath(schoolRoof, paint)
            canvas.drawRect(-6f, -1f, 6f, 7f, paint)
            paint.color = primary
            canvas.drawRect(-1.5f, 2f, 1.5f, 7f, paint)
            canvas.drawRect(-4.5f, 1f, -2.5f, 3f, paint)
            canvas.drawRect(2.5f, 1f, 4.5f, 3f, paint)
        } else {
            // Compact public transport pictogram: body, window and wheels.
            canvas.drawRoundRect(RectF(-5.5f, -7f, 5.5f, 5f), 2f, 2f, paint)
            canvas.drawCircle(-3.5f, 6f, 1.5f, paint); canvas.drawCircle(3.5f, 6f, 1.5f, paint)
            paint.color = primary
            canvas.drawRoundRect(RectF(-4f, -5f, 4f, 0f), 1f, 1f, paint)
            canvas.drawCircle(-3f, 3f, 1f, paint); canvas.drawCircle(3f, 3f, 1f, paint)
        }
        canvas.restore()
    }

    override fun onSingleTapConfirmed(event: MotionEvent, map: MapView): Boolean {
        // Keep a 44 dp touch target even though the visible icon is only 14 dp.
        val nearest = pins(map).filter { it.stop != null }
            .minByOrNull { hypot(it.x - event.x, it.y - event.y) } ?: return false
        if (hypot(nearest.x - event.x, nearest.y - event.y) > 22 * density) return false
        nearest.stop?.let(onSelect)
        return true
    }
}
