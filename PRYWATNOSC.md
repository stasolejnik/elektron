# Polityka prywatności - eLektron

Ostatnia aktualizacja: październik 2026

eLektron to nieoficjalna aplikacja z planem lekcji, zastępstwami i ogłoszeniami
Zespołu Szkół Elektronicznych w Bydgoszczy. Poniżej opisujemy, jakie dane przetwarza.

## W skrócie

- Nie zakładasz konta i nie podajesz żadnych danych osobowych.
- Aplikacja nie zawiera reklam, analityki ani śledzenia.
- Twoje ustawienia są zapisane tylko na Twoim telefonie.

## Dane zapisane na telefonie

- **Ustawienia:** wybrana klasa, grupy zajęciowe, motyw, ustawienia powiadomień.
- **Kopia danych ze strony szkoły:** plan lekcji, zastępstwa i ogłoszenia (żeby aplikacja
  działała bez internetu).
- **Ulubione ogłoszenia:** wybrane wpisy i pobrana treść artykułów, przechowywane lokalnie również po czyszczeniu danych podręcznych.
- **Raport ostatniej awarii** (tylko jeśli aplikacja się zamknęła z powodu błędu): wersja
  aplikacji, model telefonu, wersja Androida, techniczny opis błędu, stan ustawień ważnych dla
  działania aplikacji (czy powiadomienia są włączone, oszczędzanie baterii, dokładne alarmy,
  rodzaj połączenia z internetem) i ostatnie logi samej aplikacji (komunikaty techniczne, np.
  o błędach połączenia ze stroną szkoły - bez danych osobowych). Przy następnym uruchomieniu możesz go skopiować, wysłać e-mailem do dewelopera,
  zgłosić na GitHubie albo pominąć - w każdym przypadku raport jest potem usuwany.
  Aplikacja nigdy nie wysyła go sama. Taki sam raport (bez awarii) tworzy przycisk
  **Zgłoś problem** w Ustawieniach.

Aplikacja nie wysyła tych danych automatycznie. Przyciski **Udostępnij dzień** i **Udostępnij tydzień** pozwalają przekazać plan wybranego dnia lub tygodnia (klasę, godziny, przedmioty, sale, nauczycieli i zastępstwa) do aplikacji, którą sam wybierzesz w systemowym oknie udostępniania.

Ustawienia mogą trafić do kopii zapasowej Androida
(Google), jeśli masz ją włączoną w telefonie - kopia danych ze strony szkoły jest z niej
wykluczona. Odinstalowanie aplikacji usuwa wszystkie te dane.

## Połączenia z internetem

- **Strony szkoły** (plan.zse.bydgoszcz.pl, zastepstwa.zse.bydgoszcz.pl,
  zse.bydgoszcz.pl i zse.edu.bydgoszcz.pl): aplikacja pobiera z nich publicznie dostępne
  informacje. Po otwarciu ogłoszenia lub dodaniu go do ulubionych pobiera także treść artykułu do czytania offline. Serwery szkoły widzą, jak przy każdej stronie internetowej, adres IP
  i wersję aplikacji.
- **Firebase Cloud Messaging (Google)** - tylko w wersji z GitHuba (w wersji z F-Droid
  Firebase nie ma): służy wyłącznie do powiadomień push o nowych zastępstwach
  i ogłoszeniach. Telefon subskrybuje ogólne tematy powiadomień wspólne dla wszystkich
  użytkowników - nie jest przesyłana wybrana klasa ani żadne dane osobowe. Google przetwarza
  przy tym techniczny identyfikator instalacji aplikacji, zgodnie
  z [zasadami prywatności Google](https://policies.google.com/privacy).
  O tym, które powiadomienia pokazać, decyduje aplikacja na telefonie, na podstawie
  Twoich ustawień.
- **GitHub (sprawdzanie aktualizacji)** - tylko w wersji z GitHuba: najwyżej co kilka
  godzin aplikacja pobiera z api.github.com publiczną listę wydań eLektrona, żeby sprawdzić,
  czy jest nowsza wersja. Po naciśnięciu **Aktualizuj** pobiera z GitHuba plik APK nowej
  wersji (zapisywany tymczasowo w pamięci podręcznej aplikacji) i otwiera instalator systemu -
  instalację zawsze zatwierdzasz sam. GitHub widzi przy tym adres IP i wersję aplikacji; nie są
  wysyłane żadne inne dane. Zasady GitHuba: https://docs.github.com/site-policy/privacy-policies

**Wersja z F-Droid** łączy się wyłącznie ze stronami szkoły.

## Notatki do lekcji

Notatki i ustawienie ich przypomnień są zapisywane lokalnie na telefonie, oddzielnie od danych podręcznych szkoły. Nie są wysyłane do szkoły, Firebase ani GitHuba; mogą wejść do systemowej kopii zapasowej Androida, tak jak ustawienia. Czyszczenie danych podręcznych ich nie usuwa. Przypomnienia działają lokalnie i są domyślnie wyłączone. Treść notatki jest ukrywana na ekranie blokady zgodnie z ustawieniami systemu.

Udostępnianie zastępstw przekazuje wybrane informacje do aplikacji wskazanej przez użytkownika w systemowym oknie udostępniania. Własne notatki nie są dodawane do udostępnianego planu ani zastępstw.

## Uprawnienia

- **Internet:** pobieranie danych ze stron szkoły i powiadomień.
- **Powiadomienia:** informowanie o nowych zastępstwach i ogłoszeniach (możesz je
  wyłączyć w ustawieniach aplikacji albo telefonu).

## Dzieci

Aplikacja jest przeznaczona dla uczniów, także niepełnoletnich. Ponieważ nie zbiera
danych osobowych, nie wymaga zgody rodzica na przetwarzanie danych.

## Kod źródłowy i kontakt

eLektron jest wolnym oprogramowaniem - każdy może sprawdzić, co robi aplikacja:
https://github.com/stasolejnik/elektron

Kontakt z deweloperem: **kontakt.elektron@pm.me**
Zgłoszenia błędów: https://github.com/stasolejnik/elektron/issues
