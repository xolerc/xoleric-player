# XOLERIC — Premium Music Player

4K tiniq, premium UI/UX'ga ega Android musiqa pleyer.

## Xususiyatlari
- **Vertikal fon animatsiyasi** — Choreographer/vsync bilan, millisekund aniqlik, pas yo'q (hardware Canvas)
- **mydrime loader** — matrix shamol → XOLERIC logo (shunchaki icon emas, to'liq animatsiyali loader)
- **4K tiniq ranglar** — yuqori kontrast, xira emas, chuqur qora + po'lat-ko'k
- **Musiqa pleyer** — MediaStore skan, play/pause/next/prev, progress, albom art
- **Nol external dependency** — Android framework (MediaPlayer), offline build xavfsiz

## Texnik
- Native Android (Java), compileSdk 34, minSdk 24
- AGP 8.11.1, Gradle 8.14
- Signed release (CN=XOLERIC)

## Build
```bash
./gradlew assembleRelease --offline
```

## Yuklab olish
GitHub Releases'dan APK'ni yuklab oling yoki QR kod orqali.
