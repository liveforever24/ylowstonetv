<p align="center">
  <img src="docs/banner.png" alt="YlowstoneTV Banner" width="100%" />
</p>

<h1 align="center">YlowstoneTV</h1>

<p align="center">
  <strong>Zero-bloat native IPTV &amp; Xtream media engine for Android and Android TV.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v2.5.2-0A0C0E?style=flat-square&logo=android&logoColor=E89D3C" alt="Release v2.5.2" />
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Android%20TV-121512?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%C2%B7%20Jetpack%20Compose-1A1E19?style=flat-square" alt="Kotlin Jetpack Compose" />
  <img src="https://img.shields.io/badge/Player-Media3%20ExoPlayer-2B2110?style=flat-square" alt="Media3 ExoPlayer" />
</p>

## Engineering Philosophy

YlowstoneTV is engineered around three strict constraints: **zero visual bloat**, **sub-second navigation across 175,000+ indexed entries**, and **hardened credential & connection security**. No ads, no tracking telemetry, no cramped bottom navigation bars—just an AMOLED-first interface anchored by deterministic SQLite encryption, Android Keystore, UniversalStreamStabilizer v2.0, and a persistent Media3 ExoPlayer pipeline.

## Core Capabilities (`v2.5.2`)

- **UniversalStreamStabilizer v2.0 (Anti-Kick IP Handoff & DoH):** Built-in **DNS-over-HTTPS (DoH)** with **Fast-IPv4 Pinning**, consistent per-session HTTP header & `X-Playback-Session-Id` fingerprinting, **Graceful Connection Teardown + Cool-Down Handshake + active quota check** on Wi-Fi/Cellular switches, **Dual Buffer Profiles** (fast-start Live TV vs large-buffer VOD), **Zapping Pre-Warm**, **Silent Token Auto-Heal** (HTTP 401/403/410), and 2-way `.m3u8 <-> .ts` automatic failover with per-channel session caching.
- **Hardcore Security Architecture:** Backend **SSL Certificate Pinning** (CA SPKI), deterministic AES-256-GCM encryption on `stream_url` in `channels` & `watch_history`, Android Keystore AES-256-GCM encryption for playlist credentials, AES-256-GCM encrypted 1-file Backup & Restore (all playlists + settings + favourites), Release **APK Signature & Anti-Tamper/Anti-Hooking Check**, **Anti-Bruteforce 5-Minute Login Lockout**, aggressive R8 optimization, and dynamic `FLAG_SECURE` anti-capture protection for Standard User mode.
- **Live EPG & Catch-Up, Smart Sleep Timer, & Quick Recall:** Inline Xtream `get_short_epg` program titles + live progress bars in Live TV lists, Fullscreen EPG guide with 1-tap **Catch-Up** replay, **Smart Sleep Timer** (15/30/60/90m or *End of Episode*) with clean Xtream socket teardown, and 1-tap **Quick Recall (Last Channel)**.
- **Refined Home, A–Z Jump Index, & Half-Screen VOD Seekbar:** Home is streamlined strictly to **Continue Watching** (locked to Movies & Series only; watch history removed) and **Favourites**. Channel lists feature a right-edge **A–Z Fast Jump Index**, while **Half-Screen (Split) Player** adds an accidental-tap-proof **drag-only VOD seekbar** exclusively for Movies & Series across both **Phone and TV** modes (never Live TV).
- **Fullscreen Frosted Glass OSD, Slide-In Channel Drawer, & Flagship TV Audio/Haptics:** Frosted-glass OSD controls with **Screen Lock**, left-edge swipe **Slide-In Channel Drawer** without leaving Fullscreen, and bespoke low-latency **Flagship TV acoustic navigation chimes & micro-haptics** (toggleable in Settings).
- **5 Unified Dynamic Theme Palettes, 12 Custom Icons, & Auto-Subtitle Pipeline:** 5 unified palettes (*System Auto*, *Dark AMOLED Noir*, *Light*, *Amber Dusk*, *Velvet Crimson*), 12 switchable 3D launcher icons, 60fps Boomerang loading animations, Skip Intro (`MM:SS`) & Auto-Next Episode, and automatic Indonesian/preferred-language subtitle sync.

## System Architecture

```text
┌────────────────────────────────────┐      HTTPS / Pin       ┌──────────────────────────────┐
│   YlowstoneTV Android / TV App     │ ◄────────────────────► │   Edge API Backend (api/)    │
│  Kotlin · Jetpack Compose · SQLite │   /api/login           │   PIN Auth · Subtitle Proxy  │
│  Media3 · Stabilizer v2.0 · DoH    │   /api/subtitles       └──────────────────────────────┘
└─────────────────┬──────────────────┘
                  │ Direct Stream / Xtream / M3U8 / Local .m3u
                  ▼
       User-Provided IPTV Sources          CI/CD: GitHub Actions → Signed Release APK (~6 MB)
```

## Supported Sources

| Source Type | Capabilities |
| :--- | :--- |
| **Xtream Codes API** | Indexed Live TV, EPG & Catch-Up, VOD Movies, and Series with full season/episode fetching |
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
