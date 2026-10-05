package pl.zse.bydgoszcz.elektron.presentation.transit

internal data class MapLabelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: MapLabelRect) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun contains(other: MapLabelRect) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/** Omit a label when none of the four positions fits without covering another element. */
internal fun placeMapLabel(
    x: Float, y: Float, width: Float, height: Float, gap: Float,
    viewport: MapLabelRect, occupied: List<MapLabelRect>
): MapLabelRect? = listOf(
    MapLabelRect(x + gap, y - height / 2, x + gap + width, y + height / 2),
    MapLabelRect(x - gap - width, y - height / 2, x - gap, y + height / 2),
    MapLabelRect(x - width / 2, y - gap - height, x + width / 2, y - gap),
    MapLabelRect(x - width / 2, y + gap, x + width / 2, y + gap + height)
).firstOrNull { candidate -> viewport.contains(candidate) && occupied.none(candidate::overlaps) }
