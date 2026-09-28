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

## 0.4.0-alpha (11)

### Dodane
- Widżety „Następna lekcja” i „Plan dnia”, odświeżane z dzwonkiem.
- Plan lekcji: przesuwanie palcem w lewo i prawo zmienia dzień lub tydzień.
- Przygotowanie do wydań podpisanych kluczem release.

### Naprawione
- Aktualizacje nie kasują już lokalnych danych aplikacji.

Starsze wersje (0.1.0 - 0.3.1-alpha) nie są opisane w tym pliku.

[0.5.0-alpha]: https://github.com/stasolejnik/elektron/releases/tag/v0.5.0-alpha
