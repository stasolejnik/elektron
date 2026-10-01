# Changelog

Wszystkie istotne zmiany w eLektronie. Format oparty na
[Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), numeracja wersji zgodna z
[SemVer](https://semver.org/lang/pl/). W nawiasie `versionCode` z Androida.

## [0.6.5-beta] - 2026-09-30 (18)

### Dodane
- Trwająca lekcja w planie: obramowanie, „zostało X min” i pasek postępu (jak na stronie
  głównej i w widżetach).
- Zakładka Zastępstwa: „Pokaż zastępstwa innych grup” - zastępstwa klasy dla grup, do których
  nie należysz, są ukryte, ale można je podejrzeć (przygaszone).

### Zmienione
- Czas do lekcji (strona główna, widżety, kafelek): tylko w przerwie (luka do 30 min) albo
  w ostatnich 30 minutach przed lekcją. W okienku (np. lekcja innej grupy) i przed pierwszą
  lekcją - dopiero 30 min przed nią (widżet pokazywał 60 min).
- Zastępstwo znika z zakładki Zastępstwa, strony głównej i widżetu po końcu SWOJEJ lekcji
  (dawniej z zakładki dopiero po wszystkich lekcjach dnia); w planie lekcji zostaje.

### Naprawione
- W okienku przed zastępstwem strona główna pokazywała „przerwę” z czasem do lekcji
  (np. 52 min) zamiast stanu bez odliczania.
- Powiadomienia o zastępstwach, które już minęły (np. wczorajsze albo poranne odczytane po
  południu) - nie są już wysyłane, także przez push.
- Zastępstwo za lekcję innej grupy wisiało na stronie głównej do końca dnia.
- Pozostałe minuty zaokrąglane w górę („1 min” zamiast „0 min” tuż przed dzwonkiem).

## [0.6.4-beta] - 2026-09-30 (17)

### Naprawione
- **Krytyczne: gubione zastępstwa.** Parser rozpoznawał wiersze strony zastępstw po nazwach
  klas CSS (st7/st10), które Optivum nadaje od nowa przy każdym eksporcie. 30.09.2026 z 32
  zastępstw odczytane zostało 1 - pozostałe nie trafiały ani do aplikacji, ani do powiadomień
  (także push z watchera). Teraz rozpoznawanie po strukturze tabeli, z testem na prawdziwej
  stronie szkoły.

## [0.6.3-beta] - 2026-09-30 (16)

Płynny interfejs: bez migania przy przełączaniu zakładek i otwieraniu ekranów.

### Naprawione
- Przejście do dalszej zakładki (np. ze strony głównej do Ustawień) pokazywało przez ułamek
  sekundy zakładkę pośrednią - teraz krótkie przenikanie; do sąsiedniej - przesunięcie jak dotąd.
- Plan pokazywał najpierw poprzedni dzień, a potem przeskakiwał na dzisiejszy/następny - dzień
  startowy liczony jest z wyprzedzeniem (przy wyjściu z planu), a gdy musi się zmienić na
  ekranie, zmienia się płynnie.
- Widok tygodnia migał widokiem dnia przy otwarciu planu; nazwy i kolory przedmiotów migały
  domyślnymi przy starcie aplikacji.
- Ekrany (strona główna, zastępstwa, ogłoszenia, Ustawienia, grupy) pokazywały przez chwilę stan
  pusty („Brak zastępstw”, przełączniki w domyślnym położeniu) przed właściwymi danymi.
- Kółka i karty ładowania pojawiały się na ułamek sekundy - teraz dopiero, gdy wczytywanie
  trwa dłużej niż 0,4 s.

## [0.6.2-beta] - 2026-09-30 (15)

Poprawki z analizy wszystkich 29 planów oddziałów ZSE (stan z 30.09.2026).

### Zmienione
- Zajęcia łączone bez nauczyciela na stronie (np. WF j2, religia) pokazują w planie, widżetach
  i przypomnieniach „łączona z 1A” albo „grupa łączona”.

### Naprawione
- Brakujące lekcje: komórki planu wpisane w Optivum zwykłym tekstem (np. „St WP1 zaj wojskowe”
  w klasach F, „5B,5D zaj uni”) były pomijane - 19 lekcji w 7 klasach. Są rozpoznawane
  (nauczyciel, sala, oddziały), a „---” traktowane jako brak lekcji.
- „zaj.woj.@”: znak braku sali („@”) doklejał się do nazwy przedmiotu.
- Testy regresji na prawdziwych planach szkoły.

## [0.6.1-beta] - 2026-09-30 (14)

### Dodane
- Wybór grupy WF. Zajęcia łączone z inną klasą (np. „wf-j2 #1AF” w 1F) były dotąd czytane
  jako przedmiot „#1AF”, więc grupa WF nie pojawiała się w wyborze grup. Podział na grupy WF
  jest teraz wykrywany automatycznie.
- Aktualizacje w aplikacji (wersja z GitHuba): „Aktualizuj” pobiera APK z paskiem postępu,
  sprawdza sumę SHA-256, nazwę pakietu i numer wersji, po czym otwiera instalator systemu.
- Anulowanie zmian w wyborze grup w Ustawieniach („Anuluj” / „Zapisz”, pytanie przy wyjściu
  z niezapisanymi zmianami).

### Zmienione
- Przezroczystość widżetów ustawiana suwakiem, od 0 do 100%.
- Strona główna: dotknięcie karty najbliższej lekcji otwiera plan, a zastępstwa - zakładkę
  Zastępstwa.
- Nowa wersja sprawdzana przy każdym powrocie do aplikacji (najwyżej co 12 godzin), nie tylko
  przy jej uruchomieniu; „Później” odkłada informację o wersji na 3 dni zamiast na zawsze.

### Naprawione
- Przedmiot „#1AF” zamiast „wf-j2” w planie klas z zajęciami łączonymi.
- Odbiornik przypomnień z limitem czasu - zawieszona baza nie blokuje systemu.

## [0.6.0-beta] - 2026-09-29 (13)

Pierwsza wersja beta: zestaw funkcji zamrożony do wydania 1.0, dalsze wersje 0.6.x to poprawki.

### Dodane
- Przypomnienia przed lekcją: przed pierwszą lub każdą lekcją, 5-30 minut wcześniej,
  z salą i nauczycielem albo zastępcą; nie wypadają w trakcie poprzedniej lekcji.
- Ciche godziny: powiadomienia w wybranym przedziale przychodzą bez dźwięku i wibracji.
- Własne nazwy i kolory przedmiotów (wspólne dla wszystkich grup przedmiotu) - w planie,
  na stronie głównej i w widżetach.
- Kolor akcentu aplikacji i widżetów: niebieski, fioletowy, różowy, czerwony, zielony albo
  własny kolor z próbnika; odcienie dobierane automatycznie z czytelnym kontrastem.
- Wybór ekranu startowego (automatycznie, strona główna, plan, zastępstwa).
- Widżety: krycie tła, pokazywanie nauczyciela i sali.
- Rozpoznawanie zwolnień z lekcji („Uczniowie zwolnieni do domu”, „przychodzą później”):
  nie liczą się do przypomnień, końca lekcji ani inteligentnego startu.
- Raporty awarii (udostępnij lub zgłoś na GitHubie - nic nie jest wysyłane samo),
  „Zgłoś problem” i sekcja „Działanie w tle” w Ustawieniach.
- Wykrywanie zmiany układu strony szkoły z planem - zapisany plan zostaje nietknięty.
- CI na GitHubie: testy i build wersji F-Droid przy każdym pushu.

### Zmienione
- Plan: dzień startowy (dziś, po lekcjach następny) przy każdym wejściu w plan i powrocie
  do aplikacji; widok tygodnia przewija się do tego dnia; tryb dnia/tygodnia jest pamiętany.
- Plan zawsze w zwartym widoku; strzałki dnia obok przycisku odświeżania.
- Zakładka Zastępstwa: dzisiejsze wpisy znikają po lekcjach (zostają w planie).
- Grupy: w „Dostosuj osobno” najpierw religia i WF.
- Ustawienia uporządkowane; wybór klasy w oknie zamiast listy.
- Jasny motyw: mocniejszy akcent (kontrast 4,8:1), zaznaczenia w kolorze akcentu,
  widoczne wyłączone przełączniki.

### Naprawione
- Przewijanie lekcji dnia przypadkiem przełączało na następny dzień.
- Widżety nie odświeżały się od razu po zmianie ustawień.
- Po powrocie do aplikacji po lekcjach plan nadal pokazywał bieżący dzień zamiast następnego.

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

[0.6.5-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.5-beta
[0.6.4-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.4-beta
[0.6.3-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.3-beta
[0.6.2-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.2-beta
[0.6.1-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.1-beta
[0.6.0-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.0-beta
[0.5.0-alpha]: https://github.com/stasolejnik/elektron/releases/tag/v0.5.0-alpha
