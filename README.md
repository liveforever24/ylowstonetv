<div align="center">

# YlowstoneTV

**Native IPTV & Xtream Streaming Architecture for Android & Android TV**

[![Version](https://img.shields.io/badge/release-v2.3.1-0B1116?style=flat-square&labelColor=B5C8D6)](https://github.com/liveforever24/ylowstonetv/releases)
[![Platform](https://img.shields.io/badge/platform-Android%2010--17%20%26%20Android%20TV-141618?style=flat-square)](https://github.com/liveforever24/ylowstonetv)
[![Stack](https://img.shields.io/badge/stack-Kotlin%20%E2%80%A2%20Compose%20%E2%80%A2%20Media3-141618?style=flat-square)](https://github.com/liveforever24/ylowstonetv)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-141618?style=flat-square)](https://github.com/liveforever24/ylowstonetv/actions)

</div>

---

## Overview

YlowstoneTV is a native streaming client engineered for low overhead, predictable state transitions, and zero-clutter navigation across smartphones and Android TV. Built on **Jetpack Compose** and **Media3 ExoPlayer**, it replaces bloated navigation bars and speculative background syncs with explicit user control, local SQLite indexing, and adaptive split-screen playback.

---

## Core Capabilities (`v2.3.1`)

* **Active-Profile Synchronization:** Sync operations target exclusively the active playlist profile rather than mutating unrelated profiles or re-fetching remote default catalogs unnecessarily.
* **Clean Playlist Management:** Playlist cards remain focused on profile identity and quick launch; reload/sync triggers are isolated inside the per-profile action menu (`Edit`, `Show Link`, `Refresh`, `Delete`) to prevent accidental resyncs.
* **Deterministic Back Navigation:**
  * Single back inside a category channel list returns to the category list while preserving scroll and selection context (e.g., returning from `UK * Entertainment` keeps focus anchored at that category).
  * Single back at the category root stays in place with a non-blocking cue; pressing back twice within two seconds returns directly to `Home`.
* **Adaptive Split-Screen & Minimal Floating PiP:**
  * **Smartphone (Portrait):** Video surface above with an interactive active queue below.
  * **Android TV / Landscape:** Side-by-side playback with D-pad focus support and an adjacent queue panel.
  * **Floating Player:** Minimal three-action overlay (`Expand Fullscreen`, `Play/Pause`, `Close`) without redundant controls.
* **Automatic Subtitle Persistence:** Automatically resolves and applies Indonesian subtitles—or the user's last selected subtitle language—across movies, seasons, and consecutive episodes.
* **Streaming & Theme Controls:** Supports automatic playlist stream-format adaptation alongside HLS/TS selection, configurable buffer profiles, and minimalist theme accents (*Pixel Fog*, *Obsidian Crimson*, *Hydrogen Cyan*, *Emerald Studio*).
* **Regex Privacy Filtering:** Filters adult metadata at both query and presentation layers without cluttering standard browse views.

---

## Architecture

```text
+-----------------------------------+        HTTPS / JSON        +-------------------------------+
| Android & Android TV Client (v2.3.1)| <------------------------> | Vercel API (/api/login)       |
|  - Jetpack Compose UI             |                            |  - Auth & Profile Provisioning|
|  - Media3 ExoPlayer + OkHttp      |                            +-------------------------------+
|  - SQLite Local Index & History   |
+-----------------+-----------------+
                  |
                  | git push
                  v
+-----------------------------------+
| GitHub Actions (setup-java@v5)    |
|  - Gradle 8.9 Release Assembly    |
|  - Automated APK Artifacts        |
+-----------------------------------+
```

---

## Repository Structure

* `app/` — Kotlin source, Jetpack Compose screens, Media3 player integration, SQLite persistence (`DatabaseHelper`, `AppPrefs`), and adaptive icon variants.
* `api/` & `public/` — Serverless authentication endpoint and web distribution assets.
* `docs/` — Project documentation and release notes.
* `.github/workflows/` — Automated release build pipeline.

---

## Build & Release

```bash
./gradlew assembleRelease --stacktrace
```

Release builds are compiled via GitHub Actions on `ubuntu-26.04` with JDK 17 (`actions/setup-java@v5`) and Gradle `8.9`.
