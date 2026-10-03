<div align="center">

<img src="docs/logo.png" alt="eLektron" width="112">

# eLektron

**Plan lekcji, zastępstwa i ogłoszenia Zespołu Szkół Elektronicznych w Bydgoszczy - w jednej aplikacji na Androida.**

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?include_prereleases&label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz najnowszą wersję**](https://github.com/stasolejnik/elektron/releases)

</div>

## O projekcie

**Skąd pomysł.** eLektron powstał z inspiracji aplikacją
[Atom](https://github.com/kacpergorka/atom), którą
[Kacper Górka](https://github.com/kacpergorka) stworzył dla uczniów i nauczycieli ZSE na
urządzenia Apple (iOS, iPadOS, macOS). Dziękujemy za ten pomysł! eLektron to osobny,
niezależny projekt na Androida, napisany od zera - nie jest kopią ani rozszerzeniem Atomu,
nie zawiera jego kodu i nie korzysta z jego serwerów. Dane pobiera bezpośrednio
z publicznych stron szkoły.

**Kto to napisał.** Cały kod (100%) napisała sztuczna inteligencja - Claude od firmy Anthropic.
Autorem projektu jest Stanisław Olejnik, uczeń Zespołu Szkół Elektronicznych w Bydgoszczy:
wymyślił funkcje, testował aplikację na swoim telefonie i zgłaszał, co poprawić. Sam nie
napisał ani linijki kodu i nie jest programistą - interesuje się Linuksem. Kod jest otwarty,
więc każdy może go przejrzeć, a błędy można [zgłaszać](#mam-problem-albo-pomysł).

**Stan:** wersja kandydująca do 1.0.0 (tzw. *release candidate*, „rc”) - prawie gotowa,
ostatnie poprawki przed pierwszym pełnym wydaniem.

---

## Co potrafi

- **Plan lekcji** - dzień albo cały tydzień, z naniesionymi zastępstwami. Dotknij lekcji, żeby
  zobaczyć szczegóły (nauczyciel, sala, godziny, zastępstwo).
- **Zastępstwa** - tylko te, które dotyczą Twojej klasy i Twoich grup. Dotknięcie zastępstwa
  otwiera tę lekcję w planie.
- **Ogłoszenia szkoły** - także starsze, z wyszukiwaniem.
- **Strona główna** - trwająca albo najbliższa lekcja, przerwa i nadchodzące zmiany.
- **Powiadomienia** o nowych zastępstwach i ogłoszeniach.
- **Przypomnienia przed lekcją** i ciche godziny, gdy nie chcesz nic dostawać.
- **Widżety na ekran główny**: Następna lekcja, Plan dnia, Zastępstwa, a także kafelek
  w szybkich ustawieniach.
- **Działa bez internetu** - pokazuje ostatnio pobrane dane.
- Własne nazwy i kolory przedmiotów, jasny i ciemny motyw, kolor akcentu.

**Dla kogo?** Dla uczniów ZSE w Bydgoszczy (i każdego, kto chce szybko zajrzeć do planu
którejś klasy). To **nieoficjalna** aplikacja - nie jest powiązana ze szkołą ani z firmą VULCAN.
Nie wymaga logowania ani konta.

## Jak zainstalować

APK to plik instalacyjny aplikacji na Androida (tak jak `.exe` na Windowsie).

1. Na telefonie wejdź na stronę [Releases](https://github.com/stasolejnik/elektron/releases)
   i przy najnowszej wersji, w sekcji **Assets**, pobierz plik `eLektron-….apk`.
2. Otwórz pobrany plik. Android zapyta, czy pozwolić przeglądarce (albo menedżerowi plików)
   instalować aplikacje - zezwól. To normalne przy aplikacjach spoza Sklepu Play.
3. Zainstaluj i uruchom eLektron.

**Aktualizacje:** gdy pojawi się nowa wersja, aplikacja sama o tym poinformuje na stronie
głównej - wystarczy przycisk, a eLektron pobierze ją i otworzy instalator. Ustawienia zostają.
Ręcznie możesz to sprawdzić w **Ustawienia → Aktualizacje → Sprawdź aktualizacje**.

## Pierwsze uruchomienie

1. **Wybierz klasę** - np. `2D`. Zmienisz ją później w **Ustawienia → Klasa**.
2. **Wybierz swoje grupy.** Na niektórych lekcjach klasa dzieli się na grupy: np. WF
   (klasa ćwiczy w dwóch grupach), języki obce (grupa 1 i grupa 2, albo różne języki),
   zajęcia praktyczne czy religia. Gdy wybierzesz swoje grupy, w planie i w zastępstwach
   zobaczysz tylko **swoje** lekcje. Zmienisz to w **Ustawienia → Grupy zajęciowe**.

Gotowe - plan pobierze się sam.

## Powiadomienia - jak działają

Synchronizacja to pobranie świeżych danych ze stron szkoły. eLektron robi ją sam w tle mniej
więcej co 15 minut, a w wersji z GitHuba dostaje też powiadomienia push - krótkie
wiadomości wysyłane na telefon, gdy na stronie szkoły pojawi się nowe zastępstwo albo
ogłoszenie.

**Dlaczego czasem z opóźnieniem?** Android oszczędza baterię: usypia aplikacje działające
w tle i grupuje ich pracę, więc powiadomienie może przyjść kilka, a czasem kilkanaście minut
po zmianie na stronie szkoły. Przypomnienia przed lekcją korzystają z zegara telefonu - gdy
telefon nie pozwala na dokładne alarmy albo mocno oszczędza baterię, mogą się spóźnić.

**Powiadomienia nie przychodzą?**

1. Sprawdź, czy są włączone w **Ustawienia → Powiadomienia** w eLektronie i w ustawieniach
   Androida dla eLektronu.
2. W **Ustawienia → Działanie w tle** wybierz **Wyłącz optymalizację baterii** (ustaw
   eLektron na „Bez ograniczeń”). Obok jest też **Poradnik dla Twojego telefonu** - niektórzy
   producenci (np. Xiaomi, Huawei, Samsung) usypiają aplikacje szczególnie mocno.
3. Dane możesz zawsze odświeżyć ręcznie: na stronie głównej przeciągnij palcem w dół.

## Pytania i odpowiedzi

**Skąd są dane?**
Wyłącznie z publicznych stron szkoły: planu lekcji, zastępstw i ogłoszeń (szczegóły
w [Źródłach danych](#źródła-danych)). eLektron niczego nie wymyśla - jeśli czegoś nie ma na
stronie szkoły, nie będzie też w aplikacji.

**Czy aplikacja zbiera moje dane?**
Nie. Nie zakładasz konta, nie podajesz imienia ani hasła. Aplikacja nie ma reklam ani
statystyk, a ustawienia zostają na Twoim telefonie. Szczegóły:
[polityka prywatności](PRYWATNOSC.md).

**Dlaczego nie widzę jakiegoś zastępstwa?**
Najczęściej dotyczy ono innej grupy niż Twoja (np. drugiej grupy językowej). Takie zastępstwa
są ukryte - w zakładce Zastępstwa możesz je pokazać przyciskiem **Pokaż zastępstwa innych
grup**. Sprawdź też, czy masz dobrze wybrane grupy w **Ustawienia → Grupy zajęciowe**.
Zastępstwa, które już minęły, znikają z listy, ale zostają w planie lekcji.

### Mam problem albo pomysł

- W aplikacji: **Ustawienia → Zgłoś problem**. Przygotuje raport techniczny (wersja aplikacji
  i Androida, model telefonu, ostatnie logi aplikacji - bez danych osobowych), który możesz
  wysłać e-mailem albo dołączyć do zgłoszenia.
- Na GitHubie: [Issues](https://github.com/stasolejnik/elektron/issues).
- E-mail: **[kontakt.elektron@pm.me](mailto:kontakt.elektron@pm.me)**

Po awarii aplikacja sama zaproponuje wysłanie raportu.

---

## Dla zainteresowanych technicznie

### Technologie

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · WorkManager · Glance
(widżety) · OkHttp · Jsoup. Minimalna wersja: Android 8.0.

### Struktura kodu

| Katalog `app/src/main/kotlin/pl/zse/bydgoszcz/elektron/` | Zawartość |
|---|---|
| `data/remote` | pobieranie i parsowanie stron szkoły (plan, zastępstwa, RSS) |
| `data/local`, `data/repository` | baza Room, ustawienia (DataStore), repozytoria |
| `domain` | modele i czysta logika (grupy, zastępstwa, zegar lekcji), koordynator synchronizacji |
| `presentation` | ekrany w Compose (strona główna, plan, zastępstwa, ogłoszenia, ustawienia) |
| `work` | synchronizacja w tle, powiadomienia, przypomnienia |
| `widget` | widżety (Glance) i kafelek szybkich ustawień |

Kod zależny od wariantu leży w `app/src/gms/` i `app/src/foss/`.

### Warianty

| Wariant | Różnice |
|---|---|
| `gms` | wersja z GitHub Releases: powiadomienia push (Firebase Cloud Messaging) i sprawdzanie aktualizacji |
| `foss` | bez usług Google, tylko wolne zależności; powiadomienia wyłącznie z synchronizacji w tle |

### Budowanie i testy

Wymagania: JDK 17, Android SDK.

```sh
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

Wariant `gms` wymaga pliku `app/google-services.json` (projekt Firebase; nie ma go
w repozytorium - patrz [PORADNIK_PUSH.md](PORADNIK_PUSH.md)). Podpisane wydania korzystają
z `keystore.properties` - wzór w [keystore.properties.example](keystore.properties.example).
Historia zmian: [CHANGELOG.md](CHANGELOG.md).

### Synchronizacja i powiadomienia

Każda synchronizacja (WorkManager co 15 minut, wybór klasy, przyciski odświeżania)
przechodzi przez jeden koordynator, który pobiera dane, zapisuje je w bazie i wysyła
powiadomienia o nowych zastępstwach. Push w wariancie `gms` wysyła osobna usługa
[elektron-push-watcher](https://github.com/stasolejnik/elektron-push-watcher), która co kilka
minut sprawdza strony szkoły i powiadamia przez wspólne tematy FCM - bez serwera aplikacji
i bez żadnych danych użytkowników.

### Źródła danych

Dane pochodzą wyłącznie z publicznych stron szkoły, pobieranych z poszanowaniem `robots.txt`:

| Dane | Źródło |
|---|---|
| Plan lekcji i lista klas | [plan.zse.bydgoszcz.pl](https://plan.zse.bydgoszcz.pl) (VULCAN Optivum) |
| Zastępstwa | [zastepstwa.zse.bydgoszcz.pl](https://zastepstwa.zse.bydgoszcz.pl) |
| Ogłoszenia | kanały RSS [zse.bydgoszcz.pl](https://zse.bydgoszcz.pl) |

Poza stronami szkoły wariant `gms` łączy się tylko z usługą powiadomień push i z GitHubem
(sprawdzanie aktualizacji) - patrz [polityka prywatności](PRYWATNOSC.md).

## Licencja

Copyright © 2026 Stanisław Olejnik

eLektron jest wolnym oprogramowaniem: możesz go rozpowszechniać i modyfikować na warunkach
[GNU General Public License](LICENSE) w wersji 3 lub (według Twojego wyboru) dowolnej
późniejszej. Aplikacja jest napisana od podstaw i nie zawiera kodu aplikacji Atom -
szczegóły w [NOTICE](NOTICE).

Projekt niezależny - niepowiązany z Zespołem Szkół Elektronicznych w Bydgoszczy
ani z firmą VULCAN.
