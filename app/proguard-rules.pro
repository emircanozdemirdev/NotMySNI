# Keep Hilt-generated entry points and injected members stable for release builds.
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# OkHttp uses reflection for platform-specific TLS integrations.
-dontwarn okhttp3.**
-dontwarn okio.**
