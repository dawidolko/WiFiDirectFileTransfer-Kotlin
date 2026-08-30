# Docker — WiFi Direct File Transfer (Kotlin / Android)

Obraz zawiera JDK 17 i Android SDK (platforma 35, build-tools 35.0.0),
co pozwala zbudowac plik APK bez instalowania SDK na hoscie.

## Czego ten kontener nie robi

Aplikacji **nie da sie uruchomic w kontenerze** — korzysta z Wi-Fi Direct,
wiec wymaga fizycznego urzadzenia z Androidem i dwoch telefonow do przesylania
plikow. Emulator nie obsluguje Wi-Fi Direct. Kontener sluzy wylacznie do
powtarzalnego budowania.

## Wymagania

- Docker Engine 24+ z wtyczka `docker compose`

## Budowanie APK

```bash
cd .tools/docker
docker compose run --rm build
```

Gotowy plik znajdziesz w:

```
WifiDirect/app/build/outputs/apk/debug/app-debug.apk
```

Aby zbudowac wariant release:

```bash
docker compose run --rm build ./gradlew assembleRelease
```

## Drugi modul

Repozytorium zawiera takze modul `WifiP2P`. Zbudujesz go zmieniajac katalog
roboczy:

```bash
docker compose run --rm -w /workspace/WifiP2P build ./gradlew assembleDebug
```

## Instalacja na urzadzeniu

```bash
adb install -r WifiDirect/app/build/outputs/apk/debug/app-debug.apk
```

## Zatrzymanie

```bash
docker compose down -v
```

Flaga `-v` usuwa takze wolumen z cache Gradle'a.
