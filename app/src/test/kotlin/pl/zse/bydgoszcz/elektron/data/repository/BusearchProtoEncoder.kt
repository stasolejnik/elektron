package pl.zse.bydgoszcz.elektron.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/**
 * Tylko do testów: JSON wersji JSON API BUSearch -> odpowiedź binarna (/v2/app) według schematu z
 * [BusearchProto]. Dzięki temu dawne nagrania odpowiedzi w JSON służą też do testów wersji binarnej.
 */
internal object BusearchProtoEncoder {
    /**
     * Przechwytywacz dodawany PRZED testowym, który odpowiada w JSON: odpowiedzi z /v2/app (wersja binarna)
     * zamienia na Protobuf - dawne testy z odpowiedziami w JSON sprawdzają też dekoder.
     */
    val FromJson = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val path = chain.request().url.encodedPath
        val message = when {
            !path.startsWith("/v2/app/cities/bydgoszcz/") || !response.isSuccessful -> null
            path.endsWith("/journeys") -> BusearchProto.JOURNEYS
            path.endsWith("/departures") -> BusearchProto.DEPARTURES
            path.endsWith("/etas") -> BusearchProto.ETAS
            path.endsWith("/stops") -> BusearchProto.STOPS
            path.contains("/vehicles/") -> BusearchProto.VEHICLE
            else -> null
        } ?: return@Interceptor response
        val text = response.body!!.string()
        val bytes = encode(JSONObject(text), message)
        response.newBuilder().body(okhttp3.ResponseBody.Companion.run { bytes.toResponseBody(okhttp3.MediaType.Companion.run { BusearchProto.CONTENT_TYPE.toMediaType() }) }).build()
    }

    fun encode(json: JSONObject, message: BusearchProto.Message): ByteArray = ByteArrayOutputStream().also { write(it, json, message) }.toByteArray()

    private fun write(out: ByteArrayOutputStream, json: JSONObject, message: BusearchProto.Message) {
        val byName = message.byNumber.values.associateBy { it.name }
        json.keys().forEach { key ->
            val field = byName[key] ?: return@forEach
            val value = json.get(key)
            if (value == JSONObject.NULL) return@forEach
            if (field.repeated) {
                val array = value as JSONArray
                for (i in 0 until array.length()) single(out, field, array.get(i))
            } else single(out, field, value)
        }
    }

    private fun single(out: ByteArrayOutputStream, field: BusearchProto.Field, value: Any) {
        when (field.kind) {
            BusearchProto.Kind.STRING -> bytes(out, field.number, value.toString().toByteArray(Charsets.UTF_8))
            BusearchProto.Kind.MESSAGE -> bytes(out, field.number, encode(value as JSONObject, field.message!!()))
            BusearchProto.Kind.BOOL -> if (value as Boolean) { key(out, field.number, 0); varint(out, 1) }
            BusearchProto.Kind.INT -> { key(out, field.number, 0); varint(out, zigzag((value as Number).toDouble().roundToLong())) }
            BusearchProto.Kind.E6 -> { key(out, field.number, 0); varint(out, zigzag(((value as Number).toDouble() * 1e6).roundToLong())) }
            BusearchProto.Kind.ENUM -> field.enumValues.indexOf(value as String).takeIf { it > 0 }?.let { key(out, field.number, 0); varint(out, it.toLong()) }
            BusearchProto.Kind.SHAPE_E5 -> {
                val points = value as JSONArray
                val packed = ByteArrayOutputStream()
                var lat = 0L; var lon = 0L
                for (i in 0 until points.length()) {
                    val p = points.getJSONArray(i)
                    val la = (p.getDouble(0) * 1e5).roundToLong(); val lo = (p.getDouble(1) * 1e5).roundToLong()
                    varint(packed, zigzag(la - lat)); varint(packed, zigzag(lo - lon))
                    lat = la; lon = lo
                }
                bytes(out, field.number, packed.toByteArray())
            }
        }
    }

    private fun bytes(out: ByteArrayOutputStream, number: Int, data: ByteArray) {
        key(out, number, 2); varint(out, data.size.toLong()); out.write(data)
    }

    private fun key(out: ByteArrayOutputStream, number: Int, wire: Int) = varint(out, (number.toLong() shl 3) or wire.toLong())

    private fun zigzag(n: Long) = (n shl 1) xor (n shr 63)

    private fun varint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
        out.write(v.toInt())
    }
}
