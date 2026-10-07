package pl.zse.bydgoszcz.elektron.domain.model

object TransitTimes {
    fun duration(minutes: Long): String {
        val value = minutes.coerceAtLeast(0)
        if (value < 60) return "$value min"
        val rest = value % 60
        return "${value / 60} godz." + if (rest == 0L) "" else " $rest min"
    }

    fun until(departureMs: Long, nowMs: Long): String {
        if (departureMs <= nowMs) return "teraz"
        val delta = departureMs - nowMs
        val minutes = delta / 60_000 + if (delta % 60_000 == 0L) 0 else 1
        return "za ${duration(minutes)}"
    }
}
