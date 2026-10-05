<div align="center">

<img src="docs/logo.png" alt="eLektron" width="112">

# eLektron

**Plan lekcji, zastępstwa, ogłoszenia i odjazdy ze szkoły w jednej aplikacji na Androida dla uczniów ZSE w Bydgoszczy.**

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?include_prereleases&label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz aplikację**](https://github.com/stasolejnik/elektron/releases)

</div>

## O aplikacji

eLektron zbiera informacje potrzebne w szkolnym dniu: plan Twojej klasy i grup, zastępstwa,
ogłoszenia oraz połączenia komunikacji miejskiej ze szkoły. Nie wymaga konta. Pobrane dane
szkolne i zapisane ulubione ogłoszenia można odczytać bez internetu.

To nieoficjalny projekt ucznia, niezwiązany ze szkołą ani z firmą VULCAN. Aplikacja jest
inspirowana [Atomem](https://github.com/kacpergorka/atom) [Kacpra Górki](https://github.com/kacpergorka)
na urządzenia Apple, ale została napisana od podstaw i nie zawiera jego kodu.
Kod tworzą Claude (Anthropic) i ChatGPT (OpenAI); ja wybieram funkcje i testuję aplikację.

## Funkcje

- **Plan lekcji:** widok dnia i tygodnia z zastępstwami, szczegóły sali i nauczyciela,
  wybór własnych grup zajęciowych oraz udostępnianie dnia lub tygodnia.
- **Notatki do lekcji:** przytrzymaj przyszłą lekcję, aby dodać notatkę. Możesz ustawić
  przypomnienie przed lekcją albo dzień wcześniej. Notatki są też widoczne w widżetach.
- **Zastępstwa:** zmiany dotyczące Twojej klasy i grup, przejście do lekcji w planie
  oraz udostępnianie pojedynczego zastępstwa lub całej widocznej listy.
- **Ogłoszenia:** wyszukiwanie, czytanie treści w aplikacji i ulubione z zapisem do odczytu
  bez internetu.
- **Odjazdy ze szkoły:** połączenia do wybranego przystanku, linia, kierunek, godziny
  rozkładowe i czas dojścia pieszo. Cel i preferowany przystanek początkowy wybierzesz
  przez wyszukiwarkę lub mapę OpenStreetMap, bez GPS. Dostępne są ostatnie wybory,
  późniejsze odjazdy i opcjonalne połączenia z przesiadką.
- **Strona główna:** trwająca lub najbliższa lekcja, przerwa i nadchodzące zmiany.
- **Powiadomienia:** nowe zastępstwa i ogłoszenia, przypomnienia o lekcjach i notatkach,
  ciche godziny.
- **Szybki dostęp:** widżety Następna lekcja, Plan dnia i Zastępstwa, kafelek szybkich
  ustawień oraz skróty po przytrzymaniu ikony aplikacji.
- **Wygląd:** własne nazwy i kolory przedmiotów, jasny lub ciemny motyw, kolor akcentu
  i kolory z tapety na Androidzie 12 lub nowszym.

Odjazdy włączysz w **Ustawieniach**, w sekcji **Funkcje eksperymentalne**, po wybraniu
przystanku docelowego. Zakładka pojawi się między Zastępstwami a Ogłoszeniami. Punktem
początkowym jest szkoła przy **Mieczysława Karłowicza 20 w Bydgoszczy**. Godziny pochodzą
z rozkładu, a dojście jest wyznaczane ulicami i ścieżkami. Sprawdzanie nowych połączeń
wymaga internetu; funkcja nie korzysta z lokalizacji telefonu.

## Instalacja i aktualizacje

1. Otwórz [wydania na GitHubie](https://github.com/stasolejnik/elektron/releases).
2. W sekcji **Assets** pobierz plik `eLektron-<wersja>.apk` i otwórz go na telefonie.
3. Jeśli Android poprosi o zgodę na instalację z przeglądarki lub menedżera plików,
   zezwól na instalowanie z tego źródła.
4. Przy pierwszym uruchomieniu wybierz klasę i swoje grupy zajęciowe.

Aplikacja wymaga **Androida 8.0 lub nowszego**. Kolejne wersje instaluj jako aktualizację,
bez odinstalowywania - zachowasz ustawienia, notatki i ulubione. W wydaniu z GitHuba
aktualizacje można sprawdzić również w aplikacji, w **Ustawieniach**.

## Powiadomienia

Android może opóźniać pracę aplikacji w tle. Jeśli powiadomienia nie przychodzą,
sprawdź zgodę na powiadomienia oraz sekcję **Działanie w tle** w Ustawieniach eLektronu.
Przypomnienia o lekcjach korzystają także z uprawnienia do dokładnych alarmów.

Dane szkolne są sprawdzane w tle przez WorkManager; wariant z GitHuba korzysta również
z powiadomień Firebase Cloud Messaging. Informacje o zastępstwach i ogłoszeniach pochodzą
ze stron szkoły. Odjazdy są sprawdzane podczas korzystania z tej funkcji.

## Prywatność

Aplikacja nie wymaga konta i nie zawiera reklam ani analityki. Ustawienia, notatki i ostatnie
wybrane przystanki są przechowywane lokalnie. Odjazdy nie wymagają GPS: zapytania dotyczą
stałego adresu szkoły i wybranych przystanków. Mapy i wyznaczanie tras korzystają z usług
zewnętrznych. Wariant `gms` korzysta również z Firebase do powiadomień i GitHuba do aktualizacji.
Szczegóły połączeń, przechowywania danych i kopii zapasowych: [PRYWATNOSC.md](PRYWATNOSC.md).

## Zgłaszanie problemów

W Ustawieniach użyj **Zgłoś problem**, aby przygotować raport z wersją aplikacji, informacjami
o urządzeniu i ostatnimi logami. Po awarii aplikacja może zaproponować raport przy kolejnym
uruchomieniu. Raport wysyłasz samodzielnie.

Opisz, co się stało i jak odtworzyć problem, w [Issues](https://github.com/stasolejnik/elektron/issues)
lub napisz na [kontakt.elektron@pm.me](mailto:kontakt.elektron@pm.me).

## Dla osób rozwijających projekt

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager, Glance,
OkHttp, Jsoup i osmdroid. Wymagania: JDK 17 i Android SDK; minSdk 26.

| Wariant | Zawartość |
|---|---|
| `gms` | GitHub Releases, powiadomienia FCM i aktualizacje w aplikacji |
| `foss` | Wolne zależności, bez usług Google i instalowania aktualizacji z aplikacji |

Testy i kompilacja wariantu `foss`:

```sh
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

Wariant `gms` wymaga lokalnego `app/google-services.json`; konfigurację opisuje
[PORADNIK_PUSH.md](PORADNIK_PUSH.md). Podpisywanie wydań wymaga `keystore.properties`
([przykład](keystore.properties.example)). Pliki konfiguracji i klucze nie trafiają do repozytorium.

Kod wspólny jest w `app/src/main/`, a zależny od wariantu w `app/src/gms/` i `app/src/foss/`.
Historia wydań: [CHANGELOG.md](CHANGELOG.md).

## Źródła danych

| Dane | Źródło |
|---|---|
| Plan lekcji i lista klas | [plan.zse.bydgoszcz.pl](https://plan.zse.bydgoszcz.pl) |
| Zastępstwa | [zastepstwa.zse.bydgoszcz.pl](https://zastepstwa.zse.bydgoszcz.pl) |
| Ogłoszenia | [zse.bydgoszcz.pl](https://zse.bydgoszcz.pl) |
| Przystanki i połączenia | [BUSearch Bydgoszcz](https://busearch.pl/bydgoszcz) |
| Mapa | [OpenStreetMap](https://www.openstreetmap.org/copyright) |
| Trasy piesze | [FOSSGIS / OpenStreetMap](https://routing.openstreetmap.de/about.html) |

## Licencja

Copyright © 2026 Stanisław Olejnik.

eLektron jest wolnym oprogramowaniem na licencji [GNU GPL](LICENSE) w wersji 3 lub
późniejszej. Podziękowania i informacje o zależnościach: [NOTICE](NOTICE).
