<div align="center">

<img src="fastlane/metadata/android/pl-PL/images/icon.png" alt="eLektron" width="112">

# eLektron

**Plan lekcji, zastępstwa i ogłoszenia Zespołu Szkół Elektronicznych w Bydgoszczy**

Nieoficjalna aplikacja na Androida · FOSS

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?include_prereleases&label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz najnowszą wersję**](https://github.com/stasolejnik/elektron/releases)

</div>

> Aplikacja w pełni napisana przez bezduszne AI, głównie Claude (Anthropic).

## Funkcje

**Plan i zastępstwa**
- Plan dnia i tygodnia z naniesionymi zastępstwami; po lekcjach od razu następny dzień.
- Grupy zajęciowe - widać tylko Twoje lekcje (języki, zajęcia praktyczne, WF, religia).
- Zastępstwa Twojej klasy i grupy, z rozpoznawaniem zwolnień z lekcji.
- Strona główna z trwającą lub najbliższą lekcją, przerwą i nadchodzącymi zmianami.
- Ogłoszenia szkoły, także starsze z archiwum, z wyszukiwaniem.

**Powiadomienia**
- Nowe zastępstwa i ogłoszenia.
- Przypomnienia przed lekcją i ciche godziny.

**Personalizacja**
- Własne nazwy i kolory przedmiotów.
- Kolor akcentu (pięć palet lub własny), jasny i ciemny motyw, kolory z tapety.
- Widżety: Następna lekcja, Plan dnia, Zastępstwa oraz kafelek w szybkich ustawieniach.

**Prywatność**
- Bez logowania i bez konta - dane pochodzą z publicznych stron szkoły.
- Działa offline; nie zbiera danych osobowych ani statystyk.

## Instalacja

1. Pobierz plik `eLektron-<wersja>.apk` z [Releases](https://github.com/stasolejnik/elektron/releases).
2. Otwórz go na telefonie i zezwól na instalację z tego źródła.

Kolejne wersje instalują się jako aktualizacja, bez utraty ustawień. Aplikacja sama informuje
o nowym wydaniu. Wersja w F-Droid jest w przygotowaniu.

## Warianty

| Wariant | Dystrybucja | Różnice |
|---|---|---|
| `gms` | GitHub Releases | powiadomienia push (Firebase Cloud Messaging), sprawdzanie aktualizacji |
| `foss` | F-Droid | tylko wolne zależności; powiadomienia z synchronizacji w tle co 15 minut |

## Budowanie

Wymagania: JDK 17, Android SDK.

```sh
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

Wariant `gms` wymaga pliku `app/google-services.json` (projekt Firebase, nie ma go
w repozytorium - patrz [PORADNIK_PUSH.md](PORADNIK_PUSH.md)). Podpisane wydania korzystają
z `keystore.properties` - wzór w [keystore.properties.example](keystore.properties.example).

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance · OkHttp · Jsoup

## Zgłaszanie błędów

Błędy i propozycje zgłaszaj w [Issues](https://github.com/stasolejnik/elektron/issues) -
najprościej przyciskiem **Zgłoś problem** w Ustawieniach aplikacji, który dołącza wersję
aplikacji, Androida i model telefonu. Po awarii aplikacja sama proponuje wysłanie raportu.

## Prywatność

eLektron łączy się wyłącznie z publicznymi stronami ZSE w Bydgoszczy (oraz, w wariancie
`gms`, z usługą powiadomień push i GitHubem w celu sprawdzenia aktualizacji).
Szczegóły: [polityka prywatności](PRYWATNOSC.md).

## Powiązane projekty

- [elektron-push-watcher](https://github.com/stasolejnik/elektron-push-watcher) - usługa
  wysyłająca powiadomienia push o zastępstwach i ogłoszeniach.
- [Atom](https://github.com/kacpergorka/atom) - aplikacja na iOS autorstwa Kacpra Górki,
  inspiracja dla eLektronu.

## Licencja

Copyright © 2026 Stanisław Olejnik

eLektron jest wolnym oprogramowaniem: możesz go rozpowszechniać i modyfikować na warunkach
[GNU General Public License](LICENSE) w wersji 3 lub (według Twojego wyboru) dowolnej
późniejszej. Aplikacja jest napisana od podstaw i nie zawiera kodu aplikacji Atom -
szczegóły w [NOTICE](NOTICE).

Projekt niezależny - niepowiązany z Zespołem Szkół Elektronicznych w Bydgoszczy
ani z firmą VULCAN.
