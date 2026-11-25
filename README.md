# SnapGrab

A lightweight Android app (no Gradle, no external libraries) that downloads **videos, photos, music and documents** from **Instagram**, **Pinterest** and any web page — with an OLED pure-black UI and a crimson accent.

> **فارسی:** اسنپ‌گرپ یک اپلیکیشن سبک اندروید برای دانلود ویدیو، عکس، آهنگ و سند از اینستاگرام، پینترست و صفحات وب است — با رابط سیاه خالص OLED و رنگ سرخابی. توضیح فارسی در انتهای همین صفحه آمده است.

| | |
|---|---|
| Package | `com.snapgrab.app` |
| Min / Target SDK | Android 6.0 (API 23) / Android 13 (API 33) |
| Size | ~128 KB |
| Languages | English + Persian (فارسی) — follows device language |

## Features

- **Paste & extract** — paste an Instagram / Pinterest / any link, tap *Extract media*, pick items, save.
- **Built-in browser** — log in inside the app, long-press any photo to save it, or tap *Save page media* to scan the loaded page (works even when anonymous extraction is blocked).
- **Download history** — SQLite-backed list; tap to open, long-press for share/delete, clear all.
- **Share receiving** — hit *Share → SnapGrab* in Instagram, Pinterest or any browser; the link is extracted in a popup and saved in one tap.
- **Save all** — queue every found item at once.
- **Foreground download service** — 2 parallel workers, progress/completion notifications.
- **Organized storage** — files land in `Download/SnapGrab/{Images, Videos, Music, Docs}`.
  - Android 10+: saved via `MediaStore.Downloads` (no storage permission needed).
  - Android 6–9: classic path + a `ContentProvider` so other apps can open the files.

## Install

A prebuilt, signed APK (`v1.0`) ships in this repo **in base64 form** (see "Binary assets" below):

```bash
git clone https://github.com/christmtrader-spec/SnapGrab.git
cd SnapGrab
bash tools/restore_binaries.sh      # rebuilds icons, keystore and apk/SnapGrab-v1.0.apk
# then install apk/SnapGrab-v1.0.apk on your phone (enable "install unknown apps")
```

## Build from source

No Gradle is used — the build is plain `aapt2 → javac → d8 → zip → zipalign → sign`:

```bash
# Requirements: ANDROID_HOME with build-tools;34 + platforms;android-35, JAVA_HOME (JDK 17+)
./build_apk.sh
# output: ./SnapGrab.apk
```

The script auto-restores missing binary assets first (icons + keystore), builds, aligns, signs with **v1 + v2 + v3** schemes and verifies the signature.

### Signing

- Keystore: `snapgrab.keystore` (in repo root, restored by the script)
- Alias: `snapgrab`, Store/Key password: `snapgrab2026`

> **Warning:** the signing keystore and its password are committed so that rebuilt APKs can update the installed app. If you plan to make this repository widely public, remove the keystore (and rotate it) — otherwise anyone can sign fake "SnapGrab" updates. For private/personal use this is acceptable.

## Binary assets in this repo

The upload tooling available for this repository could only commit **text** files, so all binary assets are stored base64-encoded under `tools/bin/`:

```
tools/bin/snapgrab.keystore.b64
tools/bin/res/mipmap-*/ic_launcher*.png.b64
tools/bin/apk/SnapGrab-v1.0.apk.b64.00..03
```

`tools/restore_binaries.sh` decodes everything back to the real files (icons into `res/mipmap-*/`, keystore into the repo root, prebuilt APK into `apk/`). `build_apk.sh` runs it automatically when assets are missing, so after a fresh clone you only run `./build_apk.sh`.

`tools/make_icons.py` can also regenerate the launcher icons from scratch (requires Pillow).

## Project structure

```
SnapGrab/
├── AndroidManifest.xml
├── build_apk.sh              # full build+sign pipeline (no Gradle)
├── snapgrab.keystore         # restored from tools/bin/ on first build
├── java/com/snapgrab/app/
│   ├── MainActivity.java     # 3 tabs: Download / Browser / History
│   ├── ShareActivity.java    # receives ACTION_SEND links
│   ├── Extractor.java        # og:meta / IG+Pinterest JSON / CDN / Pinterest API
│   ├── Downloader.java       # HttpURLConnection + redirects + Referer
│   ├── DownloadService.java  # foreground queue, 2 workers, notifications
│   ├── Storage.java          # MediaStore (API 29+) / legacy file path
│   ├── MediaProvider.java    # content://com.snapgrab.app.files for legacy saves
│   ├── HistoryDb.java        # SQLite history
│   ├── MediaAdapter.java / MediaItem.java / Assets.java
├── res/                      # layouts, drawables, values, values-fa (Persian)
└── tools/
    ├── restore_binaries.sh   # rebuild binaries from tools/bin/*.b64
    └── make_icons.py         # regenerate launcher icons
```

## Known limitations

- **Anonymous extraction is best-effort.** Instagram and Pinterest block datacenter IPs with 403 — pasted links may fail. The in-app **Browser** path always works: log in, then use *Save page media* or long-press-save.
- **HLS/DASH streams** (`m3u8`, `mpd`) are skipped; progressive `mp4`, images, audio and documents are supported.
- Only verified at build/signature level (APK compiles, aligns, signs); not tested on an emulator.
