package pl.zse.bydgoszcz.elektron.domain.model

object NavigationOrder {
    val DEFAULT = listOf("dashboard", "timetable", "substitutions", "transit", "announcements", "settings")
    fun normalize(order: List<String>): List<String> = (order.filter { it in DEFAULT }.distinct() + DEFAULT).distinct()
    fun move(order: List<String>, route: String, index: Int): List<String> {
        if (route !in order) return order
        return order.toMutableList().apply { remove(route); add(index.coerceIn(0, size), route) }
    }
    // Replace only the slots occupied by visible buttons; hidden routes keep their saved position.
    fun reorderVisible(saved: List<String>, visible: List<String>): List<String> {
        val all = normalize(saved)
        val requested = visible.filter { it in DEFAULT }.distinct()
        val iterator = requested.iterator()
        return all.map { if (it in requested) iterator.next() else it }
    }
    fun slotLeft(index: Int, width: Float, count: Int, rtl: Boolean = false): Float =
        if (count <= 0) 0f else (if (rtl) count - 1 - index else index) * width / count
    fun targetIndex(x: Float, width: Float, count: Int, rtl: Boolean = false): Int {
        if (width <= 0f || count <= 0) return 0
        val visual = (x / (width / count)).toInt().coerceIn(0, count - 1)
        return if (rtl) count - 1 - visual else visual
    }
}
