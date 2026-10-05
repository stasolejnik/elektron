package pl.zse.bydgoszcz.elektron.presentation.transit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitStopOverlayTest {
    @Test fun smallStopIconRetainsLargeTouchTargetAndDoesNotSelectFromElsewhere() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val map = MapView(context)
        map.setUseDataConnection(false)
        try {
            map.layout(0, 0, 600, 800)
            map.controller.setZoom(16.0)
            map.controller.setCenter(GeoPoint(SchoolTransit.LATITUDE, SchoolTransit.LONGITUDE))
            val stop = TransitDestination("Cel", listOf("123"), SchoolTransit.LATITUDE + 0.001, SchoolTransit.LONGITUDE)
            var selected: TransitDestination? = null
            val overlay = TransitStopOverlay(1f, 12f) { selected = it }.apply { stops = listOf(stop) }
            val point = map.projection.toPixels(GeoPoint(stop.latitude, stop.longitude), Point())
            fun tap(offset: Float): Boolean {
                val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, point.x + offset, point.y.toFloat(), 0)
                return try { overlay.onSingleTapConfirmed(event, map) } finally { event.recycle() }
            }
            assertTrue(tap(21f))
            assertEquals(stop, selected)
            selected = null
            assertFalse(tap(23f))
            assertNull(selected)
            overlay.stops = emptyList()
            assertFalse(tap(0f))
        } finally { map.onDetach() }
    }

    @Test fun drawingHandlesCrowdedStopsLargeFontsAndSelectionCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val map = MapView(context)
        map.setUseDataConnection(false)
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        try {
            map.layout(0, 0, 600, 800)
            map.controller.setZoom(15.0)
            map.controller.setCenter(GeoPoint(SchoolTransit.LATITUDE, SchoolTransit.LONGITUDE))
            val overlay = TransitStopOverlay(1f, 24f) {}.apply {
                stops = (1..30).map { TransitDestination("Bardzo długa nazwa przystanku $it", listOf(it.toString()),
                    SchoolTransit.LATITUDE + it * 0.00005, SchoolTransit.LONGITUDE) }
                selectedKey = stops.first().key
                bottomInset = 180f
            }
            overlay.draw(Canvas(bitmap), map, false)
            map.controller.setZoom(14.0)
            overlay.draw(Canvas(bitmap), map, false)
        } finally { bitmap.recycle(); map.onDetach() }
    }
    @Test fun schoolNameIsVisibleWithoutZoomingIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val map = MapView(context).apply { setUseDataConnection(false) }
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        try {
            map.layout(0, 0, 600, 800)
            map.controller.setZoom(14.0)
            map.controller.setCenter(GeoPoint(SchoolTransit.LATITUDE, SchoolTransit.LONGITUDE))
            val canvas = Canvas(bitmap)
            TransitStopOverlay(1f, 12f) {}.draw(canvas, map, false)
            val shadow = org.robolectric.Shadows.shadowOf(canvas)
            assertTrue((0 until shadow.textHistoryCount).any { shadow.getDrawnTextEvent(it).text == "Szkoła" })
        } finally { bitmap.recycle(); map.onDetach() }
    }
}
