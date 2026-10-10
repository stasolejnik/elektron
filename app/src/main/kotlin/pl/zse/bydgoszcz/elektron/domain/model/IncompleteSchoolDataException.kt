package pl.zse.bydgoszcz.elektron.domain.model

class IncompleteSchoolDataException : Exception(USER_MESSAGE) {
    companion object {
        // Ostrzeżenie, nie awaria: odświeżenie się udało, tylko część wierszy strony ma nietypowy zapis.
        // Dawniej "Nie udało się odświeżyć zastępstw. Nie udało się odczytać…" w czerwonej karcie.
        const val USER_MESSAGE = "Kilku wpisów ze strony zastępstw nie udało się odczytać - lista może być niepełna. Wcześniejsze wpisy zostały zachowane."

        /** Komunikat to tylko to ostrzeżenie (bez innych błędów) - UI pokazuje je łagodniej. */
        fun isWarning(message: String?): Boolean = message == USER_MESSAGE

        /** Komunikat zapisany przez 1.0.0-rc7 - widoczny po aktualizacji do pierwszej synchronizacji. */
        private const val RC7_MESSAGE = "Nie udało się odświeżyć zastępstw. Nie udało się odczytać wszystkich zastępstw. " +
            "Lista może być niepełna; zachowano wcześniejsze wpisy."

        /** Zapisany komunikat w obecnej postaci (stary tekst rc7 -> [USER_MESSAGE]). */
        fun normalize(message: String?): String? = if (message == RC7_MESSAGE) USER_MESSAGE else message
    }
}
