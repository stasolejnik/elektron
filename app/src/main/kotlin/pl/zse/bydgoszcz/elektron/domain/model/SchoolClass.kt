package pl.zse.bydgoszcz.elektron.domain.model

/** Oddział szkolny. shortName to pierwszy token, np. "1A". */
data class SchoolClass(
    val id: String,
    val fullName: String,
    val shortName: String,
    val url: String
)
