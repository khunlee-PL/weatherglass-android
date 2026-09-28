# Weatherglass — Android wrapper (Capacitor + Codemagic, Play Asset Delivery)

The Google Play binary for **Weatherglass**, the twin of `weatherglass-ios/`. Same app, same law:
the base .aab carries the app with **English inline**; every other language is an **on-demand
Play Asset Delivery pack** (`lang_<code>/`, the Play twin of the iOS On-Demand Resource tag) that
`native/WeatherglassLangPlugin.kt` fetches when the reader picks it and sha-verifies against
`www/lang-manifest.json` before trusting it. No IAP, no accounts, no analytics.

## How content flows

```
corpus repo (C:\1Weather):
    tools/build_weatherglass.py  ->  build/weatherglass.html (web) / --lean-langs (store)
    tools/ship_android.py        ->  www/index.html (lean) + PWA shell + icons,
                                     lang_th/, lang_fil/ asset-pack modules, www/lang-manifest.json,
                                     www/ship-manifest.json
this repo (weatherglass-android):
    Codemagic (codemagic.yaml)   ->  cap add/sync android, wires the plugin + packs, builds the
                                     signed .aab (artifact) → upload to Play Console by hand
```

## One-time setup

1. **Upload keystore** — `C:\1Weather\weatherglass-android-signing\weatherglass-upload.jks`
   (alias `weatherglass_upload`, password in `KEYSTORE-README.txt` beside it). Upload it in
   Codemagic → Teams → Code signing identities → Android keystores as **weatherglass_upload**.
   Back that folder up; it is not in git.
2. **Codemagic app** — add this repo; workflow `android-release`; start builds from the UI
   (branch `main`) or push a tag `v*`.
3. **Play Console** — create the app (package `live.weatherglass.app`), opt in to Play App Signing
   (default), fill the store listing (reuse the App Store text), Data safety (location: collected
   only for app functionality, not shared, optional), content rating (IARC questionnaire, all-ages),
   target audience, privacy policy https://weatherglass.live/privacy. Upload the .aab to
   **Internal testing** first; production for a personal developer account requires the
   12-tester / 14-day closed test.

## Every release

```bash
# corpus repo, after the guards are green:
python3 tools/build_weatherglass.py
python3 tools/ship_android.py
git add -A weatherglass-android && git commit
# this repo: push, then start the Codemagic build; download the .aab artifact; upload to Play.
# Bump MARKETING_VERSION in codemagic.yaml for a new version (versionCode = Codemagic build number).
```

## What the CI does

- Guard: lean `www/index.html`, `lang-manifest.json`, every pack file present and matching digests.
- `cap add android` + `cap sync` (Geolocation, Preferences, ScreenOrientation, App plugins from npm).
- Writes `MainActivity.java` that registers `WeatherglassLangPlugin` (local plugins are never
  auto-discovered) and locks phones to portrait / tablets to landscape.
- Wires `assetPacks`, `asset-delivery`, Kotlin, compile/targetSdk 36; declares location permissions.
- `gradlew bundleRelease` signed with the upload keystore → `.aab` artifact.
