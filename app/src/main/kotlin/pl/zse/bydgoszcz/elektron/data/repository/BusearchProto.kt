package pl.zse.bydgoszcz.elektron.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.zse.bydgoszcz.elektron.data.remote.http.readCancellable
import java.io.IOException

/**
 * Binarna wersja API BUSearch (`/v2/app/...`, Protobuf, schemat `/v2/app/schema.proto`). Odpowiedzi są
 * kilka razy mniejsze od JSON (po kompresji gzip zwykle o 20-45%), a pola i ich numery w v2 się nie zmieniają.
 *
 * Bez biblioteki Protobuf i bez generowania kodu (protoc): mały dekoder poniżej zamienia odpowiedź na ten
 * sam [JSONObject], który zwraca wersja JSON (`/v2/...`), więc parsery Przejazdów zostają bez zmian.
 * Opisane są tylko wiadomości i pola, których aplikacja używa; pozostałe są pomijane.
 */
internal object BusearchProto {
    const val BASE = "https://api.busearch.pl/v2/app/cities/bydgoszcz"
    const val CONTENT_TYPE = "application/x-protobuf"

    enum class Kind {
        STRING, INT, BOOL, ENUM, MESSAGE,
        /** sint32 w milionowych częściach stopnia (`latE6`) - w JSON liczba zmiennoprzecinkowa pod nazwą bez "E6". */
        E6,
        /** Przebieg trasy (`shapeE5`): pierwszy punkt w 1e-5 stopnia, dalej różnice - w JSON [[lat, lon], ...]. */
        SHAPE_E5
    }

    class Field(val number: Int, val name: String, val kind: Kind, val repeated: Boolean = false,
        val message: (() -> Message)? = null, val enumValues: List<String> = emptyList())

    class Message(vararg fields: Field) {
        val byNumber: Map<Int, Field> = fields.associateBy { it.number }
    }

    private fun s(n: Int, name: String, repeated: Boolean = false) = Field(n, name, Kind.STRING, repeated)
    private fun i(n: Int, name: String) = Field(n, name, Kind.INT)
    private fun b(n: Int, name: String) = Field(n, name, Kind.BOOL)
    private fun e6(n: Int, name: String) = Field(n, name, Kind.E6)
    private fun e(n: Int, name: String, vararg values: String) = Field(n, name, Kind.ENUM, enumValues = listOf("", *values))
    private fun m(n: Int, name: String, repeated: Boolean = false, message: () -> Message) = Field(n, name, Kind.MESSAGE, repeated, message)

    // Numery pól ze schema.proto BUSearch v2 (nie zmieniają znaczenia).
    private val journeyStop = Message(s(1, "id"), s(2, "name"), e6(3, "lat"), e6(4, "lon"))
    private val intermediateStop = Message(s(1, "id"), s(2, "name"), i(3, "atMs"))
    private val leg = Message(e(1, "mode", "walk", "transit"), m(2, "fromStop") { journeyStop }, m(3, "toStop") { journeyStop },
        i(4, "departureMs"), i(5, "arrivalMs"), s(6, "line"), s(7, "destination"), i(8, "stopCount"),
        Field(9, "shape", Kind.SHAPE_E5), m(10, "intermediateStops", repeated = true) { intermediateStop })
    private val journey = Message(i(1, "leaveMs"), i(2, "departureMs"), i(3, "arrivalMs"), i(4, "durationMin"), i(5, "transfers"),
        s(6, "lines", repeated = true), m(7, "legs", repeated = true) { leg })
    /** POST /journeys. */
    val JOURNEYS = Message(i(1, "searchedFromMs"), i(2, "page"), i(3, "maxPages"), i(4, "nextPage"), i(5, "nextWhenMs"),
        m(6, "journeys", repeated = true) { journey })

    private val departure = Message(s(1, "line"), s(2, "destination"), i(3, "atMs"), i(4, "scheduledAtMs"), s(5, "time"), s(6, "timeText"),
        e(7, "source", "live", "timetable"), i(8, "delaySeconds"), i(9, "delayMinutes"), i(10, "observedAtMs"), s(11, "tripId"),
        s(12, "serviceDate"), s(13, "variant"), b(14, "lowFloor"), b(15, "arrivalOnly"), s(16, "vehicleId"),
        b(17, "vehiclePositionEstimated"), b(18, "vehiclePositionStale"), i(21, "distanceToStopM"))
    /** GET /stops/{id}/departures. */
    val DEPARTURES = Message(m(1, "departures", repeated = true) { departure }, s(2, "reason"))

    private val stop = Message(s(1, "id"), s(2, "name"), s(3, "baseName"), i(4, "poleNumber"), s(5, "code"), e6(6, "lat"), e6(7, "lon"),
        s(8, "lines", repeated = true), b(10, "tram"), b(11, "rail"), i(12, "bearing"))
    /** GET /stops. */
    val STOPS = Message(m(1, "stops", repeated = true) { stop })

    private val vehicle = Message(s(1, "vehicleId"), s(2, "fleetNumber"), s(3, "line"), e(4, "type", "BUS", "TRAM", "TROLLEYBUS", "TRAIN"),
        e6(5, "lat"), e6(6, "lon"), i(7, "bearing"), i(8, "movementBearing"), i(9, "speedKmh"), s(10, "destination"),
        i(20, "delaySeconds"), e(21, "positionSource", "gps", "board", "timetable", "estimated"), b(22, "boardConfirmed"),
        i(23, "positionUpdatedAtMs"), i(24, "lastSeenMs"), b(25, "offline"), b(26, "positionStale"), b(27, "positionImprecise"))
    /** GET /vehicles/{id}. */
    val VEHICLE = Message(m(1, "vehicle") { vehicle })

