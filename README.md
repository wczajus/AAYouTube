# AAYouTube

Prywatna aplikacja na Androida, która pokazuje YouTube (m.youtube.com) na ekranie Android Auto.
Docelowo: OnePlus Nord (Android 12) + Toyota Corolla 2023, połączenie kablem USB.
Aplikacja nie jest przeznaczona do Sklepu Play.

> Uwaga: oglądanie wideo przez kierowcę w czasie jazdy jest niebezpieczne i może skutkować mandatem.

## Jak to działa

- Silnik przeglądarki to **GeckoView** (Firefox), bo Google blokuje logowanie w zwykłym WebView.
- W aucie aplikacja zgłasza się jako aplikacja nawigacyjna (oficjalna biblioteka `androidx.car.app`).
  Daje to pełnoekranową „mapę”, na której zamiast mapy rysujemy stronę YouTube.
- Dotknięcia i przewijanie z ekranu auta są przekazywane do strony.
- Przycisk **Szukaj** otwiera klawiaturę auta i wyszukuje w YouTube.
- Po przełączeniu na Mapy Google strona działa dalej w tle. Po odłączeniu kabla wszystko się zamyka.
- Na telefonie aplikacja pokazuje tę samą stronę: tam logujesz się na konto Google (raz).

## Budowanie

1. Otwórz katalog repozytorium w Android Studio (File → Open).
2. Poczekaj na synchronizację Gradle. Za pierwszym razem pobiera się GeckoView (ok. 100 MB).
3. Podłącz telefon z włączonym debugowaniem USB i uruchom konfigurację `app` (Run ▶).

Wersja GeckoView jest ustawiona na „najnowszą” (`+` w `app/build.gradle.kts`).
Po pierwszym udanym buildzie można ją tam przypiąć na konkretną wersję.

## Budowanie na GitHubie (bez Android Studio)

Plik `.github/workflows/build.yml` buduje APK przy każdym wysłaniu kodu na GitHub.
Gotowy plik: zakładka **Actions** → ostatnie uruchomienie „Build APK” → sekcja **Artifacts** → `AAYouTube-release-apk`
(pobiera się ZIP z plikiem `app-release.apk` w środku). Budowanie można też uruchomić ręcznie przyciskiem **Run workflow**.

## Włączenie w Android Auto (jednorazowo)

1. Na telefonie: Ustawienia → Połączone urządzenia → Ustawienia połączeń → **Android Auto**.
2. Przewiń na dół i stuknij 10 razy w **Wersja**, aż pojawi się informacja o trybie dewelopera.
3. Menu ⋮ (prawy górny róg) → **Ustawienia dla programistów** → zaznacz **Nieznane źródła**.
4. Odłącz kabel, wymuś zatrzymanie Android Auto (Ustawienia → Aplikacje → Android Auto → Wymuś zatrzymanie) i podłącz ponownie.
5. AAYouTube zgłasza się jako aplikacja nawigacyjna. Jeśli ikony nie ma na pierwszej stronie menu w aucie,
   sprawdź kolejne strony oraz ustawienia wyświetlania aplikacji w Android Auto (nazwa opcji zależy od wersji).

## Pierwsze uruchomienie

1. Otwórz AAYouTube na telefonie i zaloguj się na konto YouTube.
2. Podłącz telefon do Corolli i wybierz AAYouTube w menu Android Auto.

## Sterowanie w aucie

| Gest / przycisk | Działanie |
|---|---|
| Stuknięcie | kliknięcie w stronę |
| Przeciągnięcie palcem | przewijanie strony (jeśli nie działa, najpierw stuknij przycisk ze strzałkami na mapie) |
| Szukaj | klawiatura auta, wyniki w YouTube |
| Wstecz | poprzednia strona |
| Start | strona główna YouTube |

## Etap 1 (prototyp): co sprawdzić w aucie

1. Czy ikona AAYouTube jest w menu Android Auto?
2. Czy widać stronę YouTube i czy jest czytelna (rozmiar)?
3. Czy stuknięcie otwiera film?
4. Czy przewijanie działa?
5. Czy wyszukiwanie przez **Szukaj** działa (na postoju; Android Auto może blokować klawiaturę w ruchu)?
6. Czy film gra z obrazem i dźwiękiem?
7. Czy po przełączeniu na Mapy Google dźwięk gra dalej?
8. Czy jesteś zalogowany (po zalogowaniu na telefonie)?

Jeszcze nie ma: blokowania reklam i przełącznika „wideo w czasie jazdy” (kolejne etapy).
