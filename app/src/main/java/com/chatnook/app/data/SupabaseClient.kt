package com.chatnook.app.data

import com.chatnook.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Single source of truth for the ChatNook Supabase connection.
 *
 * Credentials are injected by the build (see app/build.gradle.kts BuildConfig fields)
 * so no secrets live in source control.
 */
object Supabase {

    /** Deep-link scheme/host used by magic-link sign-in. Must also be registered in
     *  the Supabase dashboard under Authentication > URL Configuration > Redirect URLs. */
    const val AUTH_SCHEME = "chatnook"
    const val AUTH_HOST = "auth"

    /** True when the build provided real credentials instead of the placeholders. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
                !BuildConfig.SUPABASE_URL.contains("your-project")

    /**
     * Lazily created client. Creating it eagerly would throw on placeholder
     * credentials, which would crash the app before the UI can warn the user.
     */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                // PKCE + deep link so magic links return into the app.
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    /** Returns the client, or null when the build has no real credentials. */
    fun clientOrNull(): SupabaseClient? = if (isConfigured) client else null
}
