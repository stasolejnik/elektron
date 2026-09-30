package pl.zse.bydgoszcz.elektron.presentation.common

object Changelog {

    data class Entry(val versionCode: Int, val versionName: String, val items: List<String>)

    val entries: List<Entry> = listOf(
        Entry(
            versionCode = 17,
            versionName = "0.6.4-beta",
            items = listOf(
                "WAŻNE: zastępstwa odczytywane w całości - część zastępstw i powiadomień mogła dotąd nie docierać"
            )
        ),
        Entry(
            versionCode = 16,
            versionName = "0.6.3-beta",
            items = listOf(
                "Płynniejsze przełączanie zakładek - bez migania innej zawartości przed właściwą",
                "Plan otwiera się od razu na właściwym dniu, bez przeskoku",
                "Kółka ładowania tylko przy dłuższym wczytywaniu"
            )
        ),
        Entry(
            versionCode = 15,
            versionName = "0.6.2-beta",
            items = listOf(
                "Plan: brakujące lekcje wpisane na stronie szkoły tekstem (np. zajęcia wojskowe w klasach F, zajęcia 5B i 5D)",
                "Zajęcia łączone pokazują, z którą klasą są (np. łączona z 1A)",
                "Poprawiona nazwa zajęć bez przypisanej sali (zaj.woj. zamiast zaj.woj.@)"
            )
        ),
        Entry(
            versionCode = 14,
            versionName = "0.6.1-beta",
            items = listOf(
                "Grupy WF: wybór grupy w klasach z podziałem na WF (np. zajęcia łączone z inną klasą)",
                "Aktualizacje pobierane i instalowane w aplikacji, z paskiem postępu i sprawdzeniem pliku",
                "Wybór grup w Ustawieniach można anulować - zmiany zapisują się dopiero po Zapisz",
                "Przezroczystość widżetów ustawiana suwakiem, od 0 do 100%",
                "Strona główna: dotknij najbliższej lekcji lub zastępstwa, aby przejść do planu lub zastępstw",
                "Poprawki stabilności"
            )
        ),
        Entry(
            versionCode = 13,
            versionName = "0.6.0-beta",
            items = listOf(
                "Przypomnienia przed lekcją - przed pierwszą albo każdą lekcją, 5-30 minut wcześniej",
                "Własne nazwy i kolory przedmiotów - w planie, na stronie głównej i w widżetach",
                "Kolor akcentu aplikacji i widżetów: pięć palet albo własny kolor",
                "Ciche godziny: powiadomienia bez dźwięku, np. w nocy",
                "Wybór ekranu startowego, zapamiętany widok dnia lub tygodnia",
                "Widżety: przezroczystość tła, nauczyciel i sala do wyboru",
                "Plan po lekcjach od razu pokazuje następny dzień, a zwolnienia z lekcji nie liczą się jako lekcje",
                "Przewijanie lekcji nie przełącza już przypadkiem dnia",
                "Raporty awarii i przycisk Zgłoś problem w Ustawieniach",
                "Czytelniejszy jasny motyw, uporządkowane Ustawienia i wiele poprawek"
            )
        ),
        Entry(
            versionCode = 12,
            versionName = "0.5.0-alpha",
            items = listOf(
                "Przesuń palcem w lewo lub prawo, aby przejść do innej sekcji",
                "Plan: przesuń w górę - następny dzień, w dół - poprzedni; po lekcjach od razu następny dzień",
                "Grupy: szybki wybór dla całego podziału, np. 1/2 dla wszystkich przedmiotów",
                "Strona główna (dawniej Start): pasek postępu lekcji i przerwy, informacja o nowej wersji",
                "Widżety: nowy widżet Zastępstwa, przerwy, kolory motywu i tapety, dotknięcie lekcji otwiera aplikację",
                "Kafelek w szybkich ustawieniach; w godzinach lekcji aplikacja otwiera się na planie",
                "Ogłoszenia: wyszukiwanie i starsze wpisy z archiwum strony szkoły",
                "Zastępstwa: na pierwszym planie nauczyciel zastępujący, uwagi widoczne w planie i widżetach",
                "Ustawienia: kolory z tapety i Wyczyść dane podręczne",
                "Wersja bez usług Google (dla F-Droid), płynniejsze działanie i poprawki"
            )
        ),
        Entry(
            versionCode = 11,
            versionName = "0.4.0-alpha",
            items = listOf(
                "Widzety: Nastepna lekcja i Plan dnia (odswiezane z dzwonkiem)",
                "Plan lekcji: przesun palcem w lewo lub prawo, aby zmienic dzien lub tydzien",
                "Aktualizacje nie kasuja juz lokalnych danych aplikacji",
                "Przygotowanie do wydania podpisanej wersji"
            )
        ),
        Entry(
            versionCode = 10,
            versionName = "0.3.1-alpha",
            items = listOf(
                "Nowe logo z litera e",
                "Lista klas w Ustawieniach przewija sie niezaleznie od reszty ekranu",
                "Usunieto oznaczanie ogloszen jako przeczytane",
                "Naprawiono wieczne ladowanie po zmianie klasy",
                "Naprawiono wiszace Synchronizacja po przerwanym syncu",
                "Mniej zapisow do bazy przy powiadomieniach"
            )
        ),
        Entry(
            versionCode = 9,
            versionName = "0.3.0-alpha",
            items = listOf(
                "Grupy zajeciowe: wybierz swoje grupy, a plan i Start pokaza tylko Twoje lekcje",
                "Religia i podobne zajecia: przelacznik pokazuj / ukryj",
                "Zastepstwa dla innej grupy nie sa juz pokazywane ani zglaszane w powiadomieniach",
                "Grupy mozna zmienic w dowolnej chwili w Ustawieniach",
                "Usunieto panel dewelopera"
            )
        ),
        Entry(
            versionCode = 8,
            versionName = "0.2.1-alpha",
            items = listOf(
                "Nowe logo aplikacji",
                "Odswiezony wyglad: duze tytuly, nowe karty, plynniejsze przejscia",
                "Kolory aplikacji zgodne z logo, w trybie jasnym i ciemnym",
                "Ustawienia w formie grupowanej listy",
                "Obrazki ogloszen na cala szerokosc karty",
                "Poprawiony link do repozytorium w O aplikacji",
                "Usunieto zakladke powiadomien i licznik na Ogloszeniach"
            )
        ),
        Entry(
            versionCode = 7,
            versionName = "0.2.0-alpha",
            items = listOf(
                "Historia powiadomien dostepna z Start (dzwonek z licznikiem nieprzeczytanych)",
                "Licznik nieprzeczytanych ogloszen na dolnym pasku",
                "Pociagnij w dol, aby odswiezyc - takze w Ogloszeniach",
                "Trwa teraz i Za X min aktualizuja sie na biezaco przy otwartym ekranie",
                "Klikniecie powiadomienia nie doklada juz kolejnych kopii ekranu",
                "Nowa ikona powiadomien na pasku stanu",
                "Mniejszy transfer danych dzieki cache HTTP (bez utraty swiezosci)",
                "Drobne poprawki stabilnosci"
            )
        ),
        Entry(
            versionCode = 6,
            versionName = "0.1.3-alpha",
            items = listOf(
                "Pociagnij w dol, aby odswiezyc - teraz tez w Planie lekcji i Zastepstwach",
                "Nowe zastepstwa od razu pojawiaja sie na planie i w Nastepnej lekcji",
                "Naprawiono powtarzajace sie powiadomienia o tych samych ogloszeniach",
                "Plan: dzien bez lekcji pokazuje Brak lekcji zamiast wiecznego ladowania",
                "Usunieto baner o odswiezaniu na Start"
            )
        ),
        Entry(
            versionCode = 5,
            versionName = "0.1.2-alpha",
            items = listOf(
                "Naprawiono brak najblizszej lekcji na Start w weekend i po ostatniej lekcji w piatek",
                "Naprawiono wieczne Ladowanie na Start, gdy pierwszy sync sie nie udal",
                "Lista klas w Ustawieniach jest przewijana i pokazuje od razu Twoja klase",
                "Ponowne wybranie tej samej klasy nie odswieza juz wszystkiego od nowa",
                "Mniej zapytan do serwera szkoly przy przegladaniu planu"
            )
        ),
        Entry(
            versionCode = 4,
            versionName = "0.1.1-alpha",
            items = listOf(
                "Naprawiono zastepstwa wszystkich klas pokazywane na Start tuz po wyborze klasy",
                "Naprawiono brak najblizszej lekcji na Start podczas pierwszego syncu",
                "Powiadomienia push przez FCM (wymaga wlasnej konfiguracji Firebase)",
                "Optymalizacja: mniej danych pobieranych z bazy przy kazdej zmianie planu"
            )
        ),
        Entry(
            versionCode = 3,
            versionName = "0.1.0-alpha",
            items = listOf(
                "Wersja Alpha — pierwsza po pełnym audycie kodu",
                "Naprawiono resetowanie 'przeczytane' na ogloszeniach po kazdym syncu",
                "Naprawiono powiadomienie o ogloszeniu prowadzace donikad",
                "Naprawiono nakladanie zastepstwa na zly dzien w widoku tygodnia",
                "Naprawiono dialog Co nowego, ktory nigdy sie nie pokazywal",
                "Zastepstwa usuniete na stronie szkoly znikaja teraz z apki",
                "Poprawiono wyscig przy rownoleglej synchronizacji (Setup/Ustawienia/Start)",
                "Drobne poprawki stabilnosci i higieny kodu"
            )
        ),
        Entry(
            versionCode = 2,
            versionName = "0.2.0",
            items = listOf(
                "Zmiana nazwy aplikacji na eLektron",
                "Naprawiono ladowanie planu i zastepstw po pierwszym uruchomieniu",
                "Zastepstwa pokazuja tylko Twoja klase, bez przeszlych",
                "Plan lekcji: przycisk Dzien/Tydzien, badge Za X min przed lekcja",
                "Pull-to-refresh w Start",
                "Powiadomienia grupowe w systemie",
                "Panel dewelopera tylko w wersji debug",
                "Drobne poprawki stabilnosci"
            )
        ),
        Entry(
            versionCode = 1,
            versionName = "0.1.0",
            items = listOf("Pierwsza wersja - plan lekcji, zastepstwa, ogloszenia")
        )
    )

    fun forVersion(code: Int): Entry? = entries.firstOrNull { it.versionCode == code }
    fun newerThan(sinceCode: Int): List<Entry> =
        entries.filter { it.versionCode > sinceCode }.sortedByDescending { it.versionCode }
}
