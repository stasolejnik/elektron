# eLektron

Nieoficjalna aplikacja na Androida z planem lekcji, zastępstwami i ogłoszeniami dla
**Zespołu Szkół Elektronicznych w Bydgoszczy** - bez logowania, działa offline, dane pobiera
prosto z publicznych stron szkoły. Inspirowana aplikacją **Atom** (iOS) Kacpra Górki.

Projekt niezależny - niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN.

- Repozytorium: https://github.com/stasolejnik/elektron
- Watcher powiadomień push: https://github.com/stasolejnik/elektron-push-watcher
- Inspiracja: https://github.com/kacpergorka/atom

## Funkcje

- **Plan lekcji** - widok dnia i tygodnia, przesuwanie palcem między dniami i tygodniami,
  zastępstwa naniesione na plan, "Trwa teraz" / "Za X min".
- **Grupy zajęciowe** - wybierasz swoje grupy (językowe, zajęcia praktyczne, religia 1/1...),
  a plan, Start i powiadomienia pokazują tylko Twoje lekcje.
- **Zastępstwa** - tylko Twoja klasa i grupa, bez minionych.
- **Ogłoszenia** - RSS szkoły, otwierane w przeglądarce.
- **Start** - najbliższa lekcja, nadchodzące zastępstwa, najnowsze ogłoszenia.
- **Widżety** - "Następna lekcja" (2x2 / 4x2) i "Plan dnia" (4x2 - 4x4), odświeżane z dzwonkiem.
- **Powiadomienia** - push na bieżąco (Firebase Cloud Messaging) + sprawdzanie w tle
  co 15 min jako zabezpieczenie.
- **Offline** - wszystko z lokalnej bazy, sieć tylko ją uzupełnia.
- Jasny i ciemny motyw, skróty z ikony aplikacji.

## Budowanie (Arch Linux)

Pierwsza konfiguracja - skrypt sam doinstaluje Javę 17 i Android SDK:

    ./build_elektron.sh

Kolejne buildy:

    JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug :app:testDebugUnitTest

APK: `app/build/outputs/apk/debug/app-debug.apk`, instalacja: `adb install -r <apk>`.

Gradle 8.9 wymaga **Javy 17** (nie działa na Javie 23+).

**Wymagany `app/google-services.json`** (projekt Firebase z aplikacją
`pl.zse.bydgoszcz.elektron`). Plik nie jest w repozytorium - bez niego build się nie uda.
Konfiguracja powiadomień push: `PORADNIK_PUSH.md`.

### Build release

1. Wygeneruj klucz (raz, w katalogu głównym projektu):

       keytool -genkeypair -v -keystore elektron-release.jks -alias elektron \
         -keyalg RSA -keysize 4096 -validity 10000

2. Skopiuj `keystore.properties.example` do `keystore.properties` i wpisz hasła.
   Oba pliki (`*.jks`, `keystore.properties`) są w `.gitignore`.
   W haśle unikaj znaku `\` (w plikach `.properties` jest znakiem specjalnym).
3. `./gradlew :app:assembleRelease` - wynik: `app/build/outputs/apk/release/app-release.apk`

**Zrób kopię zapasową klucza i hasła.** Bez tego samego klucza nie wydasz aktualizacji -
użytkownicy musieliby odinstalować aplikację.

## Stack

Kotlin 2.0.20 - Jetpack Compose + Material 3 - Hilt - Room - DataStore - WorkManager -
OkHttp + Jsoup - Coil - Glance (widżety) - Firebase Cloud Messaging.

Architektura: MVVM + Clean (data / domain / presentation).

## Dane i zasady

| Źródło | Kodowanie |
|---|---|
| plan.zse.bydgoszcz.pl | UTF-8 |
| zastepstwa.zse.bydgoszcz.pl | ISO-8859-2 (wymuszone) |
| zse.bydgoszcz.pl (RSS) | UTF-8 |

Tylko publiczne strony szkoły, bez logowania i danych osobowych, z poszanowaniem
robots.txt. Zapytania warunkowe (cache HTTP) - niezmieniona strona to krótka odpowiedź 304.

## Licencja

Copyright (C) 2026 Stanisław Olejnik

Ten program jest wolnym oprogramowaniem: możesz go rozpowszechniać i/lub modyfikować
na warunkach Powszechnej Licencji Publicznej GNU (GNU GPL) w wersji 3 lub (według
Twojego wyboru) dowolnej późniejszej. Pełny tekst: `LICENSE`.

eLektron jest napisany od podstaw i nie zawiera kodu aplikacji Atom - korzysta jedynie
z jej pomysłu. Podziękowania dla autora: `NOTICE`.
