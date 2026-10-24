<div align="center">

<img src="docs/logo.png" alt="eLektron" width="112">

# eLektron

**Plan lekcji, zastępstwa, ogłoszenia i przejazdy do szkoły i ze szkoły w jednej aplikacji na Androida dla uczniów ZSE w Bydgoszczy.**

[![Wydanie](https://img.shields.io/github/v/release/stasolejnik/elektron?label=wydanie)](https://github.com/stasolejnik/elektron/releases)
[![Build i testy](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml/badge.svg)](https://github.com/stasolejnik/elektron/actions/workflows/ci.yml)
[![Licencja: GPL v3+](https://img.shields.io/badge/licencja-GPL--3.0--or--later-blue)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[**Pobierz aplikację (.apk)**](https://github.com/stasolejnik/elektron/releases/download/v1.0.2/eLektron-1.0.2.apk)

</div>

## O aplikacji

eLektron zbiera informacje potrzebne w szkolnym dniu: plan Twojej klasy i grup, zastępstwa,
ogłoszenia oraz połączenia komunikacji miejskiej ze szkoły. Nie wymaga konta. Pobrane dane
szkolne i zapisane ulubione ogłoszenia można odczytać bez internetu.

To nieoficjalny projekt ucznia, niezwiązany ze szkołą ani z firmą VULCAN. Aplikacja jest
inspirowana [Atomem](https://github.com/kacpergorka/atom) [Kacpra Górki](https://github.com/kacpergorka)
na urządzenia Apple, ale została napisana od podstaw i nie zawiera jego kodu.
Kod tworzą Claude (Anthropic) i ChatGPT (OpenAI); ja wybieram funkcje i testuję aplikację.

## Zrzuty ekranu

<p align="center">
  <img src="docs/screenshots/strona-glowna.png" alt="Strona główna" title="Strona główna" width="23%">
  <img src="docs/screenshots/plan.png" alt="Plan lekcji" title="Plan lekcji" width="23%">
  <img src="docs/screenshots/zastepstwa.png" alt="Zastępstwa" title="Zastępstwa" width="23%">
  <img src="docs/screenshots/odjazdy.png" alt="Przejazdy" title="Przejazdy" width="23%">
</p>
<p align="center">
  <img src="docs/screenshots/ogloszenia.png" alt="Ogłoszenia" title="Ogłoszenia" width="23%">
  <img src="docs/screenshots/ustawienia.png" alt="Ustawienia" title="Ustawienia" width="23%">
  <img src="docs/screenshots/widzety.png" alt="Widżety" title="Widżety" width="23%">
  <img src="docs/screenshots/powiadomienia.png" alt="Powiadomienia" title="Powiadomienia" width="23%">
</p>

## Funkcje

- **Plan lekcji:** widok dnia i tygodnia z zastępstwami, szczegóły sali i nauczyciela,
  wybór własnych grup zajęciowych oraz udostępnianie dnia lub tygodnia.
- **Notatki do lekcji:** przytrzymaj przyszłą lekcję, aby dodać notatkę. Możesz ustawić
  przypomnienie przed lekcją albo dzień wcześniej. Notatki są też widoczne w widżetach.
- **Zastępstwa:** zmiany dotyczące Twojej klasy i grup, informacje szkoły dopisane pod datą
  (np. o oddawaniu krwi, także z obrazkiem) z godziną ostatniej aktualizacji strony, przejście do lekcji w planie oraz udostępnianie pojedynczego
  zastępstwa lub całej widocznej listy.
- **Ogłoszenia:** wyszukiwanie, czytanie treści w aplikacji i ulubione z zapisem do odczytu
  bez internetu.
- **Przejazdy:** autobusy i tramwaje między Twoim przystankiem a szkołą. Rano dojazd na
  najbliższą lekcję (kiedy będziesz w szkole, ile zapasu, ostatni kurs, którym zdążysz),
  od połowy dnia lekcji powrót ze szkoły (kiedy wyjść, żeby zdążyć dojść). Opóźnienia na żywo,
  gdy pojazd jest śledzony, linia, kierunek i czas dojścia. Przystanki wybierzesz przez
  wyszukiwarkę lub mapę OpenStreetMap, bez GPS; kierunek zmienisz też ręcznie.
- **Strona główna:** trwająca lub najbliższa lekcja, przerwa i nadchodzące zmiany.
- **Powiadomienia:** nowe zastępstwa, ogłoszenia i informacje szkoły (np. skrócone lekcje), przypomnienia o lekcjach i notatkach,
  ciche godziny oraz powiadomienie na żywo w dni lekcyjne (odliczanie do przerwy albo lekcji, także na pasku stanu w Androidzie 16+, co trwa, co dalej i kiedy koniec lekcji).
- **Szybki dostęp:** widżety Następna lekcja, Plan dnia i Zastępstwa, kafelek szybkich
  ustawień oraz skróty po przytrzymaniu ikony aplikacji.
- **Wygląd:** własne nazwy i kolory przedmiotów, jasny lub ciemny motyw, kolor akcentu,
  kolor tła (gotowy albo własny), kolory z tapety na Androidzie 12 lub
  nowszym i wybór ikony aplikacji.

Przejazdy są domyślnie wyłączone - włączysz je w **Ustawieniach → Przejazdy** (zakładka i karta
na stronie głównej niezależnie). Szkoła to **Mieczysława Karłowicza 20 w Bydgoszczy**. Godziny
pochodzą z rozkładu, opóźnienia z tablic przystanków BUSearch, a dojście ze szkoły jest
wyznaczane ulicami i ścieżkami. Kierunek przełącza się sam w połowie dnia lekcji (bez lekcji
o 12:00). Sprawdzanie połączeń wymaga internetu; funkcja nie korzysta z lokalizacji telefonu.

## Ustawienia

Na górze zakładki **Ustawienia** jest karta klasy i grup, pod nią kategorie - każda otwiera
własną stronę, a po powrocie lista jest przewinięta tam, gdzie była. Kolejność jak w aplikacji.

| Kategoria | Co ustawisz |
|---|---|
| **Klasa i grupy** (karta na górze) | Klasa (zmiana wymaga potwierdzenia) i **grupy zajęciowe** - języki, WF, religia i inne podziały; przy przedmiotach z trzema grupami i więcej można wybrać kilka („Wybierz kilka grup”). Plan, strona główna, widżety i powiadomienia pokazują wtedy tylko Twoje lekcje. |
| **Powiadomienia** | Nowe zastępstwa, nowe ogłoszenia (domyślnie wyłączone) i **informacje szkoły** dopisane na stronie zastępstw, np. skrócone lekcje (domyślnie włączone). **Powiadomienia na żywo** (domyślnie wyłączone) - od kwadransa przed pierwszą lekcją do końca ostatniej: odliczanie do przerwy w trakcie lekcji i do lekcji w przerwie, co trwa i co dalej, zastępstwa i notatki; ciche, można je ukryć do końca dnia. **Ciche godziny** - powiadomienia w wybranych godzinach przychodzą bez dźwięku i wibracji, ale nie znikają. **Przypomnienia o lekcjach** (domyślnie wyłączone; punktualne wymagają zgody na alarmy o czasie) oraz **notatki do lekcji**: lista notatek i przypomnienia dzień wcześniej o wybranej godzinie albo kilka minut przed lekcją. |
| **Strona główna** | **Ekran startowy** - co otwiera się po uruchomieniu: automatycznie (w czasie lekcji plan, poza nimi strona główna), strona główna, plan, zastępstwa albo Przejazdy. Karty na stronie głównej, w tym **Czas do końca lekcji** (domyślnie wyłączony, także linia w widżecie Plan dnia) i **Przejazdy**. |
| **Wygląd** | Motyw jasny, ciemny albo jak w systemie, **kolor akcentu**, **kolor tła** (Klasyczny, Grafit, Ciepły, Morski, Leśny albo własny - odcień i nasycenie tła i kart), kolory z tapety (Android 12+) i **ikona aplikacji**: domyślna albo logo na białym tle. Po zmianie ikony aplikacja się zamyka. |
| **Plan lekcji** | Własne nazwy i kolory przedmiotów, pokazywanie sali i nauczyciela. |
| **Przejazdy** | Zakładka Przejazdy i karta na stronie głównej (niezależne, domyślnie wyłączone), **Twój przystanek**, **przystanek przy szkole** (domyślnie „Szkoła” - najbliższe przystanki; można wybrać dowolny, lista od najbliższego szkoły) i połączenia z przesiadką. |
| **Pasek nawigacji** | Które zakładki są na dolnym pasku i w jakiej kolejności (przytrzymaj i przeciągnij). |
| **Widżety** | Przezroczystość tła oraz sala i nauczyciel w widżetach. W motywie Auto przy dużej przezroczystości kolor tekstu dopasowuje się do tapety. |
| **Praca w tle** | Stan optymalizacji baterii i poradnik dla Twojego telefonu - przydatne, gdy powiadomienia lub widżety się spóźniają. |
| **Aktualizacje** | Wersja z GitHuba: **kanał** „Stabilne” (domyślnie) albo „Beta” (także wersje testowe), **powiadomienia o nowej wersji** (domyślnie wyłączone) i ręczne sprawdzenie aktualizacji. |
| **Dane** | Wyczyszczenie danych podręcznych - plan, zastępstwa i ogłoszenia pobiorą się od nowa; ustawienia, notatki i ulubione zostają. |
| **O aplikacji** | Wersja, zgłaszanie problemów z raportem, kontakt, kod źródłowy, polityka prywatności i licencja GPL v3. |

Skróty po przytrzymaniu ikony aplikacji: Plan lekcji, Zastępstwa, Ogłoszenia, Strona główna
i - przy włączonej zakładce - Przejazdy.

**Dni wolne:** strona szkoły pokazuje stały plan także w święta i przerwy. eLektron zna święta
ustawowe, przerwy świąteczne, wakacje i ferie zimowe w kujawsko-pomorskim (2026/2027: 15-28 lutego) -
w takie dni nie ma przypomnień, powiadomienia na żywo ani „następnej lekcji”, a plan dnia mówi, że to
dzień wolny. Dni wolne ustalane przez dyrektora szkoły nie są znane z góry.

**Nowy rok szkolny:** gdy na stronie szkoły zmieni się lista klas i Twojej klasy nie ma albo jej
miejsce zajęła inna, aplikacja poprosi o ponowny wybór klasy zamiast pokazywać cudzy plan.

## Instalacja i aktualizacje

1. Pobierz [**eLektron-1.0.2.apk**](https://github.com/stasolejnik/elektron/releases/download/v1.0.2/eLektron-1.0.2.apk) (wszystkie wersje: [wydania na GitHubie](https://github.com/stasolejnik/elektron/releases)).
2. Otwórz pobrany plik na telefonie.
3. Jeśli Android poprosi o zgodę na instalację z przeglądarki lub menedżera plików,
   zezwól na instalowanie z tego źródła.
4. Przy pierwszym uruchomieniu wybierz klasę i swoje grupy zajęciowe.

Aplikacja wymaga **Androida 8.0 lub nowszego**. Kolejne wersje instaluj jako aktualizację,
bez odinstalowywania - zachowasz ustawienia, notatki i ulubione. W wydaniu z GitHuba
aktualizacje można sprawdzić również w aplikacji, w **Ustawieniach → Aktualizacje**
(domyślnie tylko wersje stabilne; kanał „Beta” obejmuje też wersje testowe). Tam też włączysz
powiadomienie o nowej wersji.

Wydania w F-Droid są budowane i podpisywane przez F-Droid, a wydania z GitHuba moim kluczem.
Nie da się zainstalować jednej wersji na drugą - przy zmianie źródła trzeba odinstalować
aplikację, co usuwa ustawienia, notatki i ulubione.

## Powiadomienia

Android może opóźniać pracę aplikacji w tle. Jeśli powiadomienia nie przychodzą,
sprawdź zgodę na powiadomienia oraz kategorię **Praca w tle** w Ustawieniach eLektronu.
Przypomnienia o lekcjach korzystają także z uprawnienia do dokładnych alarmów.

Dane szkolne są sprawdzane w tle przez WorkManager; wariant z GitHuba korzysta również
z powiadomień Firebase Cloud Messaging. Informacje o zastępstwach i ogłoszeniach pochodzą
ze stron szkoły. Przejazdy są sprawdzane podczas korzystania z tej funkcji.

## Prywatność

Aplikacja nie wymaga konta i nie zawiera reklam ani analityki. Ustawienia, notatki i ostatnie
wybrane przystanki są przechowywane lokalnie. Przejazdy nie wymagają GPS: zapytania dotyczą
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
OkHttp, Jsoup i osmdroid. Wymagania: JDK 17 lub nowszy i Android SDK; minSdk 26.

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
