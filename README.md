# 🎯 MarktSniper (Android-app)

Android-app die automatisch zoekt op **Marktplaats**, **2dehands** en **Vinted** en je een melding stuurt zodra er een koopje opstaat.

## Installeren

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
./gradlew assembleRelease   # vereist Android SDK (sdk.dir in local.properties of ANDROID_HOME)
```
