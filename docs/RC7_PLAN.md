# RC7: plan stabilizacji i wynik przeglądu

Zakres: analiza i poprawki lokalne. Bez podnoszenia numeru wersji, tworzenia tagów, APK wydania ani publikacji.

## Plan

1. Przejrzeć cykl pobierania odjazdów, paginację, pamięć i anulowanie po opuszczeniu ekranu.
2. Sprawdzić zapis notatek i dostarczanie przypomnień, w tym utratę uprawnień i zmianę treści.
3. Sprawdzić synchronizację i archiwum ogłoszeń pod kątem równoczesnych zapisów.
4. Przejrzeć nawigację i cykl życia mapy; nie zmieniać działających mechanizmów bez potwierdzonego problemu.
5. Odtworzyć znalezione błędy testami, naprawić i uruchomić pełne testy obu wariantów.

## Znalezione błędy i poprawki

- Paginacja Odjazdów anulowała pobieranie dojścia dla istniejących wyników. Nieudana kolejna strona pozostawiała „Sprawdzam dojście…”. Pobieranie dojścia trwa teraz niezależnie, a scalenie kolejnej strony korzysta z aktualnych wyników, uwzględniając zakończone dojście.
- Przypomnienie notatki było uznawane za dostarczone przed próbą wysłania. Cofamy oznaczenie po błędzie, braku uprawnień lub anulowaniu. Cofnięcie dotyczy wyłącznie tej samej rewizji, chroniąc nowsze edycje. Nieudane dostarczenie zgłasza błąd do mechanizmu ponawiania WorkManager.
- Archiwum ogłoszeń używało informacji o istniejących wpisach sprzed pobrania dat. Równoczesny zapis przez RSS/push mógł zostać nadpisany, tracąc zakładkę i treść offline. Ponowne sprawdzenie i dodanie brakujących wpisów odbywają się w jednej transakcji.

Testy regresji: nieudana i udana paginacja podczas pobierania dojścia; synchronizacja i zapis ulubionego artykułu w trakcie pobierania archiwum; utrata zgody na powiadomienia po zajęciu notatki; cofnięcie oznaczenia starej rewizji bez zmiany nowej.

Przejrzane bez nowych potwierdzonych usterek: koordynator synchronizacji, przeliczanie terminów przypomnień po synchronizacji, nawigacja z dynamiczną zakładką, wstrzymywanie mapy i zegarów w tle.

## Druga runda

Zweryfikowano raport zewnętrznego AI i wdrożono osiem poprawek oraz dwie optymalizacje. Szczegóły: [RC7_REPORT_FIXES.md](RC7_REPORT_FIXES.md).
