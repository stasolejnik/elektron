<div align="center">

<img src="docs/logo.png" alt="eLektron" width="112">

# eLektron

**Nieoficjalna aplikacja na Androida z planem lekcji, zastępstwami i ogłoszeniami Zespołu Szkół Elektronicznych w Bydgoszczy.**

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?include_prereleases&label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz najnowszą wersję**](https://github.com/stasolejnik/elektron/releases)

</div>

## O projekcie

> **Inspirowane Atomem.** Pomysł na eLektron wziął się z [Atomu](https://github.com/kacpergorka/atom)
> [Kacpra Górki](https://github.com/kacpergorka) - aplikacji dla uczniów i nauczycieli ZSE na
> urządzenia Apple (iPhone, iPad, Mac). Dziękuję za inspirację! eLektron to osobny, niezależny
> projekt na Androida, napisany od zera - nie używa kodu ani API Atomu.
>
> **W 100% napisany przez AI.** Cały kod napisała sztuczna inteligencja (Claude firmy Anthropic).
> Ja, Stanisław Olejnik, uczeń ZSE, wymyślam funkcje, testuję je na telefonie i zgłaszam poprawki,
> ale sam nie programuję (interesują mnie systemy Linux). Kod jest otwarty: każdy może go
> przejrzeć, a błędy zgłosić.
>
> **Nieoficjalny.** Projekt nie jest powiązany ze szkołą ani z firmą VULCAN. Aplikacja jest
> w wersji kandydującej do 1.0.0, czyli prawie gotowej - jeśli coś nie działa, daj znać
> (patrz [Zgłaszanie błędów](#zgłaszanie-błędów)).

## Co to jest

eLektron to aplikacja na telefon z Androidem, która pokazuje w jednym miejscu plan lekcji,
zastępstwa i ogłoszenia szkoły. Dane bierze z oficjalnych stron szkoły, więc są takie same jak
tam - ale wygodniej: z widżetami na ekranie, powiadomieniami o zastępstwach i przypomnieniami
o lekcji. Działa bez internetu (pokazuje ostatnio pobrane dane) i nie wymaga konta.

## Funkcje

- Plan dnia i tygodnia z naniesionymi zastępstwami. Dotknięcie lekcji pokazuje jej szczegóły
  (sala, nauczyciel, zmiana).
- Wybór grup zajęciowych: niektóre lekcje (języki, WF, zajęcia praktyczne, religia) dzielą klasę
  na grupy. Wybierasz swoje, a aplikacja pokazuje tylko Twoje lekcje i zastępstwa.
- Zastępstwa Twojej klasy i grup (zastępstwa innych grup są ukryte i można je pokazać przyciskiem).
  Dotknięcie zastępstwa otwiera tę lekcję w planie.
- Ogłoszenia szkoły z wyszukiwarką.
- Strona główna: trwająca lub najbliższa lekcja (z paskiem, ile zostało do końca), przerwa,
  nadchodzące zmiany.
- Powiadomienia o nowych zastępstwach i ogłoszeniach, przypomnienia przed lekcją, ciche godziny.
- Widżety na ekran główny (Następna lekcja, Plan dnia, Zastępstwa) i kafelek w panelu szybkich
  ustawień.
- Własne nazwy i kolory przedmiotów, motyw jasny/ciemny, kolor akcentu, kolory z tapety
  (Android 12 i nowszy).

## Instalacja

1. Wejdź na stronę [Releases](https://github.com/stasolejnik/elektron/releases) i w najnowszym
   wydaniu, w sekcji **Assets**, pobierz plik `eLektron-<wersja>.apk` (to plik instalacyjny
   aplikacji; jest tylko jeden).
2. Otwórz pobrany plik. Android zapyta o zgodę na instalowanie aplikacji z tego źródła
   (przeglądarki albo menedżera plików) - zezwól. Google Play Protect może pokazać ostrzeżenie,
   że aplikacja jest spoza Sklepu Play - to normalne dla aplikacji instalowanych z pliku.
3. Po pierwszym uruchomieniu wybierz klasę, a potem swoje grupy zajęciowe. Później można je
   zmienić w Ustawieniach (**Klasa** i **Grupy zajęciowe**).

**Aktualizacje:** gdy pojawi się nowa wersja, na stronie głównej będzie przycisk **Aktualizuj**
(albo Ustawienia → Aktualizacje). Twoje ustawienia zostają.

## Powiadomienia

Aplikacja co kilkanaście minut sprawdza stronę szkoły w tle, a dodatkowo szkoła ma serwer, który
co kilka minut wysyła powiadomienie o nowym zastępstwie (to szybsza droga). Mimo to powiadomienia
bywają spóźnione, bo Android oszczędza baterię i wstrzymuje aplikacje działające w tle,
a niektórzy producenci telefonów robią to szczególnie agresywnie (np. Xiaomi, Huawei, Samsung).

Gdy powiadomienia nie przychodzą albo się spóźniają:

1. W Ustawieniach eLektronu sprawdź sekcję **Powiadomienia** oraz uprawnienia aplikacji w systemie.
2. W sekcji **Działanie w tle** wyłącz oszczędzanie baterii dla eLektronu (jest tam też poradnik
   dla konkretnych producentów).
3. Zezwól aplikacji na dokładne alarmy - bez tego przypomnienia o lekcjach mogą przychodzić później.

<details>
<summary>Dla zainteresowanych: jak to działa technicznie</summary>

Synchronizacja w tle przez WorkManager co 15 min (minimum Androida); w wariancie `gms` dodatkowo
push FCM wysyłany przez [elektron-push-watcher](https://github.com/stasolejnik/elektron-push-watcher).
Opóźnienia wynikają z Doze, optymalizacji baterii i ograniczeń nakładek producentów.
Przypomnienia używają `AlarmManager`; bez uprawnienia do dokładnych alarmów mogą przyjść później.

Aktualizacje (wariant `gms`): aplikacja sprawdza GitHub Releases najwyżej co 12 h, pobiera APK,
weryfikuje SHA-256 (gdy wydanie ją podaje), nazwę pakietu i `versionCode`, po czym otwiera
systemowy instalator.

</details>

## Najczęstsze pytania

**Czy to oficjalna aplikacja szkoły?**
Nie. To niezależny projekt ucznia. Dane pochodzą z publicznych stron szkoły, ale szkoła nie ma
z aplikacją nic wspólnego.

**Czy jest na iPhone'a?**
Nie. eLektron jest tylko na Androida. Użytkownicy urządzeń Apple mogą zajrzeć do
[Atomu](https://github.com/kacpergorka/atom), który zainspirował ten projekt.

**Dlaczego nie ma jej w Sklepie Play?**
Aplikacja jest rozpowszechniana jako plik do pobrania ze strony projektu. Instalacja jest
opisana wyżej.

**Skąd aplikacja bierze dane?**
Z publicznych stron szkoły: planu lekcji, zastępstw i kanałów RSS z ogłoszeniami (patrz
[Źródła danych](#źródła-danych)). Jeśli na stronie szkoły czegoś nie ma, nie będzie tego też
w aplikacji.

**Czy aplikacja zbiera moje dane?**
Nie zna ani nie zbiera Twojego imienia, konta ani żadnych danych osobowych. Ustawienia zostają
na telefonie. Szczegóły: [PRYWATNOSC.md](PRYWATNOSC.md).

**Nie widzę zastępstwa, które powinno być.**
Zastępstwo mogło dotyczyć innej grupy niż Twoja - w zakładce Zastępstwa jest przycisk
**Pokaż zastępstwa innych grup**. Możliwe też, że szkoła jeszcze go nie opublikowała albo
aplikacja nie zdążyła odświeżyć danych (pociągnij ekran w dół, żeby odświeżyć).

**Znalazłem błąd. Co robić?**
Użyj przycisku **Zgłoś problem** w Ustawieniach (patrz niżej) albo napisz na
[kontakt.elektron@pm.me](mailto:kontakt.elektron@pm.me).

## Zgłaszanie błędów

Przycisk **Zgłoś problem** w Ustawieniach tworzy raport (wersja aplikacji i Androida, model,
stan uprawnień, ostatnie logi aplikacji, bez danych osobowych) do wysłania e-mailem lub
w [Issues](https://github.com/stasolejnik/elektron/issues). Po awarii aplikacja sama proponuje
raport. Kontakt ze mną: **[kontakt.elektron@pm.me](mailto:kontakt.elektron@pm.me)**.

## Prywatność

Brak kont, reklam, analityki i śledzenia; ustawienia zostają na urządzeniu. Aplikacja łączy
się ze stronami szkoły, a wariant `gms` dodatkowo z Firebase Cloud Messaging (powiadomienia push
na wspólne tematy dla całej szkoły; Google przypisuje telefonowi techniczny identyfikator
potrzebny do ich dostarczania) i z GitHubem (aktualizacje). eLektron nie zbiera ani nie wysyła
żadnych danych osobowych. Szczegóły: [PRYWATNOSC.md](PRYWATNOSC.md).

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
