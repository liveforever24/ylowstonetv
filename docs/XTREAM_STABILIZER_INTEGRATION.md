# Panduan Integrasi `XtreamStabilizer v1.1` — YlowstoneTV

Paket ini berisi 6 file Kotlin mandiri di bawah `com.ylowstone.tv.player.stabilizer` tanpa mengubah layout Home, tema dinamis, maupun `YlowstoneSplashV4Screen.kt` / `splash_intro.mp3`.

## Fitur Proteksi v1.1
1. **IPv4-First DNS (`Ipv4FirstDns`)**: Mencegah perpindahan IPv4 <-> IPv6 di tengah sesi streaming.
2. **Identitas HTTP Seragam (`StrictConnectionGuard`)**: `User-Agent` dan `InMemorySessionCookieJar` identik antara `player_api.php` dan playback Media3.
3. **Network Switch Guard (`NetworkSwitchGuard`)**: Mendeteksi perpindahan Wi-Fi <-> Seluler lalu memutus soket lama secara bersih sebelum membuka sesi di jaringan baru.
4. **Single-Stream Mutex + Zapping Debounce 400ms (`SmartReconnectController`)**: Mencegah koneksi ganda dan *spam request* saat pindah channel cepat.
5. **Natural Live Buffer (`createNaturalLiveLoadControl`)**: Buffer Live TV 15–30 detik agar perilaku tarik segmen terlihat wajar di mata server.
6. **Pil Status Sementara (`StabilizerStatusPill`)**: Pil kecil semi-transparan yang mengikuti tema aktif dan otomatis hilang setelah 2.8 detik saat stream stabil.

---

## 1. Cek Dependensi & Izin
Di `app/build.gradle.kts`, pastikan dependensi Media3 OkHttp aktif:
```kotlin
implementation("androidx.media3:media3-exoplayer:<versi-media3>")
implementation("androidx.media3:media3-exoplayer-hls:<versi-media3>")
implementation("androidx.media3:media3-datasource-okhttp:<versi-media3>")
```
Di `app/src/main/AndroidManifest.xml`, pastikan izin jaringan ada:
```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 2. Inisialisasi di `PlayerViewModel`
```kotlin
val connectionGuard = StrictConnectionGuard()
val streamResolver = XtreamStreamResolver()
val sessionMonitor = XtreamSessionMonitor(connectionGuard)

val exoPlayer = ExoPlayer.Builder(appContext)
    .setLoadControl(connectionGuard.createNaturalLiveLoadControl())
    .build()

val reconnectController = SmartReconnectController(
    player = exoPlayer,
    scope = viewModelScope,
    resolver = streamResolver,
    connectionGuard = connectionGuard,
    sessionMonitor = sessionMonitor
).also { it.attach() }

val networkGuard = NetworkSwitchGuard(appContext) { networkLabel ->
    reconnectController.onNetworkSwitched(networkLabel)
}.also { it.start() }
```

## 3. Memutar Channel
```kotlin
reconnectController.playCanonical(
    source = XtreamCanonicalSource(
        hostUrl = playlist.serverUrl,
        username = playlist.username,
        password = playlist.password,
        streamId = channel.streamId,
        isLive = true
    ),
    customUserAgent = playlist.customUserAgent,
    channelLabel = channel.name
)
```

## 4. Pasang Pil Status Sementara di Overlay Player Compose
Letakkan di dalam `Box` root layar player:
```kotlin
val stabilizerState by reconnectController.state.collectAsState()

StabilizerStatusPill(
    state = stabilizerState,
    onRetryClick = { reconnectController.retryNow() },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 18.dp)
)
```

## 5. Bersihkan Saat `onCleared()`
```kotlin
override fun onCleared() {
    networkGuard.stop()
    reconnectController.detach()
    connectionGuard.shutdown()
    super.onCleared()
}
```
