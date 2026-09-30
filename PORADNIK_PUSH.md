# Poradnik: powiadomienia push bez własnego backendu

Zajmie to ok. 20–30 minut, samych kliknięć w konsolach, zero kodu do pisania (kod już jest
w patchu). Wszystko na darmowych warstwach — Firebase Cloud Messaging jest darmowe zawsze,
GitHub Actions jest bez limitu na publicznym repo.

---

## Krok 1 — Firebase (5 min)

1. Wejdź na **console.firebase.google.com**, zaloguj się kontem Google.
2. **Dodaj projekt** → nazwij np. `elektron-zse`. Możesz wyłączyć Google Analytics (niepotrzebne).
3. W konsoli projektu kliknij ikonę **Androida** ("Dodaj aplikację").
4. **Nazwa pakietu**: `pl.zse.bydgoszcz.elektron` — musi się zgadzać co do litery z `applicationId` w `app/build.gradle.kts`.
5. Pobierz **`google-services.json`** i wrzuć go do `~/Atomik/app/` (obok `build.gradle.kts` tego modułu — **nie** do roota repo).
6. Cloud Messaging jest włączony domyślnie, nic więcej nie trzeba klikać.

⚠️ Bez tego pliku build appki **nie zbuduje się w ogóle** (plugin `google-services` rzuci
czytelny błąd "File google-services.json is missing"). To oczekiwane, dopóki go nie dodasz.

## Krok 2 — Klucz serwisowy do wysyłania pushy (3 min)

1. W konsoli Firebase: **⚙️ (ustawienia projektu) → Service accounts (Konta usługi)**.
2. Zakładka "Firebase Admin SDK" → **Generate new private key** → pobierze się plik JSON.
3. **Trzymaj go w tajemnicy** — to jest klucz uprawniający do wysyłania pushy w Twoim imieniu.
   Nie commituj go nigdzie do repo. Za chwilę wklejasz go jako sekret GitHub (krok 4).

## Krok 3 — Nowe, publiczne repo na GitHub (5 min)

Musi być **osobne repo na GitHub** (nie GitLab) i musi być **publiczne** — tylko wtedy
GitHub Actions jest bez limitu minut. Może się nazywać np. `elektron-push-watcher`.

```bash
# lokalnie, tam gdzie masz rozpakowany folder push-watcher/ z tej odpowiedzi:
cd push-watcher
git init
git add .
git commit -m "Initial commit: eLektron push watcher"
git branch -M main
git remote add origin https://github.com/<TWÓJ_LOGIN>/elektron-push-watcher.git
git push -u origin main
```

(Załóż puste repo na github.com/new najpierw — bez README, bez .gitignore, żeby `push -u`
zadziałało bez konfliktów.)

## Krok 4 — Sekret w GitHub Actions (2 min)

1. W nowym repo na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**.
2. Nazwa: **`FCM_SERVICE_ACCOUNT_JSON`**.
3. Wartość: **cała zawartość** pliku JSON z Kroku 2, wklejona jeden do jednego (całość, łącznie z `{ }`).
4. Zapisz.

## Krok 5 — Sprawdź, że workflow działa (2 min)

1. W repo na GitHubie: zakładka **Actions**. Jeśli GitHub zapyta, czy włączyć workflowy —
   potwierdź.
2. Kliknij workflow **"eLektron push watcher"** → **Run workflow** (to jest `workflow_dispatch`
   w YAML-u — pozwala odpalić ręcznie, bez czekania na cron).
3. Poczekaj ~10–20 sekund, sprawdź logi. Powinno być: `Sparsowano X zastępstw`,
   `Sparsowano Y ogłoszeń`. Pierwsze uruchomienie **nie wysyła** żadnego pusha (to jest
   snapshot startowy — identyczna zasada co "initial_sync_done" w appce), tylko zapisuje
   `state.json` z powrotem do repo (zobaczysz nowy commit od "elektron-push-watcher").
4. Od tego momentu workflow leci sam co 5 minut (harmonogram `cron` w pliku YAML).

