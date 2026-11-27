<p align="center">
  <img src="docs/banner.png" alt="YlowstoneTV Banner" width="100%" />
</p>

<h1 align="center">YlowstoneTV</h1>

<p align="center">
  <strong>Zero-bloat native IPTV &amp; Xtream media engine for Android and Android TV.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v2.4.5-0A0C0E?style=flat-square&logo=android&logoColor=E89D3C" alt="Release v2.4.5" />
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Android%20TV-121512?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%C2%B7%20Jetpack%20Compose-1A1E19?style=flat-square" alt="Kotlin Jetpack Compose" />
  <img src="https://img.shields.io/badge/Player-Media3%20ExoPlayer-2B2110?style=flat-square" alt="Media3 ExoPlayer" />
</p>

## Engineering Philosophy

YlowstoneTV is engineered around three strict constraints: **zero visual bloat**, **sub-second navigation across 175,000+ indexed entries**, and **full user control**. No ads, no tracking telemetry, no cramped bottom navigation bars—just an AMOLED-first interface anchored by deterministic SQLite indexing and a persistent Media3 ExoPlayer pipeline.

## Core Capabilities (`v2.4.5`)

- **Zero-Scroll Cinematic Home & Collapsing Scroll Headers (Phone & Android TV):** Pure `#000000` AMOLED single-screen dashboard featuring 3 custom matte WebP cover artworks (*TV*, *Movies*, *Series*) and genuine **InterDisplay** typography, paired with scroll-linked collapsing headers (cover scale + fade, sticky docked Champagne-Taupe Play button), borderless channel lists with active `#D4B88E` + red `• LIVE` indicators, and a floating mini-player bar across TV, Movies, Series, Playlists, History, and Favourites.
- **Borderless 60fps Boomerang Loading Animations:** Hardware-accelerated Jetpack Compose `Canvas` morphing loaders (*Chroma Boomerang*, *Amber Boomerang*, *Aurora Boomerang*) floating cleanly over a dimmed backdrop with live preview inside Settings.
- **Smart Series Playback (Skip Intro & Auto-Next Episode):** Floating *Skip Intro* pill during the opening minutes of an episode with *Off / Manual / Auto* modes and per-series duration memory, paired with a 15-second *Auto-Next Episode* countdown pill for seamless binge playback.
- **Smart Subtitle Sync & Auto-Subtitle Pipeline:** Release-tag candidate scoring for automatic Indonesian/preferred-language subtitles, per-series sync offset memory across episodes, and a 1-tap *Quick Sync* button alongside an accidental-tap-proof drag-only seekbar.
- **Stream Inspector & Quick Favourite:** Long-press any Live TV channel, movie, series, or search result to copy the stream URL (credential-masked in UI preview), probe stream health (**Active / Delay / Offline** with millisecond latency), or toggle Favourite instantly.
- **Responsive Split-Screen & Minimalist Floating/PiP Player:** Split-screen playback adapts to Portrait (video top + queue bottom) and Landscape/TV (side-by-side video + D-Pad queue). Floating Mini-Player and System PiP expose only **Fullscreen**, **Play/Pause**, and **Close (`X`)**.
- **UniversalStreamStabilizer v1.2 (Xtream, M3U/M3U8, Local SAF, .ts, .mp4/.mkv, .mpd), Active-Only Sync & JSON Backup:** Integrated anti-ghost TCP socket eviction, IPv4-first DNS + unified session CookieJar, Single-Stream Mutex with 400ms zapping debounce, clean Wi-Fi/Cellular network handoff, automatic HLS-to-TS fallback, exponential backoff with jitter & quota check, theme-aware status pill, configurable **Buffer Profiles**, single-playlist sync, and 1-file `.json` Backup & Restore.
- **5 Unified Dynamic Theme Palettes & 12 Custom Icons:** 5 unified palettes (*System Auto*, *Dark AMOLED Noir*, *Light*, *Amber Dusk*, *Velvet Crimson*) that dynamically adapt all accents, collapsing header gradients, and cover tints across every screen, paired with Dive-In Home card animations, Strict Tap Guard anti-swipe protection, custom `MM:SS` Skip Intro duration, 12 switchable 3D launcher icons, and 4-digit PIN category locks.

## System Architecture

```text
┌────────────────────────────────────┐      HTTPS / JSON      ┌──────────────────────────────┐
│   YlowstoneTV Android / TV App     │ ◄────────────────────► │   Edge API Backend (api/)    │
│  Kotlin · Jetpack Compose · SQLite │   /api/login           │   PIN Auth · Subtitle Proxy  │
│  Media3 ExoPlayer · OkHttp · Coil  │   /api/subtitles       └──────────────────────────────┘
└─────────────────┬──────────────────┘
                  │ Direct Stream / Xtream / M3U8 / Local .m3u
                  ▼
       User-Provided IPTV Sources          CI/CD: GitHub Actions → Signed Release APK (~6 MB)
```

## Supported Sources

| Source Type | Capabilities |
| :--- | :--- |
| **Xtream Codes API** | Indexed Live TV, VOD Movies, and Series with full season/episode fetching |
| **M3U / M3U8 Playlists** | Remote URL streaming parser or local `.m3u` file import via SAF |
| **Direct Stream URL** | Single-stream playback (`.m3u8`, `.mpd`, `.mp4`) with custom `User-Agent` |

## Build & Deployment

```bash
# Build signed release APK (JDK 17 + Android SDK 35)
./gradlew assembleRelease

# Output artifact
app/build/outputs/apk/release/app-release.apk
```

Prebuilt releases are published automatically via GitHub Actions on the [Releases](https://github.com/liveforever24/ylowstonetv/releases) page.

## Disclaimer

YlowstoneTV is strictly a pure media player application. It does **not** include, host, sell, or distribute any media streams, channels, or playlists. Users are solely responsible for supplying their own legally obtained playlists.

## Credits

Engineered by **ylowstone** · [github.com/liveforever24/ylowstonetv](https://github.com/liveforever24/ylowstonetv)
