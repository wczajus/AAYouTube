# AAYouTube

Prywatna aplikacja na Androida, która pokazuje YouTube (m.youtube.com) na ekranie Android Auto.
Docelowo: OnePlus Nord (Android 12) + Toyota Corolla 2023, połączenie kablem USB.
Aplikacja nie jest przeznaczona do Sklepu Play.

> Uwaga: oglądanie wideo przez kierowcę w czasie jazdy jest niebezpieczne i może skutkować mandatem.

## Jak to działa

- Silnik przeglądarki to **GeckoView** (Firefox), bo Google blokuje logowanie w zwykłym WebView.
- W aucie aplikacja używa starego mechanizmu **projekcji** Android Auto (tak jak Fermata Auto).
  Nowa oficjalna biblioteka `androidx.car.app` odpada: Android Auto 17 w aucie przepuszcza jej aplikacje
  tylko wtedy, gdy są zainstalowane ze Sklepu Play.
- Biblioteka projekcji (`aauto.aar`) nie jest w tym repozytorium. Gradle pobiera ją przy budowaniu
  z ustalonej wersji projektu Fermata i sprawdza sumę SHA-256 (`app/build.gradle.kts`).
  To nieoficjalny, zamknięty plik Google; używamy go wyłącznie prywatnie.
- W aucie: pasek z przyciskami **Szukaj**, **Wstecz**, **Start** nad stroną YouTube. Dotyk działa bezpośrednio,
  wyszukiwanie korzysta z klawiatury auta.
- Dodatkowo aplikacja ma usługę multimedialną **AAYouTube Audio** (na razie tylko z podpowiedzią), która też
  pojawia się w Android Auto. Ekran z wideo to osobna ikona **AAYouTube**.
- Po odłączeniu kabla strona w aucie się zamyka i odtwarzanie się zatrzymuje.
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

Android Auto 17.x pokazuje w aucie tylko aplikacje, które telefon uznaje za zainstalowane ze Sklepu Play.
Zwykła instalacja pliku APK nie wystarcza, nawet z włączonymi „Nieznanymi źródłami”.
Dlatego AAYouTube instalujemy przez KingInstaller (narzędzie innej firmy: https://github.com/fcaronte/KingInstaller).

1. Na telefonie: Ustawienia → Połączone urządzenia → Ustawienia połączeń → **Android Auto**.
2. Przewiń na dół i stuknij 10 razy w **Wersja**, aż pojawi się informacja o trybie dewelopera.
3. Menu ⋮ (prawy górny róg) → **Ustawienia dla programistów** → zaznacz **Nieznane źródła**.
4. Odinstaluj AAYouTube, jeśli był zainstalowany zwykłym instalatorem.
5. W KingInstaller wybierz `app-release.apk` i zainstaluj metodą podstawową (bez przełączników).
6. W KingInstaller otwórz **App Diagnostic Checker** i sprawdź AAYouTube oraz, dla porównania, Fermata Control
   (ona działa w aucie). Pola „Installed by” i „Requested by” powinny być takie same w obu aplikacjach,
   „Installed by” to Sklep Play (`com.android.vending`). Jeśli nie, spróbuj w KingInstaller metody **Shizuku**
   albo **Auto-Fixer**. Ekran z wideo (projekcja) najpewniej wymaga źródła „Sklep Play” (tak działa Fermata);
   usługa multimedialna pojawia się także bez tego.
7. Odłącz kabel, wymuś zatrzymanie Android Auto (Ustawienia → Aplikacje → Android Auto → Wymuś zatrzymanie) i podłącz ponownie.

Każdą nową wersję AAYouTube instaluj tak samo, przez KingInstaller.

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
