<p align="center">
  <img src="docs/banner.png" alt="YlowstoneTV Banner" width="100%" />
</p>

<h1 align="center">YlowstoneTV</h1>

<p align="center">
  <strong>Zero-bloat native IPTV &amp; Xtream media engine for Android and Android TV.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v2.5.4-0A0C0E?style=flat-square&logo=android&logoColor=E89D3C" alt="Release v2.5.4" />
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20%7C%20Android%20TV-121512?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%C2%B7%20Jetpack%20Compose-1A1E19?style=flat-square" alt="Kotlin Jetpack Compose" />
  <img src="https://img.shields.io/badge/Player-Media3%20ExoPlayer-2B2110?style=flat-square" alt="Media3 ExoPlayer" />
</p>

## Engineering Philosophy

YlowstoneTV is engineered around three strict constraints: **zero visual bloat**, **sub-second navigation across 175,000+ indexed entries**, and **hardened credential & connection security**. No ads, no tracking telemetry, no cramped bottom navigation bars—just an AMOLED-first interface anchored by deterministic SQLite encryption, Android Keystore, UniversalStreamStabilizer v2.0, and a persistent Media3 ExoPlayer pipeline.

## Core Capabilities (`v2.5.4`)

- **VOD & Series Detail Pages with IMDb Rating & Auto-Translation (`v2.5.4`):** Clicking any Movie or Series opens a clean destination detail screen displaying **Title**, **IMDb Rating badge**, **About/Synopsis**, release year/genre/duration, and **Play** action—followed for Series by full episode lists with **every episode title** and a switchable **List / Grid** view toggle. Expandable Title, IMDb Rating, and Synopsis cards also appear inside **Half-Screen (Split) Player** across Phone (Portrait & Landscape) and Android TV (excluded on Live TV), with automatic **Indonesian & English synopsis/genre translation** via `/api/translate` following app/device language.
- **Dedicated Android TV Side-Column Playback (Zero TV PiP):** On Android TV and Google TV hardware (`UI_MODE_TYPE_TELEVISION` / `FEATURE_LEANBACK`), system PiP and Floating Mini-Player are completely disabled so D-Pad remote navigation remains smooth while playback continues directly in the adjacent half-screen preview column as you browse categories and channels. Phone mode retains full system PiP and Floating Mini-Player support (including on auto-rotate landscape).
- **8K Cover Home, Micro-Animations, & Smart Series Queues:** AMOLED 8K cover cards (*TV*, *Film*, *Seri*) and monochrome *HomeNotchPill* shortcuts (*Continue Watching* & *Favourites* synced across playlists and category tabs) feature subtle **Entrance Depth Zoom** (`1.06 → 1.00`, `900ms`), **Tactile Spring Press** (`0.965`), and `#E89D3C` gold specular rim & TV D-Pad focus highlights. Resuming any Series episode from Continue Watching automatically hydrates the full episode queue in the background so **Next (`>|`)** and channel drawers advance seamlessly to the next episode.
- **UniversalStreamStabilizer v2.0 (Anti-Kick IP Handoff & DoH):** Built-in **DNS-over-HTTPS (DoH)** with **Fast-IPv4 Pinning**, consistent per-session HTTP header & `X-Playback-Session-Id` fingerprinting, **Graceful Connection Teardown + Cool-Down Handshake + active quota check** on Wi-Fi/Cellular switches, **Dual Buffer Profiles** (fast-start Live TV vs large-buffer VOD), **Zapping Pre-Warm**, **Silent Token Auto-Heal** (HTTP 401/403/410), and 2-way `.m3u8 <-> .ts` automatic failover with per-channel session caching.
- **Hardcore Security Architecture:** Backend **SSL Certificate Pinning** (CA SPKI), deterministic AES-256-GCM encryption on `stream_url` in `channels` & `watch_history`, Android Keystore AES-256-GCM encryption for playlist credentials, AES-256-GCM encrypted 1-file Backup & Restore (all playlists + settings + favourites), Release **APK Signature & Anti-Tamper/Anti-Hooking Check**, **Anti-Bruteforce 5-Minute Login Lockout**, aggressive R8 optimization, and dynamic `FLAG_SECURE` anti-capture protection for Standard User mode.
- **Live EPG & Catch-Up, Precision VOD Seekbar, & Playlist Health Inspector:** Inline Xtream `get_short_epg` program titles + progress bars, Fullscreen EPG guide with 1-tap **Catch-Up** replay, **Smart Sleep Timer** (15/30/60/90m or *End of Episode*), **Quick Recall (Last Channel)**, accidental-tap-proof **drag-only VOD seekbar** in Half-Screen Split Player, in-player custom `MM:SS` **Skip Intro** (Fullscreen & Half-Screen), accurate **Playlist Health Inspector** pop-up cards, **5 Unified Dynamic Theme Palettes**, **12 Custom 3D Icons**, and automatic Indonesian subtitle sync.

## System Architecture

```text
┌────────────────────────────────────┐      HTTPS / Pin       ┌───────────────────────────────┐
│   YlowstoneTV Android / TV App     │ ◄────────────────────► │    Edge API Backend (api/)    │
│  Kotlin · Jetpack Compose · SQLite │   /api/login           │  PIN Auth · Subtitle Proxy    │
│  Media3 · Stabilizer v2.0 · DoH    │   /api/subtitles       │  ID/EN Translation Proxy      │
│                                    │   /api/translate       └───────────────────────────────┘
└─────────────────┬──────────────────┘
                  │ Direct Stream / Xtream / M3U8 / Local .m3u
                  ▼
       User-Provided IPTV Sources          CI/CD: GitHub Actions → Signed Release APK (~8 MB)
```

## Supported Sources

| Source Type | Capabilities |
| :--- | :--- |
| **Xtream Codes API** | Indexed Live TV, EPG & Catch-Up, VOD Movies & Series with IMDb/Synopsis detail, full season/episode fetching |
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
