# 🎯 MarktSniper

App voor Android en je laptop (Windows, Mac, Linux) die automatisch zoekt op **Marktplaats**, **2dehands** en **Vinted** en je een melding stuurt zodra er een koopje opstaat.

## Installeren op je laptop

Ga naar **Releases → latest** op GitHub en download:
- Windows: `MarktSniper-1.0.0.msi`
- Mac: `MarktSniper-1.0.0.dmg`
- Linux: `marktsniper_1.0.0_amd64.deb`

De laptop-app is precies dezelfde app. Sluit je het venster, dan blijft hij in het systeemvak (rechtsonder bij de klok)
doorzoeken en krijg je meldingen. Echt afsluiten: rechtermuisklik op het icoontje → Afsluiten.

## Installeren op Android

1. Download [`release/MarktSniper.apk`](release/MarktSniper.apk) op je telefoon.
2. Open het bestand en sta "installeren uit onbekende bronnen" toe.
3. Open MarktSniper, sta meldingen toe en zet bij **Instellingen** de batterijbesparing uit.

## Functies

- **Snelstart**: iPhone 13/14/15, laptop onder €250, PS5, Switch OLED, AirPods Pro, MacBook Air M1/M2.
- **Elk product**: eigen zoekterm + alle hoofdcategorieën van Marktplaats/2dehands.
- **Precies filteren**: "moet één van deze bevatten" (bv. `iphone 13, iphone 14, iphone 15`) en "uitsluiten"
  (bv. `hoesje, kapot, icloud`).
- **Prijs instellen** per snipe met schuifregelaar of invoervelden.
- **Achtergrond zoeken**: elke 1–30 minuten, ook als de app dicht is en na een herstart.
- **Meldingen** met foto en prijs; tik erop en de advertentie opent.
  Alleen nieuwe advertenties geven een melding.

## Zelf bouwen

```bash
./gradlew :app:assembleRelease          # Android, vereist Android SDK (sdk.dir in local.properties of ANDROID_HOME)
./gradlew :desktop:run                    # laptop-app starten
./gradlew :desktop:packageReleaseMsi      # Windows-installer (op Windows)
```
