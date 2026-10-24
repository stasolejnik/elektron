# Changelog

Wszystkie istotne zmiany w eLektronie. Format oparty na
[Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), numeracja wersji zgodna z
[SemVer](https://semver.org/lang/pl/). W nawiasie `versionCode` z Androida.

## [1.0.2] - 2026-10-24 (38)

Krótki opis do wydania na GitHubie: [release-notes/1.0.2.md](release-notes/1.0.2.md).

- Powiadomienie na żywo: pasek postępu i nowy mały napis z końcem lekcji całego dnia („Koniec lekcji o 14:25 · jeszcze 2 h 10 min”) odświeżane co minutę alarmem bez budzenia telefonu - aktualne zawsze, gdy ekran jest włączony. Dawniej co 5 min, więc w krótkiej przerwie i przed pierwszą lekcją pasek stał daleko w tyle (np. 5% przy połowie przerwy).
- Powiadomienie na żywo na pasku stanu (Live Updates, Android 16+): gdy system ma je wyłączone dla eLektronu, Ustawienia → Powiadomienia mówią o tym i prowadzą do ustawienia. Odliczanie na pasku stanu pokazuje system z chronometru powiadomienia.
- Powiadomienie na żywo nie znika po „Wyczyść wszystko” (FLAG_NO_CLEAR); pojedyncze odsunięcie (Android 14+) nadal ukrywa je do jutra. Raport „Zgłoś problem” zapisuje ukrycia i stan Live Updates.
- Widżety: w trakcie lekcji, przerwy i 30 min przed lekcją odświeżane co minutę (alarm bez budzenia) - „Zostało X min” i pasek zgodne ze stroną główną i powiadomieniem, a nie spóźnione do 5 min.
- Nowość: czas do końca lekcji (Ustawienia → Strona główna, domyślnie wyłączony). Strona główna: karta „Do końca lekcji” z godzinami i minutami, godziną końca i paskiem dnia szkolnego. Widżet Plan dnia: mała linia „Do końca lekcji: 2 h 10 min · do 14:25” pod nagłówkiem, odświeżana co minutę przez cały dzień lekcji (alarm bez budzenia). Jedna reguła (SchoolDayEnd) dla strony głównej, widżetu i powiadomienia na żywo; lekcje, z których klasa jest zwolniona, się nie liczą.
- Grupy: dotknięcie grupy przełącza na nią (jak dotąd). Przy przedmiotach z co najmniej trzema grupami przycisk „Wybierz kilka grup” pozwala zaznaczyć kilka naraz; plan, widżety, powiadomienia, przypomnienia i zastępstwa pokazują wszystkie wybrane. Przy dwóch grupach obie to to samo co „Wszystkie”. Dotychczasowe wybory bez zmian.
- Raport „Zgłoś problem”: odświeżenia co minutę (powiadomienie na żywo, widżety) zapisują się w logu tylko przy zmianie stanu, więc w raporcie (ostatnie 150 linii) zostają błędy synchronizacji i ukrycia powiadomienia.
- Odjazdy → **Przejazdy**. Kierunek według planu: do połowy dnia lekcji (środek między początkiem pierwszej a końcem ostatniej lekcji, które się odbywają; bez lekcji 12:00) dojazd z Twojego przystanku do szkoły, potem powrót ze szkoły; przełącznik „Do szkoły / Ze szkoły” w zakładce (ręczny wybór do następnej zmiany automatycznej). Kierunek i lekcja docelowa przeliczane co minutę przy widocznym ekranie i od razu po zmianie symulowanego czasu (tryb dewelopera).
  - Dojazd: zapytanie planera BUSearch ze środka Twojego przystanku pod szkołę albo pod wybrany przystanek przy szkole (też wysiadka na sąsiednim stanowisku do 400 m, z dojściem), od teraz albo 90 min przed najbliższą lekcją - także za kilka dni (w piątek na poniedziałek, po długim weekendzie). Lista zaczyna się od kursu, który jest w szkole ok. 10 min przed lekcją (z opóźnieniem na żywo); wcześniejsze nad nim po „Pokaż wcześniejsze” albo przeciągnięciu w dół na górze listy, późniejsze po „Pokaż więcej”; lista sama dociąga okna planera do godziny lekcji (najdalej pół godziny po niej, bez lekcji w najbliższych dniach - 3 h od początku szukania; najwyżej 4 dociągnięcia na wyszukiwanie i tylko, gdy przybywa kursów, od nowa po odświeżeniu). Odjazd z Twojego przystanku albo stanowiska tuż obok (do 250 m) - planer startuje ze środka przystanku. Karta: „W szkole 7:20 · zapas 40 min” albo spóźnienie, etykieta „Ostatni, którym zdążysz”.
  - Powrót: „Wyjdź o 14:40 · dojście 5 min”, „Wyjdź teraz”, „Pospiesz się”. Przy „Szkoła” (bez wybranego przystanku) tylko kursy, na które da się dojść przed odjazdem - dawniej np. „za 1 min” z dojściem 17 min; krótszą listę uzupełnia kolejne okno planera.
  - Karta kursu w trzech wierszach: linia → przystanek, na którym wysiadasz (dawniej kierunek pojazdu), skąd z godziną i opóźnieniem, a pod spodem dotarcie do szkoły albo kiedy wyjść; przesiadka pod odjazdem.
  - Opóźnienia na żywo z tablicy odjazdów BUSearch (stanowisko i linia) dla pokazywanych kursów w ciągu 30 min, co minutę przy widocznym ekranie, nie przy symulowanym czasie; kurs spóźniony zostaje na liście do faktycznego odjazdu.
  - Karta na stronie głównej także rano (2 h przed lekcjami do ich początku: „Dojazd do szkoły”).
  - Ustawienia: „Twój przystanek” (dawniej „Dokąd”) i „Przystanek przy szkole” (dawniej „Odjazd z”) - wspólny dla obu kierunków, domyślnie „Szkoła” (najbliższe przystanki), do wyboru dowolny, bez dawnego limitu 1 km, lista od najbliższego szkoły. Zapis ostatniego wyniku w wersji 2 (z kierunkiem). Nadal domyślnie wyłączone.
- Nowość: dni wolne (SchoolCalendar). Strona szkoły pokazuje stały plan także w święta i przerwy, więc dawniej np. 11 listopada przychodziły przypomnienia i powiadomienie na żywo, a strona główna i widżety pokazywały „następną lekcję”. Teraz święta ustawowe (Wielkanoc i Boże Ciało liczone z daty Wielkanocy, Wigilia), zimowa przerwa 23-31.12, wiosenna od czwartku przed Wielkanocą do wtorku po niej, wakacje według rozporządzenia i ferie zimowe w kujawsko-pomorskim 2026/2027 (15-28.02.2027, gov.pl) to dni bez zajęć: bez przypomnień, powiadomienia na żywo, czasu do końca lekcji, dojazdu na lekcję; plan dnia pokazuje „Dzień wolny - …”. Dni wolne dyrektora nie są znane.
- Nowy rok szkolny: po pobraniu listy klas wybrana klasa, której nie ma albo której identyfikator dostała inna klasa, jest odznaczana zamiast pobierać plan innej klasy. Ekran wyboru klasy wyjaśnia, co się stało (z nazwą poprzedniej klasy), a powiadomienie prosi o ponowny wybór. Grupy i notatki zostają.
- Nowość: powiadomienia o informacjach szkoły ze strony zastępstw (np. skrócone lekcje, apel) - nowa albo zmieniona treść na dziś i kolejne dni, osobny kanał „Informacje szkoły”, domyślnie włączone (Ustawienia → Powiadomienia). Watcher wysyła na temat zastępstw push typu `notice` przy zmianie informacji, więc powiadomienie przychodzi tak szybko jak o zastępstwie; starsze wersje aplikacji ten typ ignorują.
- Nowe Ustawienia: na górze karta klasy i grup (jak dotąd, osobno), pod nią kategorie (Powiadomienia, Strona główna, Wygląd, Plan lekcji, Przejazdy, Pasek nawigacji, Widżety, Praca w tle, Aktualizacje, Dane, O aplikacji) z ikonami w kolorze akcentu i stanem; każda otwiera własną stronę, wstecz wraca do listy przewiniętej tam, gdzie była. Plakietka GPL v3 w „O aplikacji”.
- Nowość: kolor tła (Ustawienia → Wygląd) - kółka jak przy kolorze akcentu: Klasyczny (dotychczasowy), Grafit (ciemnoszary zamiast czerni), Ciepły, Morski, Leśny i Własny (odcień i nasycenie tła i kart, jak przy własnym kolorze akcentu). Dotyczy też tła widżetów. Czytelność tekstu sprawdzona testem dla każdego odcienia. Klasyczny zostawia dotychczasową paletę bez żadnej zmiany.
- Nowość (wersja z GitHuba): powiadomienie o nowej wersji (Ustawienia → Aktualizacje, domyślnie wyłączone) - sprawdzane przy synchronizacji w tle, GitHub najwyżej co 12 h, jedno powiadomienie na wersję, bez wersji odłożonej przyciskiem „Później”.
- Duży tytuł ekranów (Strona główna, Zastępstwa, Ogłoszenia, Ustawienia, Grupy, Przedmioty) stały, w jednym pasku 64 dp - bez pustego pasa nad nim (dawniej 152 dp) i bez zwijania do małego paska, które przy przewijaniu najpierw zabierało ruch, a dopiero potem przesuwało treść.
- Powiadomienie na żywo: wygaśnięcie po końcu lekcji (setTimeoutAfter) nie ukrywa go już do jutra - system wysyła wtedy ten sam sygnał co odsunięcie, więc lekcja dopisana później tego dnia nie miałaby powiadomienia. Zgoda na dokładne alarmy (Android 12) od razu przelicza przypomnienia i powiadomienie na żywo.
- Przejazdy: nieaktualna wartość ustawień obsłużona po nowszej nie przerywa trwającego zapytania (usuwa sporadyczny błąd testu TransitViewModelTest - dodatkowe zapytanie do planera).
- Informacje szkoły: linie „Publikacja / Opublikowano / Aktualizacja: …” ze strony zastępstw nie są już osobną informacją - ostatnia jest małym podpisem przy dniu w zakładce Zastępstwa. Obrazki ze strony szkoły (np. skrócony plan zajęć) widać w karcie po rozwinięciu. Migracja bazy 4 → 5; informacje zapisane przez 1.0.1 tracą linie publikacji przy odczycie. Testy na pięciu prawdziwych stronach z archiwum watchera.

## [1.0.1] - 2026-10-17 (37)

Stabilna wersja z poprawkami do 1.0.0. Zawiera wszystkie zmiany z 1.0.1-beta1 (niżej) oraz nowości poniżej.
Krótki opis do wydania na GitHubie: [release-notes/1.0.1.md](release-notes/1.0.1.md).

- Nowość: powiadomienia na żywo (Ustawienia → Powiadomienia, domyślnie wyłączone). W dni lekcyjne od 15 min przed pierwszą lekcją do końca ostatniej. Tytuł to etykieta odliczania przy chronometrze: w trakcie lekcji „Do przerwy” (albo „Do okienka”, „Do końca lekcji” przy ostatniej), w przerwie, okienku i przed lekcjami „Do lekcji”. Treść: co trwa albo co będzie z salą i nauczycielem, zastępstwa (najpierw nauczyciel), podgląd notatki, co potem i pasek postępu. Ciche, stałe ID, bez usługi pierwszoplanowej - alarmy jak przy przypomnieniach o lekcjach, treść zmienia się tylko na granicach lekcji, po synchronizacji, zmianie notatki i ustawień; pasek postępu odświeżany alarmem bez budzenia telefonu. „Ukryj na dziś” i odrzucenie (Android 14+) ukrywają je do jutra. Na ekranie blokady wersja bez szczegółów. Na Androidzie 16 prośba o „Live Update” bez zmiany compileSdk/targetSdk. Prośba o zgodę na powiadomienia przy włączaniu. Działa też z symulowanym czasem trybu dewelopera.
- Dokładne alarmy przez USE_EXACT_ALARM (Android 13+, nadawane przy instalacji; SCHEDULE_EXACT_ALARM tylko do Androida 12). Od Androida 14 SCHEDULE_EXACT_ALARM jest domyślnie odmówione, przez co zmiana powiadomienia na żywo przy dzwonku spóźniała się (odliczanie schodziło poniżej zera). Dotyczy też przypomnień o lekcjach i notatkach.
- Nowość: informacje szkoły ze strony zastępstw (np. o oddawaniu krwi) - tekst dopisany pod „Zastępstwa w dniu …”, osobno dla każdego dnia, z zachowanym podziałem na linie. Zapis w bazie (migracja 3 → 4), znika, gdy szkoła usunie go ze strony, działa offline. Zakładka Zastępstwa: karta „Informacja szkoły” na początku dnia (także dnia bez zastępstw), skrót do 3 linii z „Pokaż więcej / Zwiń” (szybkie rozwijanie, zastępstwa pod kartą przesuwają się razem z nią). Strona główna: zwarta karta dla pokazywanego dnia (dziś, po lekcjach następny dzień z lekcjami), zwinięta do 2 linii. Tekst do zaznaczenia, czytany w całości przez TalkBack. Bez powiadomień o samych informacjach.
- Widżet Następna lekcja: „Za X min”, „Zostało X min” i „Lekcja za X min” według tej samej reguły co strona główna (LessonClock) w każdym rozmiarze - bez miejsca na wiersz czasu w nagłówku. Pod lekcją kolejna lekcja (dwie linie, jedna albo wcale); lekcje, z których klasa jest zwolniona, pomijane. Wysokość przydzielana według faktycznych wysokości linii (z powiększeniem czcionki): kolejna lekcja, czas z paskiem, nauczyciel, druga linia tytułu, notatka, duży numer - bez pustej przerwy w średnim widżecie. Odświeżanie w tych samych chwilach co dotąd (granice lekcji i przerw, co 5 min w trakcie lekcji).
- Widżet Plan dnia: przy podświetlonej lekcji „lekcja za X min” w przerwie i „za X min” przed lekcją.
- Widok Dzień w planie: lekcja, z której klasa jest zwolniona, nie ma już ramki, paska i „zostało X min” trwającej lekcji (także kafelek w planie tygodniowym).
- Plan tygodniowy: przewijanie dalekich tygodni (dalej niż 4 tygodnie) nie pobiera planu w kółko - zapis tygodnia +5 nie kasuje już zapisanego tygodnia +4 przy tym samym planie.
- Synchronizacja przy otwarciu aplikacji: powrót na ekran odświeża to, na co przyszła pora (zastępstwa, plan co 6 h, ogłoszenia co godzinę), najwyżej raz na 2 min. Dawniej ekrany pokazywały dane z ostatniej synchronizacji w tle, którą system przy oszczędzaniu baterii odkładał nawet o godziny.
- Widżety i kafelek zmieniają się przy dzwonku razem z powiadomieniem na żywo: odświeżenia na dokładnym alarmie bez budzenia telefonu (przy wygaszonym ekranie - zaraz po jego włączeniu) zamiast zadania WorkManagera z opóźnieniem, które system przesuwał. Termin zerowany po restarcie, aktualizacji i zmianie czasu. Pasek postępu powiadomienia na żywo też na dokładnym alarmie bez budzenia.
- Diagnostyka: raport „Zgłoś problem” zawiera przeliczenia powiadomienia na żywo i odświeżeń widżetów.
- „Co nowego” w wersji stabilnej bez osobnych wpisów wersji testowych.

## [1.0.1-beta1] - 2026-10-11 (36)

Wersja testowa (kanał „Beta”). Szczegóły: [release-notes/1.0.1-beta1.md](release-notes/1.0.1-beta1.md).

- Powiadomienia o zastępstwach: zmiana samych uwag (np. „za ostatnią lekcję”) przy tej samej lekcji, sali i zastępcy aktualizuje powiadomienie. Identyfikator zastępstwa się nie zmienia (zgodność z watcherem push), dochodzi odcisk treści uwag; pierwsza synchronizacja po aktualizacji tylko go zapisuje.
- Ogłoszenia wczytane z archiwum („Pokaż więcej”) od razu trafiają do widzianych - następna synchronizacja nie powiadamia o artykułach sprzed miesięcy.
- Odświeżenie planu na przyszłym tygodniu zapisuje plan także od bieżącego tygodnia: dzisiejszy dzień, widżety i przypomnienia nie zostają ze starą salą, choć plan był oznaczony jako świeży.
- Przypomnienie o lekcji, którego niedokładny alarm się spóźnia, zostaje w kolejce do chwili dostarczenia (najpóźniej 5 min po początku lekcji) - synchronizacja w tle nie zastępuje go następnym.
- Build: cel JVM ustawiony wprost zamiast `jvmToolchain(17)` - projekt buduje się z JDK 17 i nowszym (F-Droid używa JDK 21); CI buduje na JDK 21.
- Widżet Plan dnia: dotknięcie „N lekcji za Tobą · Pokaż” pokazuje minione lekcje u góry listy (do przejścia do następnej lekcji). Launcher nie pozwala ustawić przewinięcia listy widżetu, dlatego przełącznik zamiast przewijania.
- Odjazdy: animacja pojawiania się kolejnych połączeń i wskaźnik wczytywania w przycisku „Pokaż więcej”.
- Widok dnia w planie: przy zastępstwie informacja szkoły (nie numer sali) jest nagłówkiem także wtedy, gdy wpisany jest zastępca; nauczyciel trafia do drugiej linii. Strona główna, plan tygodniowy, zakładka Zastępstwa, widżety i powiadomienia bez zmian (najpierw nauczyciel).
- Odjazdy: przeciągnięcie w górę na końcu listy (nowym gestem) wczytuje kolejne połączenia.
- Ogłoszenia offline (stan sieci z ConnectivityManager, bez wymogu weryfikacji sieci): tylko wpisy z zapisaną treścią i informacja nad listą; bez dociągania archiwum.
- Plan tygodniowy: kółko w nagłówku tylko przy dzisiejszej dacie (dawniej przy dniu wybranym do przewinięcia, także w innych tygodniach).
- Plan tygodniowy: inny tydzień otwiera się od pierwszej lekcji (dawniej po zatrzymaniu przewijał się do lekcji tego samego dnia tygodnia).
- Plan tygodniowy: kafelek zastępstwa pokazuje zastępcę i salę albo informację szkoły zamiast strzałki „↔” przy nazwie przedmiotu.
- Zastępstwa: strona z kilkoma nieczytelnymi wierszami (dane zapisane z ostrzeżeniem) liczy się jak udane pobranie - powiadomienia (także po pushu), stan „sprawdzono” i zwykły odstęp w tle; bez ponawiania tej samej strony i bez pobierania co 15 min w nocy.
- Znacznik powiadomienia zastępstwa obejmuje nieobecnego nauczyciela - dwa zastępstwa tej samej lekcji nie zastępują się w panelu.
- Wpisy bez klasy (zajęcia łączone) z dni bez zapisanego planu lub listy nauczycieli: dzień traktowany jak niekompletny (wpisy zostają, nie wracają jako „nowe”).
- Zmieniony plan usuwa też zapisane dalsze tygodnie klasy (poza zakresem zapisu), żeby nie zostawały ze starym planem.
- Widżety i kafelek: lekcje, z których klasa jest zwolniona, nie są trwającą ani następną lekcją (wybór dnia, odliczanie, przerwa).
- Harmonogram odświeżania widżetów liczy odstęp w strefie czasowej (zmiana czasu letniego/zimowego).
- LessonClock (strona główna, znacznik w widoku dnia) pomija lekcje, z których klasa jest zwolniona.
- Plan tygodniowy: pusty lub wczytywany tydzień jest przewijalny (gest na krawędzi zmienia tydzień); w weekend strona „dziś” to nadchodzący tydzień.
- Ogłoszenia: „Szukaj w starszych” nie zmniejsza liczby wpisów pokazywanych na liście głównej.
- Aktualizacje: lista do 100 wydań (wcześniej 10); zapamiętany wynik sprawdzenia dotyczy jednego kanału.
- Diagnostyka: więcej tagów logów aplikacji w raporcie (bez logów Odjazdów).
- README: informacja o podpisach wydań z F-Droid i z GitHuba.

## [1.0.0] - 2026-10-10 (35)

Pierwsza stabilna wersja eLektrona.

- Zastępstwa zapisane dla całej klasy za lekcję, na którą nie chodzisz („Nie chodzę” w grupach), nie trafiają na stronę główną, do zakładki, widżetu ani powiadomień; są w „Pokaż zastępstwa innych grup”.
- Powiadomienia o zastępstwach: dzień i lekcja w tytule, „Za: przedmiot · nauczyciel”, zwolnienie z lekcji opisane tekstem szkoły. Poprawione zastępstwo zastępuje poprzednie powiadomienie.
- Widżet Zastępstwa pokazuje „Sprawdzono: …”, gdy dane nie są z dziś albo mają ponad 3 godziny; Plan dnia zawsze zaczyna od bieżącej lekcji.
- Przedmiot przy zastępstwach w zakładce i na stronie głównej; ogłoszenia ze strony głównej otwierają się w aplikacji.
- Potwierdzenie zmiany klasy, większe pola przełączników, własne nazwy przedmiotów w „Twoich grupach”, stopka z porą wyświetlania karty Odjazdów.
- Szybsze Odjazdy: ostatni pełny wynik widać od razu po uruchomieniu aplikacji, pierwsze kursy przed odpowiedzią wszystkich stanowisk, a stanowiska bez kursów do celu dostają mniej zapytań. Pobrany rozkład używany do 10 minut.
- Mniejsze zużycie baterii: widżet planu budzi telefon co 5 min tylko w trakcie lekcji, przerwy i 30 min przed lekcją; w nocy (23:00–5:00) zastępstwa i ogłoszenia sprawdzane w tle najwyżej co 2,5 h; zegar ekranów tyka raz na minutę.
- Zastępstwa: wpis bez klasy (zajęcia łączone, np. „Wychowanie fizyczne - Zajęcia Świetlicowe”) jest przypisywany do klasy i grupy z planu tylko przy pewnym dopasowaniu nauczyciela i nie powoduje już stałego błędu; ucięta odpowiedź serwera nie zastępuje zapisanych zastępstw, a zastępowane są tylko dni pokazane na stronie.
- Przypomnienia o notatkach przez alarm systemowy (punktualnie także w trybie uśpienia), WorkManager jako zapas.
- Odjazdy: przystanki o tej samej nazwie w różnych miejscach osobno, zapisany przystanek po zmianie numeracji stanowisk aktualizowany, czas dojścia ponawiany po powrocie internetu.
- Niższy pasek nawigacji; Android 8–11 - kolory widżetów od razu po zmianie trybu ciemnego.
- Poprawki niezawodności: synchronizacja w tle bez zbędnych ponowień, ponawianie odczytu w widżetach, przypomnienia o notatkach po nieudanym wyświetleniu, bez powtórnego dźwięku powiadomień, poprawione wpisy zastępstw w dniach częściowo nieczytelnych, usuwanie nieistniejących klas i nauczycieli, odrzucanie uciętego planu, odczyt dat ogłoszeń w innych wariantach.
- Wybór grupy, której nie ma już w nowym planie, nie ukrywa wszystkich lekcji przedmiotu; uszkodzone ustawienia Odjazdów nie blokują ich na stałe; raport awarii także przy starcie; usuwanie pobranego pliku aktualizacji.
- Podpisy paska nawigacji w jednej linii; niepełne dane zastępstw jako łagodna informacja zamiast czerwonego błędu.
- Poprawki interfejsu: karta „Następna lekcja” otwiera dzień tej lekcji, plan nie zostaje przezroczysty po szybkich skokach, wyszukiwanie ogłoszeń nie gubi znaków, okna i szczegóły lekcji przetrwają obrót ekranu.
- Mniej zbędnych przeliczeń strony głównej; stabilniejsze testy.
- Nowy widok ogłoszeń: cała treść w jednej przewijanej kolumnie, zdjęcia w miejscu z posta i przeglądarka zdjęć z przybliżaniem, bez pustych przerw; treść zapisana przez starsze wersje pobierana od nowa.
- Nowy układ Ustawień: karta klasy na górze, powiadomienia z cichymi godzinami, Odjazdy w jednej sekcji, nagłówki z ikonami i krótkie opisy opcji; kanał aktualizacji „Stabilne” (domyślnie) albo „Beta”.
- Tytuły w kartach i oknach w kolorze tekstu zamiast szarego; strona główna bez pustych przerw; czytelniejsza trwająca lekcja z zastępstwem w widoku Dzień; baner błędu w kolorach karty.
- Ogłoszenia bez nawigacji „Poprzedni / Następny” i bez „Odśwież”; pamięć zdjęć do 50 MB. Zwarty ekran Odjazdów („Odjazd z” i „Dokąd” jak w planerach), Odjazdy jako ekran startowy, religia w szybkim wyborze grup, symulacja czasu w trybie dewelopera.
- Wybór ikony aplikacji (Ustawienia → Wygląd): domyślna albo logo na białym tle (po zmianie aplikacja się zamyka). Skróty po przytrzymaniu ikony z ikoną aplikacji i krótkimi nazwami („Strona główna”, „Ogłoszenia”), skrót Odjazdy przy włączonej zakładce. Skróty przypięte na ekran główny w starszych wersjach trzeba przypiąć ponownie.
- Symulowany czas zawsze widoczny w panelu dewelopera (także po ponownym uruchomieniu), błąd przypisania zastępstw zajęć łączonych nie usuwa wcześniej zapisanych, błąd odczytu ustawień przy starcie nie zamyka aplikacji.
- Widżety z przezroczystym tłem (ponad 40%) w motywie Auto dobierają kolor tekstu do jasności tapety - w trybie jasnym na ciemnej tapecie tekst był nieczytelny. Wybrany motyw jasny lub ciemny zawsze obowiązuje. Widżet Plan dnia przerysowuje wiersze lekcji po zmianie motywu (także trybu ciemnego systemu), przezroczystości czy dnia - wcześniej launcher zostawiał stare wiersze listy.
- Nowy plan nie wypełnia dni przed „Obowiązuje od”, miniatury ogłoszeń tylko z serwerów szkoły, kanał Stabilne nie pokaże wydania Pre-release także po zmianie kanału bez internetu.

Krótki opis do wydania na GitHubie: [release-notes/1.0.0.md](release-notes/1.0.0.md).

## [1.0.0-rc7] - 2026-10-07 (34)

- Niezależna karta Odjazdów na stronie głównej i wybór przystanków w Ustawieniach; karta i zakładka domyślnie wyłączone.
- Czytelniejsze czasy w godzinach i minutach oraz poprawki wyników, paginacji i odświeżania połączeń.
- Zmiana kolejności ikon przez przytrzymanie i przeciąganie; równe pola, okrągłe efekty dotknięcia i obsługa TalkBack.
- Pewniejszy zapis notatek, grup, ustawień i ulubionych oraz zachowanie danych po błędach pobierania.
- Poprawki przypomnień, powiadomień, widżetów i ograniczenie zbędnych obliczeń interfejsu.

Szczegóły: [opis wydania RC7](release-notes/1.0.0-rc7.md).

## [1.0.0-rc6] (33)

Nowa funkcja **Odjazdy ze szkoły** pozwala znaleźć połączenie komunikacji miejskiej
ze szkoły przy Karłowicza 20 do wybranego przystanku w Bydgoszczy.

- Włączenie w Ustawieniach po wybraniu celu; osobna zakładka między Zastępstwami a Ogłoszeniami.
- Wyszukiwanie przystanków i wybór na mapie OpenStreetMap, bez GPS.
- Przystanek początkowy, linia, kierunek, godziny rozkładowe, przyjazd i czas dojścia ulicami oraz ścieżkami.
- Wybór preferowanego przystanku początkowego oraz zmiana celu bezpośrednio w Odjazdach.
- Domyślnie połączenia bezpośrednie; przesiadki można włączyć w Ustawieniach.
- Osobne listy pięciu ostatnich celów i przystanków początkowych.
- Przystanki początkowe uporządkowane od najbliższego szkoły; „Pokaż więcej” dla późniejszych odjazdów.
- Ręczne odświeżanie i informacja o czasie ostatniego sprawdzenia.

## [1.0.0-rc5.1] - 2026-10-04 (25)

Hotfix RC5. Aktualizacja zachowuje notatki, ustawienia i ulubione.

- Stały stan panelu edycji notatek: wpisywanie tekstu i zapisywanie nie uruchamiają ponownie animacji otwierania.
- Usunięte podwójne dopasowanie panelu do klawiatury; ochrona niezapisanych zmian pozostaje.
- Własne notatki w trzech widżetach, osobno od uwag szkoły przy zastępstwach.
- Podgląd dopasowany do dostępnego miejsca i powiększenia czcionki; pełna treść w szczegółach lekcji.
- Odświeżanie widżetów po zapisaniu i usunięciu notatki, bez dodatkowych połączeń sieciowych.
- Porównywanie numerów hotfixów: RC5 rozpoznaje RC5.1 jako nowsze wydanie.

## [1.0.0-rc5] - 2026-10-04 (24)

Piąta wersja kandydująca do 1.0.0.

- Udostępnianie planu dnia i tygodnia z wybranymi grupami i zastępstwami.
- Własne notatki do przyszłych lekcji po przytrzymaniu w planie dnia lub tygodnia, z edycją i usuwaniem.
- Konfigurowalne lokalne przypomnienia o notatkach: dzień wcześniej o wybranej godzinie lub 5, 10, 15, 30, 60 albo 120 minut przed lekcją.
- „Moje notatki” w ustawieniach pozwalają odczytać i usunąć także starsze wpisy; czyszczenie danych podręcznych ich nie usuwa.
- Udostępnianie pojedynczego zastępstwa po przytrzymaniu oraz wszystkich widocznych przyciskiem w pasku.
- Stały wygląd przycisku udostępniania bez migania przy przełączaniu stron.
- Cztery skróty ikony aplikacji: Strona główna, Plan lekcji, Zastępstwa, Ogłoszenia.
- Poprawki granic przewijania planu, dużej czcionki, niezapisanych edycji i obsługi zmian klasy przy przypomnieniach.
- Ulubione ogłoszenia z zapisaniem treści do czytania bez internetu.
- Podgląd treści ogłoszenia w aplikacji z przyciskiem zakładki przy tytule.
- Poprawne odczytywanie treści ze strony szkoły i rozróżnianie błędu odczytu od braku internetu.
- Ogłoszenia zachowują formatowanie tekstu, nagłówki i listy; mniejsze odstępy między akapitami.
- Data i status zapisu przy tytule ogłoszenia, link do strony szkoły pod treścią.
- Zwarty pasek planu z ikoną udostępniania obok przełącznika Dzień/Tydzień.
- Prostszy status przypomnień: najbliższy alarm i jeden komunikat o blokadzie.
- Ulubione pozostają po synchronizacji i czyszczeniu danych podręcznych.
- Usuwanie wszystkich zakładek z potwierdzeniem, liczba ulubionych i zakładka obok tytułu na liście.
- Poprawki komunikatów, ładowania i przełączania między wszystkimi a ulubionymi ogłoszeniami.

Udostępnianie otwiera systemowe okno wyboru aplikacji. Ulubione są zapisywane lokalnie; pobranie pełnej treści wymaga internetu, późniejsze czytanie tekstu nie. Nie dodano cyklicznych połączeń w tle.

Aktualizacja z RC4 zachowuje ustawienia i dane.

## [1.0.0-rc4] - 2026-10-04 (23)

Czwarta wersja kandydująca do 1.0.0: poprawki sieci, pracy w tle i stabilności.

- Mniejsze zużycie transferu dzięki poprawionej pamięci podręcznej sieci.
- Rzadsze pobieranie planu, listy klas i ogłoszeń w tle; zastępstwa nadal co 15 minut.
- Przypomnienia uwzględniają zastępstwa otrzymane przez push.
- Szybsze przerywanie pobierania przy zmianie klasy.
- Mniej zapisów identycznych danych i odświeżeń widżetów.
- Poprawki pamięci planu, dopasowania tekstu i zegarów niewidocznych zakładek.
- Pewniejsze wykrywanie błędnych stron szkoły i pobieranie starszych ogłoszeń.

W tle: zastępstwa co 15 minut, ogłoszenia raz na godzinę, plan co 6 godzin, lista klas raz na dobę. Android może opóźniać pracę. Ręczne odświeżanie działa od razu.

Plik APK jest podpisany tym samym kluczem co poprzednie wydanie. Zainstaluj go jako aktualizację; ustawienia i wybrana klasa zostają.

## [1.0.0-rc3] - 2026-10-03 (22)

Trzecia wersja kandydująca do 1.0.0: szczegóły lekcji z zastępstw i widżetów, jedna wspólna synchronizacja i poprawki stabilności.

### Dodane
- Dotknięcie zastępstwa w zakładce Zastępstwa albo w widżecie Zastępstwa otwiera plan na dzień
  tego zastępstwa i od razu szczegóły tej lekcji (gdy lekcja jest w Twoim planie po wyborze grup;
  zastępstwo innej grupy otwiera sam plan na ten dzień).
- Widżet Następna lekcja: dotknięcie otwiera plan i szczegóły pokazywanej lekcji (także
  jutrzejszej). Widżet Plan dnia: dotknięcie lekcji otwiera jej szczegóły, a nagłówka z nazwą
  dnia zakładkę Plan.

### Zmienione
- Szczegóły lekcji otwierają się od razu w całości (dawniej przy zastępstwie okno otwierało się
  do połowy i trzeba było je rozwijać). Gdy treść się nie mieści, przewija się w oknie.
- Każda synchronizacja (w tle, po wyborze klasy, z przycisków odświeżania) przechodzi przez
  jedno miejsce: równoczesne odświeżenia nie pobierają danych dwa razy i nie dublują powiadomień.

### Naprawione
- Lekcja z nieczytelną godziną na stronie planu jest pomijana (dawniej dostawała 00:00, co psuło
  dzień startowy planu, przypomnienia i „Trwa teraz”). Gdy nieczytelne są wszystkie godziny,
  zapisany plan zostaje, a odświeżenie kończy się komunikatem o zmianie strony szkoły.
- Kodowanie znaków podane w nagłówku w cudzysłowie (`charset="ISO-8859-2"`) jest rozpoznawane.
- Plan lekcji trzyma w pamięci tylko ostatnio oglądane tygodnie (dawniej każdy przewinięty
  tydzień zostawał w pamięci do zamknięcia aplikacji).
- Aktualizacja z GitHuba: komunikat po polsku także przy braku sieci albo miejsca w telefonie;
  brak instalatora plików nie wywraca aplikacji.
- Widżety zostawiają ostatnie dane, gdy odświeżenie chwilowo się nie uda (dawniej komunikat
  „Nie można wczytać widżetu”).
- Brak usług push na telefonie nie wywraca aplikacji przy starcie (wersja z GitHuba).
- Widżet Zastępstwa: dotknięcie wiersza otwiera szczegóły lekcji (dawniej kliknięcie trafiało
  w tło widżetu i otwierało stronę główną). Widżet pokazuje tyle zastępstw, ile mieści się
  w jego wysokości, a resztę jako „+N więcej” (otwiera zakładkę Zastępstwa). Po aktualizacji
  aplikacji widżety przerysowują się od razu.

## [1.0.0-rc2] - 2026-10-03 (21)

Druga wersja kandydująca do 1.0.0: poprawki stabilności, zastępstw dla grup i odświeżania danych.

### Naprawione
- Aktualizacja z GitHuba nie przerywa się już na wolnym łączu: pobieranie APK miało limit
  45 s na cały plik.
- Błąd serwera szkoły przy liście klas, kanałach RSS i archiwum ogłoszeń to teraz porażka
  synchronizacji, a nie „pusta lista”. „Zsynchronizowano” zapisuje się tylko po odświeżeniu
  planu albo zastępstw, więc baner o nieaktualnych danych nie znika przy niedziałającej stronie.
- Uszkodzony plik ustawień nie wysypuje aplikacji, widżetów ani synchronizacji (wracają
  ustawienia domyślne).
- Zmiana dowolnego ustawienia nie przeładowuje już planu i strony głównej.
- Anulowana synchronizacja (zamknięty ekran, zatrzymane zadanie w tle) nie kończy się
  komunikatem o błędzie i nie liczy dalej.
- Przypomnienia o lekcjach: ustawiane po pobraniu planu nowej klasy (dawniej do najbliższej
  synchronizacji w tle nie było żadnego), po ręcznym odświeżeniu (np. zwolnienie z lekcji)
  i zanim zakończy się synchronizacja w tle.
- Widżety odświeżają się od razu po ręcznym odświeżeniu, a przy dzwonku przeskakują wszystkie
  (dawniej część widżetów i kafelek mogła zostać ze starą lekcją).
- Plan minionych tygodni nie jest nadpisywany bieżącym planem: strona szkoły publikuje tylko
  aktualny plan, więc dla tygodnia spoza zapisanych pojawia się komunikat zamiast cudzego planu.
  Błąd pobierania brakującego tygodnia pokazuje komunikat zamiast pustego tygodnia.
- Błąd synchronizacji po wyborze klasy pokazuje polski komunikat zamiast surowego wyjątku.
- Zastępstwa dla grup: gdy grupa 1 i grupa 2 mają zastępstwa na tej samej lekcji, każda widzi
  swoje (w planie, widżetach i przypomnieniach). Dawniej użytkownik z grupy 2 mógł nie
  zobaczyć swojego zastępstwa.
- Nierozpoznany wpis na stronie zastępstw nie kasuje już poprawnie zapisanych zastępstw z tego
  dnia - dzień jest zastępowany tylko wtedy, gdy odczytano z niego wszystkie wpisy.
- Przedmiot w powiadomieniu o zastępstwie pochodzi z grupy, której zastępstwo dotyczy
  (dawniej zawsze z pierwszej grupy lekcji).

### Zmienione
- Test zgodności identyfikatorów zastępstw z watcherem powiadomień push (te same ID w aplikacji
  i w elektron-push-watcher, bez podwójnych powiadomień).

## [1.0.0-rc1] - 2026-10-02 (20)

Pierwsza wersja kandydująca do wydania 1.0.0.

### Dodane
- Nowy widok tygodnia (jak w eduVulcan): po lewej godziny, 5 kolumn dni, lekcje rozmieszczone
  według godzin (okienka i przerwy widać), w kafelku tylko nazwa przedmiotu (dzielenie wyrazów),
  linia bieżącej godziny, przewijanie w pionie. Dotknięcie lekcji (także w widoku dnia) otwiera
  szczegóły: dzień, godziny, przedmiot, grupa, sala, nauczyciel, zastępstwo.
- Raport błędu z surowymi logami (po awarii i z „Zgłoś problem” w Ustawieniach): przewijany
  podgląd, „Kopiuj logi”, „Zgłoś przez e-mail” (raport trafia do schowka, a wiadomość do
  kontakt.elektron@pm.me prosi o jego wklejenie - programy pocztowe ucinały długie treści)
  i „Zgłoś na GitHubie”. Raport zawiera ślad stosu, wątek, stan synchronizacji, stan powiadomień,
  baterii i sieci oraz ostatnie logi aplikacji (bez szumu systemowego).
- Kontakt z deweloperem: kontakt.elektron@pm.me (Ustawienia → O aplikacji, README, polityka
  prywatności).
- Tryb dewelopera: symulacja zastępstwa, odwołania lekcji, ogłoszenia (z powiadomieniami)
  i awarii aplikacji, raport błędu, usuwanie symulacji.

### Zmienione
- „Sprawdź aktualizacje” w osobnej sekcji „Aktualizacje”, nad „O aplikacji”.
- Wersja release bez zaciemniania nazw (R8 nadal zmniejsza kod): ślady stosu w raportach
  błędów są czytelne bez pliku mapowania.
- Licencja w Ustawieniach jako plakietka GPLv3 (dotknięcie otwiera tekst licencji); informacja
  o źródłach danych przeniesiona do README.
- Repozytorium: przezroczyste logo (`docs/logo.svg`, `docs/logo.png`).

### Naprawione
- Czas do końca lekcji/przerwy w planie był nieaktualny po powrocie do aplikacji pozostawionej
  w tle (zegar tykał co 30 s niezależnie od ekranu) - teraz aktualizuje się od razu.
- Okno raportu błędu na małych ekranach - przewijane, przyciski zawsze dostępne.
- Ręczne odświeżanie na stronie głównej bez internetu zapisywało „zsynchronizowano teraz”
  i kasowało komunikat o błędzie - dane wyglądały na aktualne.
- Odświeżanie w planie lekcji ignorowało błędy (poza zmianą układu strony) - teraz komunikat.
- Synchronizacja w tle kasowała komunikat o błędzie, gdy pobrała się choć część danych - nie
  było widać, że np. strona zastępstw nie działa.

## [0.6.6-beta] - 2026-10-02 (19)

### Zmienione
- Kolor akcentu przy włączonych kolorach z tapety: kółka zostają widoczne (żadne nie jest
  zaznaczone), wybranie koloru wyłącza kolory z tapety. Dawniej wybór znikał.

### Naprawione
- Przypomnienia o lekcjach: Android kasuje alarmy przy aktualizacji aplikacji i restarcie
  telefonu - przypomnienie wracało dopiero przy najbliższej synchronizacji (do 15 min), więc
  tuż po aktualizacji mogło przepaść. Teraz od razu (także po zmianie czasu i strefy).
- Widżet „Plan dnia” nie pokazywał, ile zostało do końca trwającej lekcji.
- Zastępstwa: synchronizacja kasowała wszystkie zastępstwa od dziś i wstawiała tylko te ze
  strony. Gdy szkoła publikowała zastępstwa na jutro w trakcie dzisiejszych lekcji, dzisiejsze
  znikały z planu. Teraz zastępowane są tylko dni pokazane na stronie.
- Zastępstwa: dzień, w którym szkoła odwołała wszystkie zastępstwa (nagłówek dnia bez wpisów),
  był ignorowany i stare wpisy zostawały.
- Plan lekcji: nowy plan opublikowany z wyprzedzeniem („Obowiązuje od: …”) trafiał od razu do
  bieżącego tygodnia. Teraz dni przed tą datą zachowują dotychczasowy plan.
- Błąd serwera szkoły i brak połączenia (plan, zastępstwa, oba kanały ogłoszeń) liczyły się jako
  udana synchronizacja - bez komunikatu, choć nic nie zostało pobrane.
- Komunikaty błędów: zamiast technicznych („Unable to resolve host…”, „HTTP 503…”) - „Brak
  połączenia z internetem albo ze stroną szkoły” i „Strona szkoły chwilowo nie odpowiada”.

## [0.6.5-beta] - 2026-09-30 (18)

### Dodane
- Trwająca lekcja w planie: obramowanie, „zostało X min” i pasek postępu (jak na stronie
  głównej i w widżetach).
- Zakładka Zastępstwa: „Pokaż zastępstwa innych grup” - zastępstwa klasy dla grup, do których
  nie należysz, są ukryte, ale można je podejrzeć (przygaszone).

### Zmienione
- Czas do lekcji (plan, strona główna, widżety, kafelek): tylko w przerwie (luka do 30 min) albo
  w ostatnich 30 minutach przed lekcją. W okienku (np. lekcja innej grupy) i przed pierwszą
  lekcją - dopiero 30 min przed nią (widżet pokazywał 60 min).
- Powiadomienie o zastępstwie: tytuł „Zastępstwo: Jutro, 7. lekcja · przedmiot”, treść jak
  w zakładce Zastępstwa, przedmiot z Twoją nazwą (bez sufiksu grupy).
- Grupy WF w wyborze grup i w planie: „Grupa 1”/„Grupa 2” zamiast „j1”/„j2”.
- Zastępstwo znika z zakładki Zastępstwa, strony głównej i widżetu po końcu SWOJEJ lekcji
  (dawniej z zakładki dopiero po wszystkich lekcjach dnia); w planie lekcji zostaje.

### Naprawione
- Plan lekcji: w trakcie lekcji następna lekcja pokazywała „Za 52 min” (odliczanie przez całą
  trwającą lekcję); to samo strona główna w okienku przed zastępstwem.
- Widżet: odliczanie przed pierwszą lekcją się nie pojawiało (następne odświeżenie było
  zaplanowane dopiero na początek lekcji) - teraz widżet odświeża się 30 min przed lekcją.
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

## [0.4.0-alpha] (11)

### Dodane
- Widżety „Następna lekcja” i „Plan dnia”, odświeżane z dzwonkiem.
- Plan lekcji: przesuwanie palcem w lewo i prawo zmienia dzień lub tydzień.
- Przygotowanie do wydań podpisanych kluczem release.

### Naprawione
- Aktualizacje nie kasują już lokalnych danych aplikacji.

Starsze wersje (0.1.0 - 0.3.1-alpha) nie są opisane w tym pliku.

[1.0.2]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.2
[1.0.1]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.1
[1.0.1-beta1]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.1-beta1
[1.0.0]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0
[1.0.0-rc7]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc7
[1.0.0-rc6]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc6
[1.0.0-rc5.1]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc5.1
[1.0.0-rc5]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc5
[1.0.0-rc4]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc4
[1.0.0-rc3]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc3
[1.0.0-rc2]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc2
[1.0.0-rc1]: https://github.com/stasolejnik/elektron/releases/tag/v1.0.0-rc1
[0.6.6-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.6-beta
[0.6.5-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.5-beta
[0.6.4-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.4-beta
[0.6.3-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.3-beta
[0.6.2-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.2-beta
[0.6.1-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.1-beta
[0.6.0-beta]: https://github.com/stasolejnik/elektron/releases/tag/v0.6.0-beta
[0.5.0-alpha]: https://github.com/stasolejnik/elektron/releases/tag/v0.5.0-alpha
[0.4.0-alpha]: https://github.com/stasolejnik/elektron/releases/tag/v0.4.0-alpha