    private val etaStop = Message(s(1, "id"), s(2, "name"), s(3, "baseName"), i(4, "poleNumber"), e6(5, "lat"), e6(6, "lon"),
        b(7, "passed"), b(8, "current"), i(9, "etaMinutes"), s(10, "plannedTime"), s(11, "actualTime"), i(12, "delayMinutes"))
    /** GET /vehicles/{id}/etas. */
    val ETAS = Message(s(1, "vehicleId"), s(2, "line"), i(4, "delayMinutes"), i(5, "currentIndex"), i(6, "nextIndex"),
        m(7, "stops", repeated = true) { etaStop })

    /** Odpowiedź binarna jako JSON wersji JSON tego samego zasobu. */
    fun decode(bytes: ByteArray, message: Message): JSONObject = Reader(bytes).message(message, bytes.size, depth = 0)

    /** Żądanie do `/v2/app/...` i odpowiedź jako [JSONObject]; null przy 304 (bez zmian względem [Request] z If-None-Match). */
    suspend fun fetch(client: OkHttpClient, request: Request, message: Message, maxBytes: Long, what: String,
        onResponse: (okhttp3.Response) -> Unit = {}): JSONObject? {
        val bytes = client.newCall(request.newBuilder().header("Accept", CONTENT_TYPE).build()).readCancellable { response ->
            if (response.code == 304) return@readCancellable null
            if (!response.isSuccessful) throw IOException("$what: HTTP ${response.code}")
            onResponse(response)
            val source = response.body?.source() ?: throw IOException("Pusta odpowiedź")
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw IOException("Zbyt duża odpowiedź")
            source.readByteArray()
        } ?: return null
        return try { decode(bytes, message) } catch (e: IllegalArgumentException) { throw IOException("$what: nieprawidłowa odpowiedź", e) }
    }

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun message(message: Message, end: Int, depth: Int): JSONObject {
            require(depth <= 8) { "Zbyt głęboka odpowiedź" }
            val out = JSONObject()
            // Jak w JSON: listy są zawsze (puste też), a nie tylko gdy mają elementy.
            message.byNumber.values.filter { it.repeated }.forEach { out.put(it.name, JSONArray()) }
            while (pos < end) {
                val key = varint()
                val number = (key ushr 3).toInt()
                val wire = (key and 7).toInt()
                require(number > 0) { "Nieprawidłowe pole" }
                val field = message.byNumber[number]
                if (field == null) { skip(wire); continue }
                when (field.kind) {
                    Kind.MESSAGE -> {
                        require(wire == 2)
                        val length = length()
                        val value = message(field.message!!(), pos + length, depth + 1)
                        if (field.repeated) out.getJSONArray(field.name).put(value) else out.put(field.name, value)
                    }
                    Kind.STRING -> {
                        require(wire == 2)
                        val length = length()
                        val value = String(data, pos, length, Charsets.UTF_8).also { pos += length }
                        if (field.repeated) out.getJSONArray(field.name).put(value) else out.put(field.name, value)
                    }
                    Kind.SHAPE_E5 -> out.put(field.name, shape(packed(wire)))
                    else -> {
                        require(wire == 0)
                        val raw = varint()
                        when (field.kind) {
                            Kind.BOOL -> out.put(field.name, raw != 0L)
                            Kind.INT -> out.put(field.name, zigzag(raw))
                            Kind.E6 -> out.put(field.name, zigzag(raw) / 1e6)
                            Kind.ENUM -> field.enumValues.getOrNull(raw.toInt())?.takeIf { raw in 1..Int.MAX_VALUE.toLong() }?.let { out.put(field.name, it) }
                            else -> Unit
                        }
                    }
                }
            }
            require(pos == end) { "Ucięta wiadomość" }
            return out
        }

        /** Powtarzane sint32: spakowane (proto3) albo pojedynczo. */
        private fun packed(wire: Int): List<Long> = when (wire) {
            2 -> { val length = length(); val end = pos + length; buildList { while (pos < end) add(zigzag(varint())) }.also { require(pos == end) } }
            0 -> listOf(zigzag(varint()))
            else -> throw IllegalArgumentException("Nieprawidłowy przebieg trasy")
        }

        private fun shape(values: List<Long>): JSONArray {
            val points = JSONArray()
            var lat = 0L; var lon = 0L
            for (k in 0 until values.size / 2) {
                lat += values[2 * k]; lon += values[2 * k + 1]
                points.put(JSONArray().put(lat / 1e5).put(lon / 1e5))
            }
            return points
        }

        private fun zigzag(n: Long): Long = (n ushr 1) xor -(n and 1)

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < data.size && shift < 64) { "Ucięta liczba" }
                val byte = data[pos++].toInt()
                result = result or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }

        private fun length(): Int {
            val length = varint()
            require(length in 0..(data.size - pos).toLong()) { "Nieprawidłowa długość" }
            return length.toInt()
        }

        private fun skip(wire: Int) {
            val size = when (wire) {
                0 -> { varint(); 0 }
                1 -> 8
                2 -> length()
                5 -> 4
                else -> throw IllegalArgumentException("Nieobsługiwany typ pola")
            }
            require(pos + size <= data.size) { "Ucięta wiadomość" }
            pos += size
        }
    }
}
