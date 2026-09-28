# Polityka prywatności - eLektron

Ostatnia aktualizacja: wrzesień 2026

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

Dane te nie są nigdzie wysyłane. Ustawienia mogą trafić do kopii zapasowej Androida
(Google), jeśli masz ją włączoną w telefonie - kopia danych ze strony szkoły jest z niej
wykluczona. Odinstalowanie aplikacji usuwa wszystkie te dane.

## Połączenia z internetem

- **Strony szkoły** (plan.zse.bydgoszcz.pl, zastepstwa.zse.bydgoszcz.pl,
  zse.bydgoszcz.pl i zse.edu.bydgoszcz.pl): aplikacja pobiera z nich publicznie dostępne
  informacje. Serwery szkoły widzą, jak przy każdej stronie internetowej, adres IP
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
  czy jest nowsza wersja. GitHub widzi przy tym adres IP i wersję aplikacji; nie są wysyłane
  żadne inne dane. Zasady GitHuba: https://docs.github.com/site-policy/privacy-policies

**Wersja z F-Droid** łączy się wyłącznie ze stronami szkoły.

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

Pytania i zgłoszenia: https://github.com/stasolejnik/elektron/issues
