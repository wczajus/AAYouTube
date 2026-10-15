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
- Reklamy blokuje **uBlock Origin**. Aplikacja pobiera go przy pierwszym uruchomieniu z oficjalnej strony dodatków
  Mozilli (addons.mozilla.org, potrzebny internet) i przy każdym kolejnym sprawdza aktualizacje. Gdyby reklama w filmie
  mimo to się pojawiła, aplikacja odtwarza ją wyciszoną z maksymalną prędkością i naciska „Pomiń”.
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

### Stały klucz podpisu (aktualizacja bez odinstalowywania)

Bez własnego klucza każdy build na GitHubie ma inny podpis i Android nie przyjmie go jako aktualizacji.
Klucz tworzy się raz, na swoim komputerze (PowerShell, `keytool` z Android Studio):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -keystore aayoutube.jks `
    -alias aayoutube -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=AAYouTube"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\aayoutube.jks")) | Set-Clipboard
```

Potem na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**:

- `RELEASE_KEYSTORE_BASE64`: zawartość schowka (zakodowany plik `aayoutube.jks`),
- `RELEASE_KEYSTORE_PASSWORD`: hasło podane przy tworzeniu klucza.

Opcjonalnie `RELEASE_KEY_ALIAS` i `RELEASE_KEY_PASSWORD`, jeśli klucz ma inny alias lub osobne hasło.
Plik `aayoutube.jks` i hasło zachowaj (bez nich nie da się wydać aktualizacji) i nigdy nie dodawaj ich do repozytorium.
Pierwszy build z tym kluczem trzeba jeszcze zainstalować po odinstalowaniu starej wersji; kolejne instalują się jako aktualizacje.

## Włączenie w Android Auto (jednorazowo)

Ekran wideo (projekcja) Android Auto pokazuje tylko wtedy, gdy telefon uznaje aplikację za zainstalowaną
ze Sklepu Play. Na OnePlusie z Androidem 12 nie ustawi tego ani adb (`-i com.android.vending` jest ignorowane),
ani KingInstaller w trybie podstawowym. Działa natomiast systemowy instalator pakietów, któremu aplikacja
przekazuje nazwę instalatora (`Intent.EXTRA_INSTALLER_PACKAGE_NAME`). Tak zainstalowany jest Local Video Player.
AAYouTube ma do tego przycisk **Zainstaluj APK**.

1. Na telefonie: Ustawienia → Połączone urządzenia → Ustawienia połączeń → **Android Auto**.
2. Przewiń na dół i stuknij 10 razy w **Wersja**, aż pojawi się informacja o trybie dewelopera.
3. Menu ⋮ (prawy górny róg) → **Ustawienia dla programistów** → zaznacz **Nieznane źródła**.
4. Zainstaluj `app-release.apk` w dowolny sposób (np. `adb install`) i skopiuj ten sam plik do telefonu
   (np. `adb push app-release.apk /sdcard/Download/`).
5. Otwórz AAYouTube na telefonie, stuknij **Zainstaluj APK** i wybierz ten sam plik.
   Za pierwszym razem Android poprosi o zgodę na instalowanie aplikacji z AAYouTube: zezwól i wróć.
6. Potwierdź aktualizację. Po ponownym otwarciu AAYouTube napis nad stroną powinien brzmieć
   „Źródło instalacji: Sklep Play”.
7. Odłącz kabel, wymuś zatrzymanie Android Auto (Ustawienia → Aplikacje → Android Auto → Wymuś zatrzymanie) i podłącz ponownie.

Każdą nową wersję instaluj tak samo. Jeśli wersja z GitHuba ma inny klucz podpisu, najpierw odinstaluj starą
(ze stałym kluczem, patrz wyżej, nie trzeba).

## Pierwsze uruchomienie

1. Otwórz AAYouTube na telefonie i zaloguj się na konto YouTube.
2. W aucie Android Auto nie pokazuje ikony aplikacji spoza Sklepu Play, ale wznawia ostatnio używaną
   nawigację. Dlatego najpierw uruchom ekran na emulatorze auta (Desktop Head Unit z Android Studio):
   w Android Auto menu ⋮ → **Włącz serwer radioodtwarzacza**, na komputerze `adb forward tcp:5277 tcp:5277`
   i `desktop-head-unit.exe`, a w emulatorze otwórz **AAYouTube Wideo**.
3. Zamknij emulator (bez stukania w dolny pasek Android Auto), odłącz telefon i podłącz go w aucie.
   Android Auto otworzy AAYouTube Wideo jako nawigację. Po przełączeniu na inną nawigację powtórz krok 2.

## Sterowanie w aucie

| Gest / przycisk | Działanie |
|---|---|
| Stuknięcie | kliknięcie w stronę |
| Przeciągnięcie palcem | przewijanie strony (jeśli nie działa, najpierw stuknij przycisk ze strzałkami na mapie) |
| Stuknięcie, a zaraz potem przeciągnięcie | przeciąganie od stukniętego miejsca, np. suwaka na pasku postępu filmu (Android Auto nie podaje, gdzie zaczyna się przeciąganie) |
| ⏪ / ⏩ (przy mapie) | film 10 s wstecz / do przodu |
| ⛶ (przy mapie) lub przycisk pełnego ekranu w odtwarzaczu | film na cały ekran auta (to samo ponownie: wyjście) |
| Podwójne stuknięcie | na pełnym ekranie: wyjście z pełnego ekranu; poza nim trafia do strony (w odtwarzaczu YouTube przewija o 10 s) |
| Szukaj | klawiatura auta, wyniki w YouTube |
| Wstecz | poprzednia strona |
| Start | strona główna YouTube |

Dźwięk: film przejmuje dźwięk jak każdy odtwarzacz (np. radio się zatrzymuje). Włączenie innego
odtwarzacza pauzuje film; po rozmowie telefonicznej film gra dalej, a komunikaty nawigacji
tylko go ściszają.

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
