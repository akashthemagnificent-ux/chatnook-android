# Compose / Kotlin metadata
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Coil
-dontwarn coil3.**

# Ktor finds engines via META-INF/services + ServiceLoader. R8 renames the engine
# classes but not the service descriptor, so the lookup fails at runtime.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
