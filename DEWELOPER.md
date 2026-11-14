# eLektron dla dewelopera

Jak zbudować i rozwijać aplikację, wydawać nowe wersje i co zrobić, gdy zmieni się strona szkoły
albo API BUSearch. Instrukcja dla użytkowników: [INSTRUKCJA.md](INSTRUKCJA.md).

## Spis treści

1. [Środowisko](#środowisko)
2. [Struktura projektu](#struktura-projektu)
3. [Budowanie i testy](#budowanie-i-testy)
4. [Zasady w repozytorium](#zasady-w-repozytorium)
5. [Wydanie nowej wersji](#wydanie-nowej-wersji)
6. [F-Droid](#f-droid)
7. [Powiadomienia push](#powiadomienia-push)
8. [Gdy szkoła zmieni stronę](#gdy-szkoła-zmieni-stronę)
9. [API BUSearch (Przejazdy)](#api-busearch-przejazdy)
10. [Do zrobienia co roku](#do-zrobienia-co-roku)
11. [Rozwiązywanie problemów](#rozwiązywanie-problemów)

## Środowisko

- **JDK 17 lub nowszy** (CI używa 21; kod kompiluje się pod Javę 17) i **Android SDK** z compileSdk 36.
  Ścieżkę do SDK wpisz w `local.properties` (`sdk.dir=...`) - Android Studio robi to samo.
- Po sklonowaniu włącz hook sprawdzający, czy nie commitujesz plików lokalnych:

  ```sh
  git config core.hooksPath .githooks
  ```

- Pliki **tylko lokalne**, nigdy w repozytorium: `local.properties`, `keystore.properties`, `*.jks`,
  `app/google-services.json` i klucze konta usługi Firebase (są w `.gitignore`).

## Struktura projektu

Jeden moduł `app`, minSdk 26, Kotlin + Jetpack Compose. Kod wspólny jest w `app/src/main/`,
zależny od wariantu w `app/src/gms/` i `app/src/foss/`.

| Wariant | Dystrybucja | Różnice |
|---|---|---|
| `gms` | GitHub Releases | Firebase Cloud Messaging (push), sprawdzanie i instalowanie aktualizacji z GitHuba |
| `foss` | F-Droid | Bez usług Google: tylko synchronizacja w tle, bez aktualizacji w aplikacji |

Pakiety w `app/src/main/kotlin/pl/zse/bydgoszcz/elektron/`:

| Pakiet | Co zawiera |
|---|---|
| `data/remote/sources/zse` | `SchoolEndpoints` (wszystkie adresy szkoły) i źródła: plan, zastępstwa, ogłoszenia |
| `data/remote/parser` | Parsery stron szkoły: `OptivumListaParser` (lista klas), `OptivumTimetableParser` (plan), `ZastepstwaParser` (zastępstwa i informacje szkoły), RSS i archiwum ogłoszeń |
| `data/repository` | Repozytoria (Room, DataStore), Przejazdy: `TransitJourneyRepository` (planer), `TransitLiveRepository` (tablice odjazdów i pojazdy), `TransitStopCatalog` |
| `data/local` | Room: encje, DAO, migracje (`DatabaseMigrations`); schematy w `app/schemas/` |
| `domain/model` | Logika bez Androida: kalendarz szkolny, reguła „lekcja się odbywa” (`SubstitutionDisplay.takesPlace`), `LessonClock`, Przejazdy, `MoreMenu` |
| `domain/sync` | `SyncCoordinator` - jedyne miejsce synchronizacji ze szkołą; `BackgroundSyncPolicy` - kiedy sprawdzać w tle |
| `work` | WorkManager, przypomnienia, powiadomienie na żywo (`LiveLessonScheduler`), powiadomienia |
| `widget` | Widżety Glance i kafelek; dane widżetów liczone w osobnych obiektach (`WidgetData`, `TransitWidgetData`) |
| `presentation` | Ekrany Compose i ViewModele; dolny pasek z „Więcej” w `common/ElektronBottomBar.kt` |

**Przewodnik po aplikacji** (`presentation/common/Tour.kt`): kroki w `tourSteps()`, a element do
podświetlenia oznacza się `Modifier.tourTarget("klucz")` w miejscu, gdzie jest rysowany. Krok z `route`
sam przełącza zakładkę; dla list (Ustawienia) trzeba dodać przewinięcie do elementu, jak w
`SettingsHome`. Przewodnik pokazuje się po świeżej instalacji (`tourPending` w ustawieniach) i z
O aplikacji. Tam też jest „Poleć aplikację” (`settings/RecommendApp.kt`, kod QR z biblioteki
`io.nayuki:qrcodegen`).

## Budowanie i testy

```sh
./gradlew :app:testFossDebugUnitTest :app:testGmsDebugUnitTest   # testy obu wariantów
./gradlew :app:lintFossDebug :app:lintGmsDebug                    # lint (0 błędów)
./gradlew :app:assembleFossDebug                                  # debug bez Firebase
./gradlew :app:assembleGmsRelease                                 # wydanie z GitHuba (podpisane)
```

- Testy działają w strefie **Europe/Warsaw**, a kalendarz dni wolnych jest w nich wyłączony
  (`app/build.gradle.kts`, `tasks.withType<Test>`). Testy kalendarza włączają go same.
- Wiele testów korzysta z **prawdziwych stron szkoły** w `app/src/test/resources/` (zastępstwa
  w ISO-8859-2, plany, lista klas) i zapisanych odpowiedzi BUSearch (`transit/`).
- Wariant `gms` wymaga `app/google-services.json` (patrz [Powiadomienia push](#powiadomienia-push)).
- CI (`.github/workflows/ci.yml`) przy każdym pushu uruchamia testy i build `foss`, a
  `repository-hygiene.yml` - sprawdzenie plików (`.github/check_repository_files.py`).
- **Przed pushem do `main`** uruchom testy obu wariantów lokalnie.

## Zasady w repozytorium

- Praca bezpośrednio na `main`, bez gałęzi.
- Teksty w aplikacji i dokumentacji po polsku, bez długich myślników (tylko zwykły „-”), bez
  wyróżniania zwykłych treści czerwienią.
- Dokumentacja (`*.md`) bez danych osobowych: bez prawdziwych nazwisk nauczycieli, konkretnej klasy,
  dat testów czy prywatnych przystanków - przykłady ogólne („J. Kowalski”). Prawdziwe dane mogą być
  w testach (strony szkoły jako dane testowe).
- Publiczny kontakt to wyłącznie **elektron@tuta.com** (`CrashReport.CONTACT_EMAIL`, README, polityka
  prywatności). Commity podpisuj tym adresem (`git config user.email elektron@tuta.com`).
- Nowe połączenie sieciowe albo nowe dane zapisywane na telefonie = aktualizacja
  [PRYWATNOSC.md](PRYWATNOSC.md).
- `check_repository_files.py` odrzuca: pliki z `.gitignore`, `*.md` w `docs/` (tam tylko grafiki)
  i robocze nazwy (`plan`, `raport`, `audit`, `iter…`, `notatki`). Opisy wydań: tylko
  `release-notes/<wersja>.md`.

## Wydanie nowej wersji

Numer wersji: `versionCode` i `versionName` w `app/build.gradle.kts`. Każde wydanie, także stabilne
po becie, ma wyższy `versionCode` niż poprzednie (np. 1.0.4-beta1 - 40, 1.0.4 - 41): aktualizacja z
aplikacji odrzuca plik z tym samym kodem, a okno „Co nowego” pokazuje wpisy według kodu.

1. **Wersja:** podnieś `versionCode` (o 1) i ustaw `versionName` (`1.0.5`, `1.0.5-beta1`).
2. **Zmiany, od najważniejszych:**
   - `CHANGELOG.md` - sekcja `## [wersja] - RRRR-MM-DD (versionCode)` (do wydania: `niewydane`);
   - `release-notes/<wersja>.md` - krótki opis dla użytkowników (treść wydania na GitHubie);
   - `presentation/common/Changelog.kt` - wpis „Co nowego” w aplikacji (test pilnuje, by najnowszy
     wpis miał `versionCode` z builda);
   - `fastlane/metadata/android/{pl-PL,en-US}/changelogs/<versionCode>.txt` - **najwyżej 500 znaków**
     (F-Droid).
   Podgląd okna „Co nowego”: tryb dewelopera (Ustawienia → O aplikacji, 5 dotknięć logo) →
  „Podgląd „Co nowego””.
3. **Sprawdzenie:** testy obu wariantów, lint, build `foss` i `gms`.
4. **Commit** `Release <wersja>` z datą w CHANGELOG, **push** na `main`.
5. **APK:** `./gradlew :app:assembleGmsRelease`, sprawdź podpis
   (`apksigner verify app/build/outputs/apk/gms/release/app-gms-release.apk`) i skopiuj jako
   `eLektron-<wersja>.apk`.
6. **Tag i wydanie:**

   ```sh
   git tag v1.0.5 && git push origin v1.0.5
   gh release create v1.0.5 eLektron-1.0.5.apk --title "eLektron 1.0.5" --notes-file release-notes/1.0.5.md --latest
   ```

   Wersje testowe: tag `v1.0.5-beta1` i `--prerelease` zamiast `--latest`. Aplikacja z kanałem
   „Beta” widzi je w Ustawieniach → Aktualizacje.
7. **Watcher:** jeśli zmieniał się jego kod - `git pull --rebase`, testy (`python3 -m unittest discover -s tests`), push.

### Podpisywanie wydań

Klucz tworzy się raz i **nie wolno go zgubić** - bez niego nie da się wydać aktualizacji
instalowanej na wierzch poprzedniej wersji z GitHuba.

```sh
keytool -genkeypair -v -keystore elektron-release.jks -alias elektron -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties   # uzupełnij hasła
```

Kopię klucza i haseł trzymaj poza komputerem (np. w menedżerze haseł).

## F-Droid

Przepis budowania jest w repozytorium F-Droid:
[`metadata/pl.zse.bydgoszcz.elektron.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/pl.zse.bydgoszcz.elektron.yml).
Buduje wariant `foss`, a przed buildem usuwa z plików Gradle wtyczkę Google Services i Firebase (`sed`).

- **Aktualizacje są automatyczne:** bot F-Droid wykrywa nowy tag `v<liczby>` (np. `v1.0.5`, bez
  `-beta`) i dopisuje wersję, kopiując ostatni przepis. Każda wersja musi się więc budować z tymi
  samymi `sed`-ami - nie dodawaj zależności Google poza wariantem `gms`.
- Opis, zrzuty i „Co nowego” F-Droid bierze z `fastlane/metadata/android/`.
- Stan budowania: [monitor.f-droid.org](https://monitor.f-droid.org) (szukaj `pl.zse.bydgoszcz.elektron`).
  Nowa wersja pojawia się w F-Droid zwykle kilka dni po tagu.
- Zmiana metadanych (np. adresu autora): Merge Request do `fdroiddata`, zmieniając tylko potrzebną
  linię (nie ruszaj bloku `Builds` - zarządza nim bot).

Symulacja lokalna (przed tagiem, w osobnym klonie):

```sh
git clone . /tmp/fdroid-test && cd /tmp/fdroid-test
sed -i -e '/google.services/d' build.gradle.kts
sed -i -e '/firebase/d' -e '/com.google.gms.google-services/d' app/build.gradle.kts
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleFossRelease
```

## Powiadomienia push

Tylko wariant `gms`. Push skraca czas, po którym telefon dowiaduje się o nowym zastępstwie albo
ogłoszeniu (z ok. 15 minut do kilku). Push jest tylko **sygnałem** - aplikacja sama sprawdza stronę
szkoły i decyduje, czy pokazać powiadomienie (klasa, grupy, ustawienia). Synchronizacja w tle działa dalej.

| Element | Gdzie | Rola |
|---|---|---|
| Firebase Cloud Messaging | konsola Firebase | dostarcza wiadomości na telefony (tematy `elektron-subs-zse-bydgoszcz`, `elektron-anns-zse-bydgoszcz`) |
| Watcher | [elektron-push-watcher](https://github.com/stasolejnik/elektron-push-watcher) | GitHub Actions: sprawdza stronę szkoły i RSS, porównuje ze `state.json`, wysyła push |
| Zegar | [cron-job.org](https://cron-job.org) | uruchamia watcher co 5 minut (harmonogram GitHuba odpala go co 15-25 min) |
| Aplikacja | `app/src/gms` | `FcmTopicManager` subskrybuje tematy, `ElektronFirebaseMessagingService` odbiera |

Całość jest darmowa (repozytorium watchera musi być publiczne - wtedy Actions nie mają limitu minut).

### Konfiguracja od zera (np. własny fork)

1. **Firebase:** w console.firebase.google.com utwórz projekt i dodaj aplikację Android
   `pl.zse.bydgoszcz.elektron`. Pobierz `google-services.json` do `app/` (nie commituj).
2. **Klucz konta usługi:** Ustawienia projektu → Konta usługi → „Wygeneruj nowy klucz prywatny”.
   Nie wrzucaj go do żadnego repozytorium.
3. **Watcher:** fork `elektron-push-watcher` (publiczny), włącz Actions, zastąp `state.json` przez `{}`.
4. **Sekret:** w repozytorium watchera Settings → Secrets → Actions: `FCM_SERVICE_ACCOUNT_JSON`
   z całą zawartością pliku klucza.
5. **Pierwszy przebieg:** Actions → „Run workflow”. Pierwszy przebieg tylko zapisuje stan, bez
   powiadomień.
6. **Zegar:** fine-grained token GitHub (tylko repozytorium watchera, uprawnienie Actions: Read and write)
   i zadanie w cron-job.org co 5 minut: `POST https://api.github.com/repos/<LOGIN>/elektron-push-watcher/actions/workflows/watch.yml/dispatches`,
   nagłówki `Authorization: Bearer <TOKEN>`, `Accept: application/vnd.github+json`,
   treść `{"ref":"main"}`. Poprawna odpowiedź: **204**. Zapisz datę wygaśnięcia tokenu.

### Gdy push nie działa

- **Workflow czerwony, „Brak sekretu”:** sekret nie istnieje albo ma inną nazwę.
- **„FCM send błąd (401/403)”:** w sekrecie musi być cały plik JSON klucza, z tego samego projektu
  Firebase co `google-services.json` w aplikacji.
- **cron-job.org: 401** - token wygasł; **404** - zły adres albo brak dostępu; **422** - zła gałąź.
- **Przebiegi tylko co 15-25 min:** nie działa cron-job.org. GitHub wyłącza harmonogram po 60 dniach
  bez commitów - watcher zapisuje `heartbeat.txt` co 25 dni; w razie czego włącz go w zakładce Actions.
- **Zielony workflow, brak powiadomień:** na telefonie musi być APK `gms`, włączone powiadomienia
  i właściwa klasa; błędy subskrypcji w logcat pod tagiem `FcmTopicManager`.

## Gdy szkoła zmieni stronę

Strony szkoły to eksport Optivum (VULCAN). Gdy zmienią wygląd, aplikacja pokazuje ostatni zapisany
plan i komunikat „Strona szkoły zmieniła wygląd” (`SchoolPageChangedException`), a w tle sprawdza
najwyżej co godzinę. Poprawka wymaga nowej wersji aplikacji.

1. **Zapisz nową stronę.** Watcher archiwizuje strony zastępstw w swoim repozytorium (`archiwum/`).
   Plan i listę klas pobierz ręcznie (`curl`). Strona zastępstw jest w **ISO-8859-2** - zapisuj
   oryginalne bajty.
2. **Dodaj ją do testów** (`app/src/test/resources/...`) i napisz test, który na niej pada.
3. **Popraw tylko to, co trzeba:**
   - adresy - wyłącznie w `SchoolEndpoints`;
   - lista klas - `OptivumListaParser` (lista musi mieć klasy, nauczycieli i sale; inaczej jest
     traktowana jako ucięta i nie usuwa klas);
   - plan - `OptivumTimetableParser`;
   - zastępstwa i informacje szkoły - `ZastepstwaParser` (rozpoznaje wiersze po budowie tabeli, nie po
     klasach CSS, które Optivum zmienia przy każdym eksporcie; `hasFooter` wykrywa ucięte strony).
4. **Watcher liczy te same identyfikatory zastępstw** (`watch.py`). Zmiana zasad odczytu zastępstw
   musi trafić do obu repozytoriów - pilnuje tego `WatcherIdCompatibilityTest` (plik `*.watcher.ids`
   wygenerowany przez watcher).
5. Wydaj poprawkę jak najszybciej (wersja z GitHuba trafia do ludzi od razu, F-Droid po kilku dniach).

## API BUSearch (Przejazdy)

Przejazdy korzystają z publicznego API [BUSearch](https://busearch.pl) (autor: Szymon Czaja) w wersji 2.
Wszystkie adresy zaczynają się od `https://api.busearch.pl/v2/cities/bydgoszcz`:

| Zapytanie | Kod | Do czego |
|---|---|---|
| `GET /stops` (z ETag) | `TransitStopCatalog` | katalog stanowisk; nazwy bez numerów słupków (`baseName`), zapis w pamięci podręcznej |
| `POST /journeys` | `TransitJourneyRepository` | planer połączeń (`from`/`to`, `whenMs`, `maxTransfers`, `limit`, `shape`); stronicowanie przez `nextWhenMs` |
| `GET /stops/{id}/departures?line=` | `TransitLiveRepository.departures` | opóźnienia na żywo i `vehicleId` (kursy w ciągu 30 min) |
| `GET /vehicles/{id}` | `TransitLiveRepository.vehicle` | pozycja pojazdu (po dotknięciu kursu, co 10 s) |
| `GET /vehicles/{id}/etas` | `TransitLiveRepository.vehicle` | przystanki pojazdu: minięte, ETA, godziny z rozkładu |
| `POST /journeys` z `"shape": true` | `TransitLiveRepository.tripShape` | przebieg trasy kursu po ulicach: planer pytany o ten kurs od pierwszego do ostatniego przystanku o godzinie z rozkładu |

Uwagi:

- Ostatnie zapytanie to obejście - API nie ma osobnego zapytania o przebieg trasy. Gdy przestanie
  działać, mapa łączy przystanki prostymi odcinkami (nic się nie psuje).
- Dojście ze szkoły do przystanku jest szacowane lokalnie (`TransitWalkingRepository`: odległość × 1,3 /
  75 m/min), bez zewnętrznego planera tras pieszych.
- Widżet Przejazdy pyta planer sam w ciągu dnia (`TransitWidgetRefresher`, limity 5/15 min) - przy
  zmianie tych zasad zaktualizuj [PRYWATNOSC.md](PRYWATNOSC.md).
- **Gdy API się zmieni:** zapisz nowe odpowiedzi jako dane testowe (`app/src/test/resources/transit/`),
  popraw funkcje `parse…` w repozytoriach i testy `Transit*Test`. Pomaga zapytanie do autora BUSearch
  o planowane zmiany. Dawne API v1 działało do końca 2026 r. - aplikacja korzysta już tylko z v2.

## Do zrobienia co roku

- **Ferie zimowe:** MEN ogłasza je z wyprzedzeniem dla województw. Dopisz kujawsko-pomorskie do
  `SchoolCalendar.WINTER_BREAKS` (test `winterBreakIsKnownForTheCurrentSchoolYear` zaczyna padać,
  gdy brakuje terminu na bieżący rok szkolny).
- **Nowy rok szkolny:** po zmianie listy klas aplikacja sama prosi o ponowny wybór. Warto sprawdzić
  nową stronę planu i listę klas (zmiana wyglądu eksportu) przed 1 września.
- **Token zegara watchera** (cron-job.org) - przed datą wygaśnięcia.
- **targetSdk:** Google Play wymaga go co roku wyżej; F-Droid nie, ale nowe API Androida (np. Live
  Updates) wymagają aktualnego compileSdk.

## Rozwiązywanie problemów

- **Lint kończy się „LintError” (wewnętrzny błąd):** stare wyniki częściowe. Usuń
  `app/build/intermediates/lint-cache` oraz katalogi `*lint_partial_results` w `app/build/intermediates`
  (także `unit_test_` i `android_test_`), uruchom lint osobno dla każdego wariantu.
- **„Dispatchers.Main is used concurrently with setting it”:** rzadko pada `TransitDirectionTest`
  albo `TransitStartupTest` przy obciążonym komputerze - uruchom te testy ponownie.
- **Testy zależne od godziny:** strefa jest ustawiona na Europe/Warsaw; testy przejazdów i kalendarza
  podają czas jawnie (`at("07:00")`), nie z zegara systemowego.
- **Zmiana bazy danych:** podnieś `DATABASE_VERSION`, dodaj migrację w `DatabaseMigrations`
  i test w `DatabaseMigrationsTest`; schemat JSON z `app/schemas/` commituj.
- **Widżet nie pokazuje nowych danych:** sesja Glance odświeża dane tylko po zmianie wersji -
  `WidgetUpdater.updateNow()` (wszystkie) albo `updateTransit()` (Przejazdy).
- **Czas na emulatorze:** `adb root` i `adb shell date MMDDhhmmYYYY.ss` (wyłącz wcześniej automatyczny
  czas). W aplikacji jest też symulowany czas w trybie dewelopera (Ustawienia → O aplikacji, 5 dotknięć
  logo).
- **Raport od użytkownika:** „Zgłoś problem” zawiera wersję, telefon, stan uprawnień i logi z tagami
  aplikacji (`Diagnostics.APP_TAGS`). Logi Przejazdów są celowo pominięte (przystanki), a parser
  zastępstw nie loguje nazwisk - nie dodawaj danych osobowych do logów.
