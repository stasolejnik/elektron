# RC7: weryfikacja raportu i wdrożone poprawki

Analiza dotyczyła aktualnego drzewa roboczego po RC6 i pierwszych lokalnych poprawkach RC7.
Zmiany nie stanowią nowego wydania: versionCode 33 i versionName 1.0.0-rc6 pozostają bez zmian.

| Punkt raportu | Rozwiązanie |
|---|---|
| 1. Kopia ulubionych | Wybrane artykuły, metadane i treść offline są zapisywane przez AtomicFile w filesDir. Istniejące ulubione migrują bez usuwania oryginału przed zapisem; plik pozwala odtworzyć je w pustej bazie. Baza podręczna nadal pozostaje poza backupem. |
| 2. Edytor po obrocie | Otwarta lekcja i tekst szkicu są w ViewModelu; SavedStateHandle zapisuje cel i tekst. Odtworzony edytor nie zastępuje szkicu treścią z repozytorium. |
| 3. Zapis grup | Produkcyjne repozytorium zapisuje wszystkie zmiany i zakończenie konfiguracji w jednym DataStore.edit. Błąd trafia do UI, szkic pozostaje, ekran nie zamyka się. Podczas zapisu kolejne zmiany są wstrzymane. |
| 4. Reset archiwum | Usuwanie cache i reset kursora są serializowane z pobieraniem archiwum. Numer generacji resetuje flagi UI i odrzuca opóźniony wynik poprzedniej generacji. Odświeżenie również resetuje paginację. |
| 5. Błędy źródeł | Plan i zastępstwa zachowują osobne opisy błędów w istniejącej tabeli sync_state. Udane pobranie usuwa tylko swój błąd. Klucz planu zawiera identyfikator klasy. Stare wpisy bez opisu też zachowują informację o porażce. |
| 6. Ładowanie strony głównej | Brak znacznika loaded po zakończonej porażce nie oznacza już trwającego pobierania. |
| 7. Widżet zastępstw | Planuje lokalne odświeżenie na koniec znanej lekcji i na północ. Wspólny harmonogram zachowuje wcześniejszy termin zamiast przesuwać go przez późniejszy render innego widżetu. |
| 8. F-Droid/Gradle | Neutralne zadania i opcje nie decydują o uruchamianiu Google Services. W mieszanych wywołaniach zadania Google dla foss są wyłączone. Dostępna jest też jawna właściwość elektron.gms. |
| 9. Dane tygodnia | SharedWeekCache współdzieli jeden łańcuch źródła między odbiorcami tygodnia. Wpisy mają ograniczony czas życia; usunięty wpis działa tylko do zamknięcia istniejących odbiorców. Replay wygasa bez odbiorców, a zmiana klasy emituje stan oczekiwania przed nowymi danymi. |
| 10. Dojście i paginacja | Udana paginacja nie anuluje trwającej partii tras. Jej wyniki są scalane z aktualnymi kursami według przystanków; tylko nowe brakujące dojścia wymagają kolejnej partii. |

## Weryfikacja

Testy regresji obejmują odtworzenie ulubionych do pustej bazy oraz ich usunięcie bez ponownego pojawienia się, zachowanie szkicu w SavedStateHandle, odmowę zapisu całego zestawu grup i zachowanie szkicu w UI, reset kursora po końcu archiwum, błędy planu i zastępstw w obu kierunkach, zakończenie wskaźników ładowania po błędzie, lokalny termin widżetu i zachowanie wcześniejszego zlecenia WorkManager, jedną subskrypcję dla trzech dni oraz sprzątanie po wyrzuceniu tygodnia, a także odpowiedź paginacji wcześniejszą niż odpowiedź dojścia.

Na kopii projektu bez google-services.json sprawdzono graf zadań: zwykłe testy foss, clean + testy foss oraz opcję --rerun. Google Services nie było stosowane w żadnym z tych wywołań. Są to kontrole konfiguracji; pełne testy wykonywano osobno w głównym katalogu projektu.

Nie wykonywano fizycznego transferu między telefonami, wizualnego sprawdzenia obrotu, pomiarów FPS, baterii ani rzeczywistych opóźnień launchera. Testy odtworzenia danych i stanu nie zastępują tych kontroli. Nie używano ADB.

## Kolejne zgłoszenia użytkownika

