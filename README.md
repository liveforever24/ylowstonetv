&lt;p align=&quot;center&quot;&gt;
  &lt;img src=&quot;docs/banner.png&quot; alt=&quot;YlowstoneTV Banner&quot; width=&quot;100%&quot; /&gt;
&lt;/p&gt;

&lt;h1 align=&quot;center&quot;&gt;YlowstoneTV&lt;/h1&gt;

&lt;p align=&quot;center&quot;&gt;
  &lt;strong&gt;Zero-bloat native IPTV &amp;amp; Xtream media engine for Android and Android TV.&lt;/strong&gt;
&lt;/p&gt;

&lt;p align=&quot;center&quot;&gt;
  &lt;img src=&quot;https://img.shields.io/badge/Release-v2.3.2-0A0C0E?style=flat-square&amp;logo=android&amp;logoColor=E89D3C&quot; alt=&quot;Release v2.3.2&quot; /&gt;
  &lt;img src=&quot;https://www.google.com/search?q=https://img.shields.io/badge/Platform-Android%252010%252B%2520%257C%2520Android%2520TV-121512%3Fstyle%3Dflat-square&quot; alt=&quot;Platform&quot; /&gt;
  &lt;img src=&quot;https://www.google.com/search?q=https://img.shields.io/badge/Stack-Kotlin%2520%25C2%25B7%2520Jetpack%2520Compose-1A1E19%3Fstyle%3Dflat-square&quot; alt=&quot;Kotlin Jetpack Compose&quot; /&gt;
  &lt;img src=&quot;https://www.google.com/search?q=https://img.shields.io/badge/Player-Media3%2520ExoPlayer-2B2110%3Fstyle%3Dflat-square&quot; alt=&quot;Media3 ExoPlayer&quot; /&gt;
&lt;/p&gt;

## Engineering Philosophy

YlowstoneTV is engineered around three strict constraints: **zero visual bloat**, **sub-second navigation across 175,000+ indexed entries**, and **full user control**. No ads, no tracking telemetry, no cramped bottom navigation bars—just an AMOLED-first interface anchored by deterministic SQLite indexing and a persistent Media3 ExoPlayer pipeline.

## Core Capabilities (`v2.3.2`)

- **Ylowstone Signature Design & Adaptive Bento Engine:** Pure `#000000` AMOLED canvas paired with splash-derived Smoked Olive and Amber Sunstone tones, a borderless floating `Y` emblem, an asymmetric Bento Grid on smartphones (tall Live TV left, stacked Movies/Series right), and a horizontal D-Pad Hero-Rail on Android TV.
- **Native 60fps Boomerang Loading Animations:** Hardware-accelerated Jetpack Compose `Canvas` morphing loaders (*Moto Boomerang*, *Amber Boomerang*, *Aurora Boomerang*) configurable with live preview inside Settings for playlist sync, refresh, and import states.
- **3-in-1 Stream Inspector & Custom Watch Groups:** Long-press any Live TV channel, movie, series, or search result to copy the stream URL (credential-masked in UI preview), run a real-time health probe (**Active / Delay / Offline** with millisecond latency), or organize custom watch groups (*Sports*, *Event Pertandingan*, *Daftar Tontonan*, or user-defined groups) with collapsible Show/Hide controls on Home.
- **Responsive Split-Screen & Minimalist Floating/PiP Player:** Split-screen playback adapts automatically to Portrait (video top + interactive queue bottom) and Landscape/TV (side-by-side video + D-Pad queue). Floating Mini-Player and System PiP expose only **Fullscreen**, **Play/Pause**, and **Close (`X`)**.
- **Auto-Subtitle Pipeline & Drag-Only Seekbar:** Automatic Indonesian/preferred-language subtitle resolution for Movies & Series with automatic `SxxExx` episode/season detection, paired with an accidental-tap-proof drag-only timeline and ±10s double-tap skip.
- **Resilient Streaming & Active-Only Sync:** Configurable **Auto / HLS / MPEG-TS** stream format, tunable **Buffer Profiles** (*Low Latency*, *Balanced*, *Anti-Buffering*), silent **Live TV Auto-Reconnect** on transient network drops, and fast single-playlist sync.
- **Layered Anti-Misclick Navigation:** Back gesture from a channel list returns to Categories while preserving exact scroll position; double-back within 2 seconds exits Categories to Home without intrusive confirmation pop-ups.
- **Theme Presets, 12 Custom Icons & Privacy Guardrails:** 5 curated accent palettes (*Ylowstone Signature*, *Pixel Fog*, *Obsidian Crimson*, *Hydrogen Ice*, *Emerald Studio*) across System/Light/AMOLED modes, 12 switchable 3D launcher icons, 4-digit PIN category locks, and automatic adult-content filtering on Home.

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
# Build signed release APK (JDK 17 + Android SDK 35)
./gradlew assembleRelease

# Output artifact
app/build/outputs/apk/release/app-release.apk
```

Prebuilt releases are published automatically via GitHub Actions on the [Releases](https://www.google.com/search?q=https://github.com/liveforever24/ylowstonetv/releases) page.

## Disclaimer

YlowstoneTV is strictly a pure media player application. It does **not** include, host, sell, or distribute any media streams, channels, or playlists. Users are solely responsible for supplying their own legally obtained playlists.

## Credits

Engineered by **ylowstone** · [github.com/liveforever24/ylowstonetv](https://github.com/liveforever24/ylowstonetv)
