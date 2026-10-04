package pl.zse.bydgoszcz.elektron.domain.model

class ArticleContentException : Exception("Nie rozpoznano treści artykułu") {
    companion object {
        const val USER_MESSAGE = "Nie udało się odczytać treści ogłoszenia. Możesz otworzyć je na stronie szkoły."
    }
}
