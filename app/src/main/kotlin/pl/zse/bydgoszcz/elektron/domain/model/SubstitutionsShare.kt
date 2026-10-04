package pl.zse.bydgoszcz.elektron.domain.model

import java.time.format.DateTimeFormatter

object SubstitutionsShare {
    fun text(items: List<Substitution>): String = items.distinctBy { it.id }
        .sortedWith(compareBy<Substitution> { it.date }.thenBy { it.lessonNumber }.thenBy { it.groupNumber ?: 0 })
        .groupBy { it.date to it.classShortName }.entries.joinToString("\n\n") { (key, list) ->
            "Zastępstwa · ${key.second} · ${key.first.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}\n" +
                list.joinToString("\n\n") { s -> buildString {
                    append("${s.lessonNumber}. lekcja")
                    s.groupNumber?.let { append(" · grupa $it") }
                    append("\n${SubstitutionDisplay.headline(s)}")
                    SubstitutionDisplay.place(s)?.let { append("\n$it") }
                    append("\nZa: ${s.originalTeacher}")
                    s.originalSubject?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    SubstitutionDisplay.notes(s)?.let { append("\n$it") }
                } }
        }
}
