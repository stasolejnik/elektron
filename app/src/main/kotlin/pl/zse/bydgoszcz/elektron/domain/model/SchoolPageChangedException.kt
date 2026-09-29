package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Strona szkoły odpowiedziała, ale ma inny układ niż ten, który aplikacja umie odczytać
 * (np. nowa wersja programu generującego plan). Zapisane dane zostają bez zmian, a użytkownik
 * dostaje komunikat zamiast cichego "braku lekcji".
 */
class SchoolPageChangedException(val page: String) :
    Exception("Strona szkoły ($page) ma nowy układ, którego aplikacja jeszcze nie obsługuje") {
    companion object {
        const val USER_MESSAGE = "Strona szkoły z planem lekcji zmieniła wygląd. Pokazuję ostatni " +
            "zapisany plan - poprawka pojawi się w aktualizacji aplikacji."
    }
}