- Puste miejsca widżetów Plan dnia i Następna lekcja otwierają Plan; widżetu Zastępstwa otwierają Zastępstwa. Kliknięcie konkretnej lekcji zachowuje przejście do jej szczegółów.
- Wybrany przystanek początkowy jest teraz ograniczeniem wyników. Planer otrzymuje udokumentowany stopId osobno dla stanowisk przystanku, z najwyżej dwoma równoczesnymi zapytaniami. Odpowiedzi są dodatkowo filtrowane lokalnie.
- Odjazdy są zawsze chronologiczne. Dłuższe dojście i zmiana dostępności trasy pieszej nie przesuwają kursów po liście. Pierwszy wpis nazywa się „Najbliższy odjazd”.
- Zapytanie nie dodaje sztucznego dwuminutowego progu; kursy w ostatnich dwóch minutach nie są z tego powodu pomijane po odświeżeniu. Lokalna lista odrzuca dopiero odjazdy, których godzina już minęła.
- Regresje sprawdzają odwróconą kolejność godzin, stabilność po uzupełnieniu dojścia, wszystkie stanowiska wybranego przystanku, odrzucenie obcych przystanków i zachowanie kursu za minutę.

Końcowa weryfikacja: 735 testów obu wariantów, zero błędów i pominięć. Numer wersji i changelog wydania pozostają bez zmian; nie przygotowano APK ani publikacji.


## Odjazdy: mniej powtarzanych zapytań i poprawne anulowanie

- Usunięto wyścig między obserwatorem ustawień a ekranem: po zmianie przystanku anulowane są tylko zapytania dotyczące poprzedniego wyboru. Pobieranie rozpoczęte już dla nowego celu pozostaje aktywne. Test odtwarza sytuację, w której ekran reaguje pierwszy; przy poprzednim bezwarunkowym anulowaniu test nie przechodzi.
- Każde stanowisko preferowanego przystanku ma własny kursor kolejnych wyników. „Pokaż więcej” kontynuuje od właściwej godziny i pomija stanowiska, dla których serwer zakończył wyszukiwanie. Kursor jest przekazywany dalej również przez ViewModel i zachowywany podczas uzupełniania dojścia.
- Po błędzie usługi tras pieszych kolejne automatyczne próby obliczenia dojścia są wstrzymywane na 60 sekund. Niedostępna trasa do konkretnego stanowiska jest zapamiętywana przez 5 minut; pozostałe stanowiska można nadal sprawdzać. Te ograniczenia dotyczą tylko dojścia i nie wstrzymują odświeżania odjazdów.
- Ograniczenia używają zegara monotonicznego. Anulowanie przy opuszczeniu zakładki nie jest traktowane jako awaria; ponowne wejście może ponowić pobranie. Pamięć niedostępnych tras ma limit 100 wpisów i nie jest zapisywana na dysku.
- Odpowiedź bez nowych poprawnych tras pieszych nie powoduje zapisu pliku pamięci podręcznej. Poprawne trasy nadal korzystają z istniejącej pamięci dyskowej i nie wymagają ponownego pobierania przez 7 dni.

Testy regresji sprawdzają różne kursory stanowisk, pomijanie wyczerpanych wyników, odzyskanie dojścia po upływie ograniczenia, niezależność innych stanowisk, anulowanie bez blokady ponowienia i kolejność reakcji ekranu oraz obserwatora ustawień. Nie mierzono rzeczywistego zużycia baterii ani danych na telefonie. Numer wersji pozostaje bez zmian; bez APK i publikacji.

Weryfikacja tej rundy: 374 testów FOSS i 377 testów GMS (751 łącznie), zero błędów i pominięć.


## Końcowy przegląd RC7

- Zmiana pojedynczej zakładki ogłoszenia i usunięcie wszystkich ulubionych obejmują teraz transakcję bazy danych. Jeżeli zapis trwałej kopii nie powiedzie się, zmiana w bazie jest wycofywana. Użytkownik zachowuje poprzedni stan i może ponowić operację po rozwiązaniu problemu z pamięcią.
- Trzy regresje symulują awarię katalogu zapisu już po udanej migracji: dodanie ulubionego, usunięcie pojedynczego oraz usunięcie wszystkich. Sprawdzają zachowanie bazy i poprzedniego pliku, poprawne ponowienie oraz stan po utworzeniu nowego repozytorium.
- Wybór przystanku udostępnia „Spróbuj ponownie” również podczas korzystania ze starej kopii katalogu. Wcześniej komunikat o nieudanym odświeżeniu nie miał przycisku ponowienia, jeśli lokalna kopia pozwalała otworzyć listę. Ponowienie używa istniejącego jawnego odświeżenia i jest zablokowane w czasie pobierania lub zapisu wyboru.
- Sprawdzono powiązanie zegara i zapytań Odjazdów z widocznością ekranu oraz anulowaniem przy opuszczeniu aplikacji. Nie dodano cyklicznych zapytań w tle.

Nie przygotowano wydania, APK ani zmian numeru wersji. Kontrole automatyczne nie zastępują testu działania na fizycznym telefonie.


