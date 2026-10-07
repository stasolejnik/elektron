package pl.zse.bydgoszcz.elektron.domain.model

class IncompleteSchoolDataException : Exception(USER_MESSAGE) {
    companion object {
        const val USER_MESSAGE = "Nie udało się odczytać wszystkich zastępstw. Lista może być niepełna; zachowano wcześniejsze wpisy."
    }
}