## Krok 6 — Zbuduj appkę z patchem

Jeśli jeszcze tego nie zrobiłeś — wgraj main patch z kodem (osobny plik w tej odpowiedzi),
upewnij się że `google-services.json` z Kroku 1 leży w `app/`, i zbuduj normalnie:

```bash
cd ~/Atomik
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew :app:assembleDebug
```

## Krok 7 — Test end-to-end

1. Zainstaluj appkę, przejdź Setup, wybierz swoją klasę (appka subskrybuje temat FCM
   automatycznie przy starcie — zobacz `FcmTopicManager`).
2. Na GitHubie: **Actions → Run workflow** ręcznie jeszcze raz. Jeśli w międzyczasie na
   stronie szkoły pojawiło się nowe zastępstwo dla Twojej klasy — powinieneś dostać
   powiadomienie na telefonie w ciągu kilku sekund, nawet z appką zamkniętą.
3. Do testu bez czekania na prawdziwe zastępstwo: dopisz ręcznie wpis do `state.json` w
   swoim repo tak, żeby na stronie było "coś nowego" względem stanu — albo po prostu poczekaj
   na pierwsze prawdziwe zastępstwo, appka i tak dostanie je też przez zwykły sync (15 min)
   jako backup.

---

## Co się dzieje pod spodem (skrót)

```
GitHub Actions (co 5 min, za darmo, publiczne repo)
        │
        ▼
watch.py: pobiera zastępstwa.zse... + RSS, porównuje z state.json w repo
        │  (nowość?)
        ▼
FCM HTTP v1 API → temat "elektron-subs-zse-bydgoszcz" / "elektron-anns-zse-bydgoszcz"
        │
        ▼
Telefon (zasubskrybowany temat przez FcmTopicManager)
        │
        ▼
ElektronFirebaseMessagingService.onMessageReceived()
        │  (czy dotyczy Twojej klasy/nauczyciela? czy masz włączone powiadomienia?)
        ▼
Lokalne powiadomienie systemowe (ten sam LocalNotificationSink co dotąd)
```

Zwykły `SyncWorker` w appce (co 15 min) dalej działa jako siatka bezpieczeństwa — jeśli
push z jakiegoś powodu nie dotrze (telefon offline, Doze Mode, watcher akurat nie zadziałał),
appka i tak złapie zmianę przy najbliższym cyklicznym sync.

## Koszty — realnie zero, ale pilnuj:

- **Firebase Cloud Messaging**: darmowe zawsze, bez limitu, bez karty.
- **GitHub Actions**: darmowe bez limitu **tylko jeśli repo jest publiczne**. Jeśli
  kiedyś zrobisz je prywatnym — 2000 min/mies. za darmo, a workflow co 5 min zużyje to
  w kilka dni.
- **Nie hostujesz niczego** — GitHub Actions i Firebase to cudza infrastruktura, zero
  serwera po Twojej stronie.

## Jeśli coś nie działa

- **Build appki krzyczy o `google-services.json`** → plik musi być w `app/google-services.json`,
  nie w roocie repo.
- **Workflow na GitHubie czerwony** → sprawdź logi kroku "Sprawdź zmiany i wyślij push";
  najczęstsza przyczyna to literówka w sekrecie `FCM_SERVICE_ACCOUNT_JSON` (musi być
  cały plik JSON, nie sam klucz prywatny).
- **Nie dostajesz pushy mimo zielonego workflow** → sprawdź w appce Ustawienia → czy
  przełącznik powiadomień jest włączony; sprawdź czy appka faktycznie subskrybowała
  temat (log `FcmTopicManager` w logcat filtrowany po "FcmTopicManager").
- Appka jest zaszyta na sztywno pod ZSE Bydgoszcz (wsparcie wielu szkół zostało wycofane) —
  `FcmTopicManager` subskrybuje jeden, stały temat przy każdym starcie appki, nic tu nie
  trzeba konfigurować ani przełączać.
