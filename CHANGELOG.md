# Changelog

Wszystkie istotne zmiany w eLektronie. Format oparty na
[Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), numeracja wersji zgodna z
[SemVer](https://semver.org/lang/pl/). W nawiasie `versionCode` z Androida.

## [0.5.0-alpha] - 2026-09-28 (12)

### Dodane
- Przesuwanie palcem w lewo i prawo między sekcjami, dolny pasek podąża za palcem.
- Plan lekcji jako pionowe strony dni i tygodni: w górę następny dzień, w dół poprzedni.
  Po ostatniej lekcji plan otwiera się od razu na kolejnym dniu szkolnym.
- Grupy: szybki wybór dla całego podziału (np. „1/2” dla wszystkich przedmiotów dzielonych
  na dwie grupy) oraz opcja „Wszystkie” przy każdym przedmiocie.
- Strona główna: pasek postępu trwającej lekcji i przerwy, „Zostało X min”, „Lekcja za X min”,
  „Następna lekcja · Jutro”, informacja o dostępnej nowej wersji.
- Nowy widżet „Zastępstwa”. Widżety w kolorach motywu aplikacji lub tapety (Android 12+),
  z przerwami, paskiem postępu i uwagami do zastępstw.
- Kafelek „Następna lekcja” w szybkich ustawieniach.
- Inteligentny start: w godzinach lekcji aplikacja otwiera się na planie (do wyłączenia).
- Ogłoszenia: wyszukiwanie i starsze wpisy z archiwum strony szkoły.
- Ustawienia: kolory z tapety, „Wyczyść dane podręczne”, ręczne sprawdzanie aktualizacji.
- Rozwijane powiadomienia o zastępstwach z przyciskiem „Pokaż w planie”.
- Wariant `foss` bez usług Google (pod F-Droid) i metadane `fastlane`.

### Zmienione
- Zastępstwa: na pierwszym planie nauczyciel zastępujący, pod nim sala; uwagi ze strony
  szkoły widoczne w planie, na stronie głównej i w widżetach.
- Zakładka Zastępstwa pokazuje wszystkie dzisiejsze zastępstwa przez cały dzień.
- „Start” przemianowany na „Strona główna”.
- Odświeżanie planu przyciskiem w pasku zamiast gestu pociągnięcia.
- Ogłoszenia przechowywane przez 2 lata.
- Obliczenia przeniesione poza wątek interfejsu - płynniejsze przewijanie.

### Naprawione
- Ucięte godziny i numery lekcji przy powiększonej czcionce systemowej.
- Ucięte etykiety dolnego paska - rozmiar dopasowuje się do miejsca.
- Losowe kolorowe tła wierszy w widżecie „Plan dnia”.
- Wieczne kółko ładowania tygodnia bez internetu.
- Plan i strona główna nie przechodziły same na nowy dzień po północy.

## [0.4.0-alpha] (11)

### Dodane
- Widżety „Następna lekcja” i „Plan dnia”, odświeżane z dzwonkiem.
- Plan lekcji: przesuwanie palcem w lewo i prawo zmienia dzień lub tydzień.
- Przygotowanie do wydań podpisanych kluczem release.

### Naprawione
- Aktualizacje nie kasują już lokalnych danych aplikacji.

## [0.3.1-alpha] (10)

### Zmienione
- Nowe logo z literą „e”.
- Lista klas w Ustawieniach przewija się niezależnie od reszty ekranu.
- Usunięto oznaczanie ogłoszeń jako przeczytane.
- Mniej zapisów do bazy przy powiadomieniach.

### Naprawione
- Wieczne ładowanie po zmianie klasy.
- Wiszący komunikat „Synchronizacja” po przerwanym syncu.

## [0.3.0-alpha] (9)

### Dodane
- Grupy zajęciowe: wybierasz swoje grupy, a plan i strona główna pokazują tylko Twoje lekcje.
- Przełącznik pokazuj / ukryj dla religii i podobnych zajęć.
- Zmiana grup w dowolnej chwili w Ustawieniach.

### Zmienione
- Zastępstwa dla innej grupy nie są pokazywane ani zgłaszane w powiadomieniach.
- Usunięto panel dewelopera.

## [0.2.1-alpha] (8)

### Zmienione
- Nowe logo i kolory aplikacji zgodne z logo, w trybie jasnym i ciemnym.
- Odświeżony wygląd: duże tytuły, nowe karty, płynniejsze przejścia.
- Ustawienia w formie grupowanej listy, obrazki ogłoszeń na całą szerokość karty.
- Usunięto zakładkę powiadomień i licznik na Ogłoszeniach.

### Naprawione
- Link do repozytorium w „O aplikacji”.

## [0.2.0-alpha] (7)

### Dodane
- Historia powiadomień dostępna ze strony głównej.
- Pociągnij w dół, aby odświeżyć - także w Ogłoszeniach.
- Nowa ikona powiadomień na pasku stanu.

### Zmienione
- „Trwa teraz” i „Za X min” aktualizują się na bieżąco.
- Mniejszy transfer danych dzięki cache HTTP.

### Naprawione
- Kliknięcie powiadomienia nie dokłada już kolejnych kopii ekranu.

## [0.1.3-alpha] (6)

### Dodane
- Pociągnij w dół, aby odświeżyć w Planie lekcji i Zastępstwach.

### Naprawione
- Nowe zastępstwa od razu pojawiają się na planie i w „Następnej lekcji”.
- Powtarzające się powiadomienia o tych samych ogłoszeniach.
- Dzień bez lekcji pokazuje „Brak lekcji” zamiast wiecznego ładowania.

## [0.1.2-alpha] (5)

### Naprawione
- Brak najbliższej lekcji w weekend i po ostatniej lekcji w piątek.
- Wieczne ładowanie, gdy pierwszy sync się nie udał.
- Lista klas przewija się i od razu pokazuje Twoją klasę.
- Mniej zapytań do serwera szkoły przy przeglądaniu planu.

## [0.1.1-alpha] (4)

### Dodane
- Powiadomienia push przez Firebase Cloud Messaging.

### Naprawione
- Zastępstwa wszystkich klas pokazywane tuż po wyborze klasy.
- Brak najbliższej lekcji podczas pierwszego syncu.

## [0.1.0-alpha] (3)

Pierwsza wersja alpha, po pełnym audycie kodu.

### Naprawione
- Zastępstwo nakładane na zły dzień w widoku tygodnia.
- Zastępstwa usunięte na stronie szkoły znikają teraz z aplikacji.
- Wyścig przy równoległej synchronizacji.
- Powiadomienie o ogłoszeniu prowadzące donikąd, niepokazujący się dialog „Co nowego”.

## [0.2.0] (2)

- Zmiana nazwy aplikacji na eLektron.
- Plan lekcji z przełącznikiem Dzień / Tydzień, zastępstwa tylko dla Twojej klasy.

## [0.1.0] (1)

- Pierwsza wersja: plan lekcji, zastępstwa, ogłoszenia.

[0.5.0-alpha]: https://github.com/stasolejnik/elektron/releases/tag/v0.5.0-alpha
