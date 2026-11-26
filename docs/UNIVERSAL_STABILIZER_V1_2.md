# Kontrak Modul `UniversalStreamStabilizer v1.2` — YlowstoneTV

Paket ini menyediakan satu mesin stabilisasi aliran universal di bawah `com.ylowstone.tv.player.stabilizer` untuk **semua** sumber pemutaran YlowstoneTV:
- **Xtream Codes** (Live TV `.m3u8` dengan auto-fallback `.ts`, VOD Film/Seri, dan pemantauan kuota `active_cons`).
- **URL M3U / M3U8 & Aliran Tunggal** (mendukung header kustom `User-Agent`, `Referer`, `Origin`, serta deteksi `Content-Type` untuk link tanpa ekstensi).
- **Berkas `.m3u` / `.m3u8` & Media Lokal** (`file://` dan `content://` melalui `DefaultDataSource.Factory`).
- **Aliran VOD** (`.mp4`, `.mkv`, `.webm`, `.mov`, `.m4v` dengan preset `createVodSeekLoadControl()`).
- **Aliran Video Web** (deteksi otomatis tautan halaman video daring + antarmuka `WebVideoStreamExtractor` yang dapat dihubungkan ke endpoint resolver di folder `api/`).

## Pemakaian Ringkas (Satu Pintu Masuk)

```kotlin
val connectionGuard = StrictConnectionGuard()
val webExtractor = RemoteApiWebVideoExtractor(
    resolverEndpointUrl = "https://<domain-api-ylowstone>/api/resolve-video",
    connectionGuard = connectionGuard
)
val streamResolver = XtreamStreamResolver(webVideoExtractor = webExtractor)
val sessionMonitor = XtreamSessionMonitor(connectionGuard)

val controller = SmartReconnectController(
    appContext = appContext,
    player = exoPlayer,
    scope = viewModelScope,
    resolver = streamResolver,
    connectionGuard = connectionGuard,
    sessionMonitor = sessionMonitor
).also { it.attach() }

// 1) Untuk akun Xtream Codes:
controller.play(
    source = YlowstoneStreamSource.Xtream(
        hostUrl = playlist.serverUrl,
        username = playlist.username,
        password = playlist.password,
        streamId = channel.streamId,
        isLive = true
    ),
    customUserAgent = playlist.customUserAgent,
    channelLabel = channel.name
)

// 2) Untuk URL M3U/M3U8, file lokal, .ts, .mp4 VOD, maupun tautan video web:
controller.playUrl(
    rawUrl = item.url,
    customUserAgent = item.userAgent,
    customHeaders = item.extraHeaders,
    channelLabel = item.title
)
```

## Pil Status Sementara (`StabilizerStatusPill`)
Pasang di `Box` root overlay player untuk menampilkan status semua tipe aliran (`HLS`, `TS`, `VOD`, `DASH`, `WEB`, `AUTO`), otomatis mengikuti 5 tema aktif dan menghilang setelah 2,8 detik saat aliran stabil:

```kotlin
val stabilizerState by controller.state.collectAsState()

StabilizerStatusPill(
    state = stabilizerState,
    onRetryClick = { controller.retryNow() },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 18.dp)
)
```
