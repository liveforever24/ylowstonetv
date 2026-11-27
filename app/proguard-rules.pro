# Keep MainActivity entry point
-keep class com.ylowstone.tv.MainActivity { *; }

# Keep model enum values() & valueOf(String) for SharedPreferences, SQLite & JSON
-keepclassmembers enum com.ylowstone.tv.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# High-security Release hardening: aggressive optimization, repackaging & strip debug logs
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'yl'
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Preserve Media3 reflection-loaded HLS/DASH factories & PlayerView
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { <init>(...); }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { <init>(...); }
-keep class androidx.media3.ui.PlayerView { *; }
-dontwarn androidx.media3.**

# Suppress optional runtime-checked dependencies for OkHttp / Okio / Coil
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn coil.**
-dontwarn kotlinx.coroutines.**
