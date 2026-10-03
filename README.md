<div align="center">

<img src="docs/logo.png" alt="eLektron" width="112">

# eLektron

**Nieoficjalny klient planu lekcji, zastępstw i ogłoszeń Zespołu Szkół Elektronicznych w Bydgoszczy na Androida.**

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?include_prereleases&label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz najnowszą wersję**](https://github.com/stasolejnik/elektron/releases)

</div>

## O projekcie

Inspiracją był [Atom](https://github.com/kacpergorka/atom) [Kacpra Górki](https://github.com/kacpergorka),
aplikacja dla ZSE na urządzenia Apple. Dziękuję za pomysł. eLektron to mój niezależny projekt
na Androida, napisany od zera, bez kodu i API Atomu.

Cały kod napisała AI (Claude, Anthropic). Ja wymyślam funkcje, testuję i zgłaszam poprawki;
sam nie programuję. Obecnie wersja kandydująca 1.0.0. Projekt nie jest powiązany ze szkołą
ani z firmą VULCAN.

## Funkcje

- Plan dnia i tygodnia z naniesionymi zastępstwami, filtr grup zajęciowych (języki, WF,
  zajęcia praktyczne, religia), szczegóły lekcji.
- Zastępstwa klasy i wybranych grup (zastępstwa innych grup ukryte, do pokazania na żądanie);
  dotknięcie zastępstwa otwiera lekcję w planie.
- Ogłoszenia z RSS i archiwum strony szkoły, z wyszukiwaniem.
- Strona główna: trwająca lub najbliższa lekcja, przerwa, nadchodzące zmiany.
- Powiadomienia o nowych zastępstwach i ogłoszeniach, przypomnienia przed lekcją, ciche godziny.
- Widżety (Następna lekcja, Plan dnia, Zastępstwa) i kafelek szybkich ustawień.
- Działanie offline (kopia danych w lokalnej bazie), bez konta i logowania.
- Personalizacja: nazwy i kolory przedmiotów, motyw, kolor akcentu, kolory z tapety (Android 12+).

## Instalacja i konfiguracja

1. Pobierz `eLektron-<wersja>.apk` z [Releases](https://github.com/stasolejnik/elektron/releases)
   i zezwól na instalację z nieznanego źródła.
2. Wybierz klasę, a potem grupy zajęciowe (później można je zmienić w Ustawieniach, w sekcjach
   **Klasa** i **Grupy zajęciowe**).

Aktualizacje (wariant `gms`): aplikacja sprawdza GitHub Releases najwyżej co 12 h, pobiera APK,
weryfikuje SHA-256 (gdy wydanie ją podaje), nazwę pakietu i `versionCode`, po czym otwiera
systemowy instalator. Ustawienia zostają.

## Powiadomienia

- Synchronizacja w tle przez WorkManager co 15 min (minimum Androida); w wariancie `gms`
  dodatkowo push FCM wysyłany przez
  [elektron-push-watcher](https://github.com/stasolejnik/elektron-push-watcher).
- Opóźnienia wynikają z Doze, optymalizacji baterii i ograniczeń nakładek producentów
  (np. Xiaomi, Huawei, Samsung). Przypomnienia używają `AlarmManager`; bez uprawnienia do
  dokładnych alarmów mogą przyjść później.
- Gdy powiadomienia nie docierają, sprawdź w Ustawieniach sekcję **Powiadomienia**, uprawnienia
  systemowe aplikacji oraz **Wyłącz optymalizację baterii** w sekcji **Działanie w tle** (tam
  jest też poradnik dla konkretnych producentów).

## Zgłaszanie błędów

Przycisk **Zgłoś problem** w Ustawieniach tworzy raport (wersja aplikacji i Androida, model,
stan uprawnień, ostatnie logi aplikacji, bez danych osobowych) do wysłania e-mailem lub
w [Issues](https://github.com/stasolejnik/elektron/issues). Po awarii aplikacja sama proponuje
raport. Kontakt ze mną: **[kontakt.elektron@pm.me](mailto:kontakt.elektron@pm.me)**.

## Prywatność

Brak kont, reklam, analityki i śledzenia; ustawienia zostają na urządzeniu. Aplikacja łączy
się wyłącznie ze stronami szkoły, a wariant `gms` dodatkowo z FCM (wspólne tematy, bez
identyfikacji użytkownika) i z GitHubem (aktualizacje). Szczegóły: [PRYWATNOSC.md](PRYWATNOSC.md).

## Architektura

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance ·
OkHttp · Jsoup. minSdk 26.

| Pakiet (`app/src/main/kotlin/pl/zse/bydgoszcz/elektron/`) | Zawartość |
|---|---|
| `data/remote` | klient HTTP, parsery stron (Optivum, zastępstwa, RSS, archiwum) |
| `data/local`, `data/repository` | Room, DataStore, repozytoria |
| `domain` | modele i czysta logika (grupy, dopasowanie zastępstw, zegar lekcji), `SyncCoordinator` |
| `presentation` | ekrany Compose i ViewModele |
| `work` | synchronizacja w tle, powiadomienia, przypomnienia |
| `widget` | widżety Glance, kafelek |

Każda synchronizacja (WorkManager, wybór klasy, ręczne odświeżanie) przechodzi przez
`SyncCoordinator`, który łączy równoczesne żądania, zapisuje dane w Room i wysyła
powiadomienia o nowych zastępstwach. Zastępstwa nadpisują w bazie tylko dni widoczne na
stronie szkoły; błąd HTTP lub brak sieci jest zawsze błędem synchronizacji, nigdy pustą listą.

### Warianty

| Wariant | Różnice |
|---|---|
| `gms` | dystrybucja przez GitHub Releases; push FCM, sprawdzanie i instalacja aktualizacji |
| `foss` | bez usług Google, tylko wolne zależności; powiadomienia wyłącznie z synchronizacji w tle |

Kod zależny od wariantu: `app/src/gms/`, `app/src/foss/`.

### Budowanie i testy

Wymagania: JDK 17, Android SDK.

```sh
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

Wariant `gms` wymaga `app/google-services.json` (poza repozytorium, patrz
[PORADNIK_PUSH.md](PORADNIK_PUSH.md)). Podpisywanie wydań: `keystore.properties`
(wzór: [keystore.properties.example](keystore.properties.example)).
Historia zmian: [CHANGELOG.md](CHANGELOG.md).

## Źródła danych

Wyłącznie publiczne strony szkoły, z poszanowaniem `robots.txt`:

| Dane | Źródło |
|---|---|
| Plan lekcji i lista klas | [plan.zse.bydgoszcz.pl](https://plan.zse.bydgoszcz.pl) (VULCAN Optivum) |
| Zastępstwa | [zastepstwa.zse.bydgoszcz.pl](https://zastepstwa.zse.bydgoszcz.pl) |
| Ogłoszenia | kanały RSS [zse.bydgoszcz.pl](https://zse.bydgoszcz.pl) |

## Licencja

Copyright © 2026 Stanisław Olejnik

eLektron jest wolnym oprogramowaniem: możesz go rozpowszechniać i modyfikować na warunkach
[GNU General Public License](LICENSE) w wersji 3 lub (według Twojego wyboru) dowolnej
późniejszej. Aplikacja jest napisana od podstaw i nie zawiera kodu aplikacji Atom
(szczegóły w [NOTICE](NOTICE)).
