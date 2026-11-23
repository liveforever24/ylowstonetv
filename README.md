# YlowstoneTV

> **An aesthetic Xtream & IPTV player, inspired by Apple Music.**

![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Android%20TV-000000?style=flat-square)
![Language](https://img.shields.io/badge/Built%20with-Kotlin%20%26%20Jetpack%20Compose-FA2D48?style=flat-square)
![Engine](https://img.shields.io/badge/Player-Media3%20ExoPlayer-0A84FF?style=flat-square)

**YlowstoneTV** is a lightweight, native media player for Android smartphones and Google/Android TV. Built from the ground up with Kotlin, Jetpack Compose, indexed local SQLite storage, and Media3 ExoPlayer, it delivers smooth high-refresh-rate browsing—even across playlists containing tens of thousands of channels—wrapped in a clean, distraction-free interface.

---

## Key Features

### Design & Performance
* **Apple Music-Inspired Aesthetics:** Minimalist layout featuring a floating translucent glass navigation bar, smooth transitions, and native system typography.
* **Adaptive Themes:** Switch between Pure Black AMOLED, Light, or System Default, paired with customizable accent colors.
* **Built for Large Playlists:** Indexed local database storage and lazy logo caching keep scrolling and searching instantaneous without memory bloat.

### Dual-Device Experience
* **Smartphone & TV Modes:** Dedicated profiles selectable on first launch or anytime in Settings, including D-Pad remote focus navigation and Android TV launcher banner support.
* **Smart Orientation:** Stays in portrait while managing playlists on mobile, then automatically switches to full landscape when browsing channels and watching content.

### Playlist Management & Privacy
* **Multi-Playlist Support:** Save, switch, refresh, edit credentials, or inspect and copy playlist URLs without re-entering data from scratch.
* **Category PIN Lock:** Long-press any category to lock or unlock it with a 4-digit PIN. Locked categories are automatically excluded from search results.
* **Fast Discovery:** Instant in-playlist search, Master Search across all media types, Favorites, and Watch History.

### Playback Engine
* **Media3 ExoPlayer Core:** Hardware-accelerated playback for Live TV, Movies (VOD), and Series with episode browsing.
* **Full Player Controls:** Video, audio, and subtitle track selection, aspect ratio switching (*Fit, Fill, Zoom, 16:9*), screen lock, quick channel drawer, optional brightness/volume swipe gestures, and external player handoff.

### Utility
* **Bilingual Interface:** Available in English (US) and Bahasa Indonesia.
* **In-App Updates:** Check, download, and install new releases directly from GitHub Releases inside the app.

---

## Supported Sources

YlowstoneTV focuses on three reliable, standard media loaders:
1. **Xtream Codes API** — Automatic separation into Live TV, Movies, and Series.
2. **M3U / M3U8 Playlists** — Load from a remote URL or a local `.m3u` file.
3. **Direct Stream URL** — Play single streams (`.m3u8`, `.mpd`, `.mp4`) with optional custom `User-Agent` headers.

---

## Installation

1. Go to the [Releases](https://github.com/liveforever24/ylowstonetv/releases) page.
2. Download the latest `YlowstoneTV-vX.X.X-by-ylowstone.apk` file.
3. Install the APK on your Android smartphone (Android 10+) or Google/Android TV.
4. Future updates can be installed directly via **Settings → Check for App Updates**.

---

## Build from Source

Requires **JDK 17** and **Android SDK API 35**:

    git clone https://github.com/liveforever24/ylowstonetv.git
    cd ylowstonetv
    gradle assembleRelease

---

## Disclaimer

YlowstoneTV is strictly a media player application. It does **not** include, host, provide, or distribute any media content, streams, or playlists. Users are solely responsible for providing their own legally obtained playlists and streaming sources.

---

## Credits

Crafted by **ylowstone** · [github.com/liveforever24/ylowstonetv](https://github.com/liveforever24/ylowstonetv)
