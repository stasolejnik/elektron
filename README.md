# eLektron

Nieoficjalna aplikacja na Androida z planem lekcji, zastępstwami i ogłoszeniami dla
**Zespołu Szkół Elektronicznych w Bydgoszczy** — bez logowania, działa offline, dane pobiera
prosto z publicznych stron szkoły. Inspirowana aplikacją **Atom** (iOS) Kacpra Górki.

Projekt niezależny — niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN.

- Repozytorium: https://github.com/stasolejnik/elektron
- Watcher powiadomień push: https://github.com/stasolejnik/elektron-push-watcher
- Oryginał „Atom": https://github.com/kacpergorka/atom (Apache 2.0)

## Funkcje

- **Plan lekcji** — widok dnia i tygodnia, przesuwanie palcem między dniami/tygodniami,
  zastępstwa naniesione na plan, „Trwa teraz" / „Za X min".
- **Grupy zajęciowe** — wybierasz swoje grupy (językowe, zajęcia praktyczne, religia 1/1…),
  a plan, Start i powiadomienia pokazują tylko Twoje lekcje.
- **Zastępstwa** — tylko Twoja klasa i grupa, bez minionych.
- **Ogłoszenia** — RSS szkoły, otwierane w przeglądarce.
- **Start** — najbliższa lekcja, nadchodzące zastępstwa, najnowsze ogłoszenia.
- **Widżety** — „Następna lekcja" (2×2 / 4×2) i „Plan dnia" (4×2–4×4), odświeżane z dzwonkiem.
- **Powiadomienia** — push na bieżąco (FCM) + sprawdzanie w tle co 15 min jako zabezpieczenie.
- **Offline** — wszystko z lokalnej bazy, sieć tylko ją uzupełnia.
- Jasny i ciemny motyw, skróty z ikony aplikacji.

## Budowanie (Arch Linux)

Wymagania: **Java 17** (`sudo pacman -S jdk17-openjdk`), Android SDK (platforma 35),
ok. 10 GB miejsca. Gradle 8.9 nie działa na Javie 23+.

    sudo archlinux-java set java-17-openjdk     # albo JAVA_HOME przy każdym buildzie
    ./gradlew :app:assembleDebug :app:testDebugUnitTest

APK: `app/build/outputs/apk/debug/app-debug.apk`, instalacja: `adb install -r <apk>`.

**Wymagany `app/google-services.json`** (projekt Firebase z aplikacją
`pl.zse.bydgoszcz.elektron`) — bez niego build się nie uda. Konfiguracja push:
`PORADNIK_PUSH.md`.

### Build release

1. Wygeneruj klucz (raz, w katalogu głównym projektu):

       keytool -genkeypair -v -keystore elektron-release.jks -alias elektron \
         -keyalg RSA -keysize 4096 -validity 10000

2. Skopiuj `keystore.properties.example` do `keystore.properties` i wpisz hasła.
   Oba pliki (`*.jks`, `keystore.properties`) są w `.gitignore`.
3. `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

**Zrób kopię zapasową klucza i haseł.** Bez tego samego klucza nie wydasz aktualizacji —
użytkownicy musieliby odinstalować aplikację.

## Stack

Kotlin 2.0.20 · Jetpack Compose + Material 3 · Hilt · Room · DataStore · WorkManager ·
OkHttp + Jsoup · Coil · Glance (widżety) · Firebase Cloud Messaging.
Architektura: MVVM + Clean (data / domain / presentation).

## Dane i zasady

| Źródło | Kodowanie |
|---|---|
| plan.zse.bydgoszcz.pl | UTF-8 |
| zastepstwa.zse.bydgoszcz.pl | ISO-8859-2 (wymuszone) |
| zse.bydgoszcz.pl (RSS) | UTF-8 |

Tylko publiczne strony szkoły, bez logowania i danych osobowych, z poszanowaniem
robots.txt. Zapytania warunkowe (cache HTTP) — niezmieniona strona to krótka odpowiedź 304.

## Licencja

Copyright (C) 2025–2026 Stanisław Olejnik i współtwórcy.
GNU GPL v3.0 lub nowsza — patrz `LICENSE`.

Projekt inspirowany aplikacją Atom (Apache 2.0), nie zawiera jej kodu —
patrz `NOTICE` i `LICENSE-APACHE`.
