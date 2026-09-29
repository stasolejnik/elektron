# eLektron

Nieoficjalna aplikacja na Androida z planem lekcji, zastępstwami i ogłoszeniami dla
**Zespołu Szkół Elektronicznych w Bydgoszczy**. Inspirowana aplikacją **Atom** (iOS) Kacpra Górki.

eLektron to [wolne oprogramowanie](https://www.gnu.org/philosophy/free-sw.pl.html) na licencji
[GNU GPL v3 lub nowszej](LICENSE) - możesz go swobodnie używać, poznawać, zmieniać
i rozpowszechniać.

Projekt niezależny - niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN.

Kod aplikacji napisało bezduszne AI, głównie Claude (Anthropic).

- Watcher powiadomień push: https://github.com/stasolejnik/elektron-push-watcher
- Inspiracja: https://github.com/kacpergorka/atom

## Funkcje

- **Plan lekcji** - widok dnia i tygodnia. Przesunięcie w górę to następny dzień, w dół
  poprzedni. Po ostatniej lekcji plan od razu pokazuje kolejny dzień szkolny.
- **Zastępstwa na planie** - na pierwszym planie nauczyciel zastępujący, pod nim sala
  i uwagi ze strony szkoły (np. „za ostatnią lekcję”).
- **Grupy zajęciowe** - szybki wybór dla całego podziału (np. „1/2” dla wszystkich
  przedmiotów) albo osobno dla każdego przedmiotu. Plan, strona główna, widżety
  i powiadomienia pokazują tylko Twoje lekcje.
- **Strona główna** - trwająca lub najbliższa lekcja z paskiem postępu, przerwy,
  nadchodzące zastępstwa, najnowsze ogłoszenia.
- **Ogłoszenia** - najnowsze z RSS szkoły, starsze z archiwum strony, wyszukiwanie
  bez polskich znaków.
- **Widżety** - „Następna lekcja”, „Plan dnia” i „Zastępstwa”, w kolorach motywu aplikacji
  lub tapety, odświeżane z dzwonkiem. Kafelek „Następna lekcja” w szybkich ustawieniach.
- **Powiadomienia** - o nowych zastępstwach i ogłoszeniach (push w wersji z GitHuba,
  synchronizacja w tle co 15 min w obu wersjach).
- **Offline** - wszystko z lokalnej bazy, sieć tylko ją uzupełnia.
- Przesuwanie palcem między sekcjami, jasny i ciemny motyw, kolory z tapety (Android 12+),
  skróty z ikony aplikacji, w godzinach lekcji start od razu na planie.

Wymagania: Android 8.0 (API 26) lub nowszy.

## Instalacja

1. Pobierz `eLektron-<wersja>.apk` z [Releases](https://github.com/stasolejnik/elektron/releases).
2. Otwórz plik na telefonie i zezwól na instalację z tego źródła.
3. Kolejne wersje instalują się jako aktualizacja - bez utraty ustawień. Aplikacja sama
   informuje o nowej wersji na stronie głównej.

Wersja z F-Droid (bez usług Google) jest w przygotowaniu.

## Wersje: GitHub i F-Droid

Aplikacja ma dwa warianty (Gradle product flavors):

| Wariant | Gdzie | Różnice |
|---|---|---|
| `gms` | GitHub Releases | powiadomienia push (Firebase Cloud Messaging), sprawdzanie aktualizacji |
| `foss` | F-Droid | wyłącznie wolne zależności, bez Firebase i bez sprawdzania aktualizacji; powiadomienia z synchronizacji w tle co 15 min |

Kod zależny od Firebase jest tylko w `app/src/gms/`, zaślepki w `app/src/foss/`.
Metadane dla F-Droid: `fastlane/metadata/android/`.

## Budowanie

Wymagana **Java 17** (Gradle 8.9 nie działa na Javie 23+).

Pierwsza konfiguracja na Arch Linux - skrypt sam doinstaluje Javę 17 i Android SDK:

    ./build_elektron.sh

Kolejne buildy:

    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
    ./gradlew :app:testGmsDebugUnitTest :app:assembleGmsDebug

Wersja F-Droid (nie potrzebuje `google-services.json`):

    ./gradlew :app:assembleFossRelease

**Wariant `gms` wymaga `app/google-services.json`** (projekt Firebase z aplikacją
`pl.zse.bydgoszcz.elektron`). Pliku nie ma w repozytorium. Konfiguracja powiadomień push:
`PORADNIK_PUSH.md`.

### Build release (podpisany)

1. Wygeneruj klucz (raz, w katalogu głównym projektu):

       keytool -genkeypair -v -keystore elektron-release.jks -alias elektron \
         -keyalg RSA -keysize 4096 -validity 10000

2. Skopiuj `keystore.properties.example` do `keystore.properties` i wpisz hasła.
   Oba pliki (`*.jks`, `keystore.properties`) są w `.gitignore`.
   Znak `\` w haśle zapisz jako `\\` (w plikach `.properties` jest znakiem specjalnym).
3. `./gradlew :app:assembleGmsRelease`
   → `app/build/outputs/apk/gms/release/app-gms-release.apk`

APK przenieś na telefon dowolnie (kabel USB, KDE Connect, chmura) - ADB nie jest potrzebne.

**Zrób kopię zapasową klucza i hasła.** Bez tego samego klucza nie wydasz aktualizacji -
użytkownicy musieliby odinstalować aplikację.

## Testy

    ./gradlew :app:testGmsDebugUnitTest

Testy jednostkowe (parsery, logika grup, zastępstw, widżetów, wersji) i testy repozytoriów
na Robolectric. Raport: `app/build/reports/tests/testGmsDebugUnitTest/index.html`.

## Stack

Kotlin 2.0.20 - Jetpack Compose + Material 3 - Hilt - Room - DataStore - WorkManager -
OkHttp + Jsoup - Coil - Glance (widżety) - Firebase Cloud Messaging (tylko `gms`).

Architektura: MVVM + Clean (data / domain / presentation), Room jako jedyne źródło prawdy.

## Dane i zasady

| Źródło | Kodowanie |
|---|---|
| plan.zse.bydgoszcz.pl | UTF-8 |
| zastepstwa.zse.bydgoszcz.pl | ISO-8859-2 (wymuszone) |
| zse.bydgoszcz.pl (RSS i archiwum) | UTF-8 |

Tylko publiczne strony szkoły, bez logowania i danych osobowych, z poszanowaniem
robots.txt. Zapytania warunkowe (cache HTTP) - niezmieniona strona to krótka odpowiedź 304.
Szczegóły: [polityka prywatności](PRYWATNOSC.md).

## Zmiany

Historia wersji: [CHANGELOG.md](CHANGELOG.md).

## Licencja

Copyright (C) 2026 Stanisław Olejnik

Ten program jest wolnym oprogramowaniem: możesz go rozpowszechniać i/lub modyfikować
na warunkach Powszechnej Licencji Publicznej GNU (GNU GPL) w wersji 3 lub (według
Twojego wyboru) dowolnej późniejszej. Pełny tekst: `LICENSE`.

eLektron jest napisany od podstaw i nie zawiera kodu aplikacji Atom - korzysta jedynie
z jej pomysłu. Podziękowania dla Kacpra Górki, autora aplikacji Atom - patrz `NOTICE`.
