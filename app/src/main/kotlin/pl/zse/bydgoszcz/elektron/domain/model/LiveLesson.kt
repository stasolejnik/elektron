package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Powiadomienie na żywo (Ustawienia -> Powiadomienia): w dni lekcyjne od [LEAD_MINUTES] min przed
 * pierwszą lekcją do końca ostatniej - co trwa, co dalej, zastępstwa i notatki. Czysta logika:
 * treść w danej chwili ([content]) i najbliższa granica, przy której treść się zmienia ([nextChange]).
 *
 * Stan "teraz" z tej samej reguły co strona główna i widżety ([LessonClock]): lekcje, z których
 * klasa jest zwolniona, nie trwają i nie są następne. Odliczanie pokazuje chronometr powiadomienia
 * (do [Content.countdownTo]), więc treść zmienia się tylko na granicach lekcji i przerw.
 * Lekcje muszą być po filtrze grup. Zastępstwo: najpierw nauczyciel (jak wszędzie poza widokiem Dzień).
 */
object LiveLesson {

    const val LEAD_MINUTES = 15L
    private const val NOTE_PREVIEW = 80

    enum class Phase {
        /** Przed pierwszą lekcją dnia (od [LEAD_MINUTES] min). */
        BEFORE,
        LESSON,
        /** Krótka przerwa między lekcjami (jak w [LessonClock]). */
        BREAK,
        /** Okienko - dłuższa luka między lekcjami. */
        GAP
    }

    data class Content(
        val phase: Phase,
        /** Np. "Lekcja: Matematyka · s. 105", "Przerwa", "Pierwsza lekcja: Matematyka · s. 105". */
        val title: String,
        /** Podpis przy chronometrze: "zostało", "lekcja za", "za". */
        val countdownLabel: String,
        /** Chwila, do której odlicza chronometr (koniec lekcji albo początek następnej). */
        val countdownTo: LocalDateTime,
        /** Początek odcinka (lekcji, przerwy, okienka, czasu przed lekcjami) - do paska postępu. */
        val progressFrom: LocalDateTime,
        /** Zwinięta treść: co dalej, np. "Dalej: 4. Fizyka · s. 12 · J. Kowalski". */
        val text: String,
        /** Rozwinięta treść - kolejne linie (zastępstwo, notatka, co dalej). */
        val lines: List<String>,
        /** Lekcja otwierana po dotknięciu (trwająca albo najbliższa). */
        val target: LessonTarget,
        /** Trwająca albo następna lekcja ma notatkę użytkownika. */
        val hasNote: Boolean,
        /** Koniec okna powiadomienia (koniec ostatniej lekcji) - zabezpieczenie przed zostaniem na noc. */
        val windowEnd: LocalDateTime
    ) {
        /** Postęp odcinka 0..1 w chwili [now]. */
        fun progress(now: LocalDateTime): Float {
            val total = java.time.Duration.between(progressFrom, countdownTo).seconds.coerceAtLeast(1)
            return java.time.Duration.between(progressFrom, now).seconds.coerceIn(0, total).toFloat() / total
        }
    }

    /** Okno powiadomienia w dniu [date]: od [LEAD_MINUTES] min przed pierwszą do końca ostatniej lekcji, która się odbywa. */
    fun window(lessons: List<Lesson>, date: LocalDate): Pair<LocalDateTime, LocalDateTime>? {
        val held = lessons.filter { it.date == date && SubstitutionDisplay.takesPlace(it) }
        if (held.isEmpty()) return null
        return date.atTime(held.minOf { it.timeFrom }).minusMinutes(LEAD_MINUTES) to date.atTime(held.maxOf { it.timeTo })
    }

    /** Treść w chwili [now]; null - poza oknem (przed, po lekcjach, dzień bez lekcji). */
    fun content(
        lessons: List<Lesson>,
        now: LocalDateTime,
        styles: Map<String, SubjectStyle> = emptyMap(),
        notes: Map<String, LessonNote> = emptyMap()
    ): Content? {
        val today = now.toLocalDate()
        val (start, end) = window(lessons, today) ?: return null
        if (now < start || now >= end) return null
        val held = lessons.filter { it.date == today && SubstitutionDisplay.takesPlace(it) }.sortedBy { it.timeFrom }
        fun noteOf(l: Lesson) = notes[LessonNote.key(l)]?.takeIf { NoteReminders.matchesGroups(it, l) }?.text?.takeIf { it.isNotBlank() }
        fun after(l: Lesson) = held.firstOrNull { it.timeFrom >= l.timeTo && it.id != l.id }

        return when (val status = LessonClock.status(held, now)) {
            is LessonClock.During -> {
                val l = status.lesson
                val next = after(l)
                val lines = buildList {
                    substitutionLine(l)?.let(::add)
                    noteOf(l)?.let { add("Notatka: ${preview(it)}") }
                    add(next?.let { "Dalej: ${describe(it, styles)}" } ?: "To ostatnia lekcja dzisiaj")
                    next?.let { substitutionLine(it, "Dalej zastępstwo") }?.let(::add)
                    next?.let(::noteOf)?.let { add("Notatka do następnej: ${preview(it)}") }
                }
                Content(
                    phase = Phase.LESSON,
                    title = "Lekcja: ${headline(l, styles)}",
                    countdownLabel = "zostało",
                    countdownTo = today.atTime(l.timeTo),
                    progressFrom = today.atTime(l.timeFrom),
                    text = (next?.let { "Dalej: ${describe(it, styles)}" } ?: "To ostatnia lekcja dzisiaj") +
                        if (noteOf(l) != null || next?.let(::noteOf) != null) " · ✎" else "",
                    lines = lines,
                    target = LessonTarget(today, l.number),
                    hasNote = noteOf(l) != null || next?.let(::noteOf) != null,
                    windowEnd = end
                )
            }
            is LessonClock.Break -> upcoming(Phase.BREAK, "Przerwa", "zostało", status.next, today.atTime(status.from), styles, ::noteOf, ::after, end)
            is LessonClock.Before -> {
                val next = status.next.takeIf { it.date == today } ?: return null
                val first = held.first().id == next.id
                if (first) upcoming(Phase.BEFORE, "Pierwsza lekcja: ${headline(next, styles)}", "za", next,
                    today.atTime(next.timeFrom).minusMinutes(LEAD_MINUTES), styles, ::noteOf, ::after, end)
                else {
                    val prevEnd = held.filter { it.timeTo <= now.toLocalTime() }.maxOf { it.timeTo }
                    upcoming(Phase.GAP, "Okienko", "lekcja za", next, today.atTime(prevEnd), styles, ::noteOf, ::after, end)
                }
            }
            LessonClock.Nothing -> null
        }
    }

