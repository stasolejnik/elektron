package pl.zse.bydgoszcz.elektron.data.remote.dto

/** Wpis z listy aktualności na stronie głównej szkoły (archiwum starsze niż RSS). */
data class ArchiveItemDto(
    val url: String,
    val title: String,
    val excerpt: String?,
    val imageUrl: String?
)