Dodatkowa analiza Android Lint ujawniła niechronione wywołanie opisu grupy powiadomień, dostępne od API 28, mimo wspierania API 26. Wywołanie jest teraz ograniczone do Androida 9 i nowszych; kanały nadal powstają na Androidzie 8. Regresja sprawdza ich utworzenie i ponowną inicjalizację na API 26, 28 i 34.

Inicjalizacja trybu Dzień/Tydzień została przeniesiona z mutacji wewnątrz `remember` do rozpoczęcia zbierania stanu. Pierwsza wartość interfejsu nadal uwzględnia zapisany wybór. Zegar używa jawnego stanu i `LaunchedEffect` z dotychczasowym `repeatOnLifecycle`; zastępuje to konstrukcję `produceState`, której przypisań nie rozpoznawał używany analizator. Ukryte ekrany i aplikacja w tle nadal nie uruchamiają cyklicznego zegara.

Końcowy Android Lint: FOSS: 0 błędów, 116 ostrzeżeń; GMS: 0 błędów, 119 ostrzeżeń. Pozostałe ostrzeżenia dotyczą przede wszystkim dostępnych aktualizacji zależności, tekstów wpisanych bezpośrednio w zasobach i porządkowania interfejsu. Nie dodawano wyciszeń ani pliku bazowego do ukrycia błędów.

Weryfikacja tej rundy: 380 testów FOSS i 383 testów GMS (763 łącznie), zero błędów i pominięć.


## Zachowanie wydania RC7

Odjazdy są domyślnie widoczną zakładką, także bez przystanku docelowego. Pierwsze trzy wyniki są uporządkowane według przyjazdu do celu, następnie według odjazdu; odległość i uzupełnienie dojścia nie wpływają na tę kolejność. „Pokaż więcej” rozszerza listę po trzy połączenia.

Wybór celu, preferowanego przystanku początkowego, usuwanie celu i opcja przesiadek są dostępne w Odjazdach. Ustawienia zawierają jedynie widoczność tej zakładki w grupie „Pasek nawigacji”. Nowy klucz `show_tab` domyślnie włącza zakładkę; dawny klucz eksperymentalny `enabled` nie ukrywa jej po aktualizacji. Jawne ukrycie w RC7 jest zapamiętywane. Usunięcie celu zachowuje dostęp do zakładki i ponownego wyboru. Bez celu nie są wykonywane zapytania o połączenia.

Regresje sprawdzają domyślną widoczność, migrację dawnego wyłączenia, zapamiętanie nowego ukrycia, zachowanie zakładki po usunięciu lub uszkodzeniu celu oraz trzy najwcześniejsze przyjazdy mimo odmiennej kolejności odjazdów i odległości. Wydanie ma versionCode 34 i versionName 1.0.0-rc7.


Dolny pasek ma teraz osobne przełączniki strony głównej, planu, zastępstw, odjazdów i ogłoszeń. Ukrywanie ikon nie usuwa stron z pagera ani dostępu przez skróty i widżety; Ustawienia pozostają dostępne. Tylko podpis strony głównej na pasku brzmi „Start”. Nowe wybory ikon oraz przełącznik kafelka odjazdu są zapamiętywane w DataStore.

Na stronie głównej kafelek odjazdu pojawia się od początku ostatniej odbywającej się lekcji do końca tego dnia. Reguła uwzględnia grupy użytkownika i zwolnienia z lekcji, a dni bez zajęć nie powodują pobierania połączeń. Kafelek można wyłączyć w Ustawieniach → Strona główna. Pokazuje najbliższy odjazd (godzinę odjazdu), niezależnie od sortowania pełnej zakładki według przyjazdu do celu.

Kafelek i zakładka współdzielą wynik, pamięć dojścia i bieżące pobieranie. Pobieranie oraz zegar kafelka pracują wyłącznie podczas oglądania strony głównej; odświeżanie nie tworzy pracy w tle. Sesje odbiorców chronią pobieranie przed anulowaniem przez późno zamykany ekran. Regresje obejmują kolejność sesji podczas pobierania dojścia, początek ostatniej lekcji, okres po zajęciach, odwołaną ostatnią lekcję, dzień bez planu, zapis ustawień po ponownym uruchomieniu oraz dostępność ustawień po ukryciu wszystkich pozostałych ikon.

Końcowa kontrola wydania RC7: 385 testów FOSS i 388 testów GMS (773 łącznie), zero błędów i pominięć. Android Lint obu wariantów oraz analiza release przeszły bez błędów. Podpis APK jest zgodny z opublikowanym RC6; versionCode 34 umożliwia aktualizację z RC6 i wcześniejszego APK testowego.

Weryfikacja tej rundy: 385 testów FOSS i 388 testów GMS (773 łącznie), zero błędów i pominięć.