    /** Przed lekcją [next] (przerwa, okienko, przed pierwszą): odliczanie do jej początku. */
    private fun upcoming(
        phase: Phase, title: String, label: String, next: Lesson, from: LocalDateTime,
        styles: Map<String, SubjectStyle>, noteOf: (Lesson) -> String?, after: (Lesson) -> Lesson?, end: LocalDateTime
    ): Content {
        val note = noteOf(next)
        val later = after(next)
        val lines = buildList {
            add("${if (phase == Phase.BEFORE) "Teraz" else "Dalej"}: ${describe(next, styles)}")
            substitutionLine(next)?.let(::add)
            note?.let { add("Notatka: ${preview(it)}") }
            later?.let { add("Potem: ${describe(it, styles)}") }
        }
        return Content(
            phase = phase,
            title = title,
            countdownLabel = label,
            countdownTo = next.date.atTime(next.timeFrom),
            progressFrom = from,
            text = (if (phase == Phase.BEFORE) describe(next, styles, withSubject = false).ifBlank { "${next.number}. lekcja" }
                else "Dalej: ${describe(next, styles)}") + if (note != null) " · ✎" else "",
            lines = lines,
            target = LessonTarget(next.date, next.number),
            hasNote = note != null,
            windowEnd = end
        )
    }

    /**
     * Najbliższa chwila po [now], w której treść się zmienia: początek okna, początek i koniec każdej
     * lekcji, która się odbywa (koniec ostatniej = koniec okna). Także w kolejnych dniach z [lessons].
     */
    fun nextChange(lessons: List<Lesson>, now: LocalDateTime): LocalDateTime? =
        lessons.filter(SubstitutionDisplay::takesPlace).groupBy { it.date }.flatMap { (date, day) ->
            val (start, _) = window(day, date) ?: return@flatMap emptyList()
            listOf(start) + day.flatMap { listOf(date.atTime(it.timeFrom), date.atTime(it.timeTo)) }
        }.filter { it > now }.minOrNull()

    /** "Matematyka · s. 105" (przy zastępstwie sala z zastępstwa). */
    private fun headline(l: Lesson, styles: Map<String, SubjectStyle>): String =
        listOfNotNull(subject(l, styles), room(l)?.let { "s. $it" }).joinToString(" · ")

    /**
     * "4. Fizyka · s. 12 · J. Kowalski"; przy zastępstwie najpierw zastępca (albo informacja szkoły),
     * potem sala - jak wszędzie poza widokiem Dzień.
     */
    private fun describe(l: Lesson, styles: Map<String, SubjectStyle>, withSubject: Boolean = true): String {
        val sub = l.substitution
        val who = if (sub != null) sub.substituteTeacher?.trim()?.takeIf { it.isNotEmpty() } ?: SubstitutionDisplay.info(sub)
            else l.groups.firstOrNull()?.let { it.teacherFullName ?: it.teacherCode ?: JointGroups.describe(it.classRef, l.className) }
        val roomText = room(l)?.let { "s. $it" }
        val parts = listOfNotNull(if (withSubject) "${l.number}. ${subject(l, styles)}" else null,
            *(if (sub != null) arrayOf(who, roomText) else arrayOf(roomText, who)))
        return parts.joinToString(" · ")
    }

    private fun subject(l: Lesson, styles: Map<String, SubjectStyle>): String =
        l.groups.mapNotNull { SubjectStyles.displayName(it.subject, styles) }.distinct().joinToString(" / ")
            .ifBlank { l.note ?: "Lekcja" }

    private fun room(l: Lesson): String? {
        val sub = l.substitution
        if (sub != null) {
            val text = sub.roomOrInfo.trim()
            if (!sub.substituteTeacher.isNullOrBlank() && text.isNotEmpty() && SubstitutionDisplay.isRoom(text)) return text
        }
        return l.groups.mapNotNull { it.room }.distinct().joinToString(", ").ifBlank { null }
    }

    /** "Zastępstwo: J. Nowak · s. 211 · za ostatnią lekcję" - nauczyciel pierwszy, potem sala albo informacja. */
    private fun substitutionLine(l: Lesson, prefix: String = "Zastępstwo"): String? {
        val sub = l.substitution ?: return null
        val parts = listOfNotNull(SubstitutionDisplay.headline(sub), SubstitutionDisplay.place(sub), SubstitutionDisplay.notes(sub))
        return "$prefix: " + parts.joinToString(" · ")
    }

    private fun preview(text: String): String {
        val oneLine = text.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= NOTE_PREVIEW) oneLine else oneLine.take(NOTE_PREVIEW - 1).trimEnd() + "…"
    }
}
