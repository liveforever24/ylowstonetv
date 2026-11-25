<p align="center">
  <img src="docs/banner.png" alt="YlowstoneTV Banner" width="100%" />
</p>

<h1 align="center">YlowstoneTV</h1>

<p align="center">
  <strong>Zero-bloat native IPTV &amp; Xtream media engine for Android and Android TV.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v2.3.1-0A0C0E?style=flat-square&logo=android&logoColor=F59E0B" alt="Release v2.3.1" />
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Android%20TV-141618?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%C2%B7%20Jetpack%20Compose-202B33?style=flat-square" alt="Kotlin Jetpack Compose" />
  <img src="https://img.shields.io/badge/Player-Media3%20ExoPlayer-526A7A?style=flat-square" alt="Media3 ExoPlayer" />
</p>

---

## Engineering Philosophy

YlowstoneTV is built around three non-negotiable constraints: **zero visual bloat**, **sub-second navigation across 175,000+ indexed entries**, and **full user control**. No ads, no tracking telemetry, no cramped bottom navigation bars—just pure AMOLED-first typography, deterministic SQLite indexing, and a persistent Media3 playback pipeline.

## Core Capabilities (`v2.3.1`)

- **Adaptive Multi-Device Engine:** Asymmetric Bento Grid home layout on smartphones and horizontal D-Pad Hero-Rail on Android TV. Responsive **Split-Screen Player** adapts automatically to Portrait (video top + queue bottom) and Landscape/TV (side-by-side playback + queue).
- **3-in-1 Stream Inspector & Custom Watch Groups:** Long-press any Live TV channel, movie, series, or search result to copy the stream URL (masked in UI preview), run a real-time connection health probe (**Active / Delay / Offline** with millisecond latency), or curate custom watch groups (*Sports*, *Event Pertandingan*, *Daftar Tontonan*, or user-defined groups) with Show/Hide toggles on Home.
- **Minimalist Floating Player & System PiP:** Distraction-free rounded floating window with zero idle chrome—single tap reveals only **Fullscreen**, **Play/Pause**, and **Close (`X`)**. System PiP mirrors the same minimal footprint.
- **Auto-Subtitle Pipeline & Drag-Only Seekbar:** Automatic Indonesian subtitle lookup and attachment for Movies & Series (with persistent language preference and automatic `SxxExx` episode/season detection), paired with an accidental-tap-proof drag-only timeline and ±10s double-tap skip.
- **Resilient Playback Controls:** Configurable **Auto / HLS / MPEG-TS** stream format, tunable **Buffer Profiles** (*Low Latency*, *Balanced*, *Anti-Buffering*), silent **Live TV Auto-Reconnect** on transient network drops, and active-playlist-only fast sync.
- **Layered Anti-Misclick Navigation:** Back gesture from a channel list returns to Categories while preserving exact scroll position; double-back within 2 seconds exits Categories to Home without intrusive confirmation pop-ups.
- **AMOLED Theme Presets & 12 Custom Icons:** 5 curated accent palettes (*Pixel Fog*, *Obsidian Crimson*, *Hydrogen Ice*, *Emerald Studio*, *Amber Sunstone*) across System/Light/AMOLED modes plus 12 switchable 3D launcher icons and 4-digit PIN category locks with regex-level adult content filtering.

## System Architecture

```text
┌────────────────────────────────────┐      HTTPS / JSON      ┌──────────────────────────────┐
│   YlowstoneTV Android / TV App     │ ◄────────────────────► │   Vercel Edge API (api/)     │
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
# Clone & build signed release APK (JDK 17 + Android SDK 35)
./gradlew assembleRelease

# Output artifact
app/build/outputs/apk/release/app-release.apk
```

Prebuilt releases are published automatically via GitHub Actions on the [Releases](https://github.com/liveforever24/ylowstonetv/releases) page.

## Disclaimer

YlowstoneTV is strictly a pure media player application. It does **not** include, host, sell, or distribute any media streams, channels, or playlists. Users are solely responsible for supplying their own legally obtained playlists.

## Credits

Engineered by **ylowstone** · [github.com/liveforever24/ylowstonetv](https://github.com/liveforever24/ylowstonetv)
