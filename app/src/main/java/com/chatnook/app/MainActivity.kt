package com.chatnook.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chatnook.app.data.Supabase
import com.chatnook.app.ui.ChatNookApp
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Magic-link / recovery links land here as chatnook://auth?code=...
        Supabase.clientOrNull()?.handleDeeplinks(intent)
        setContent { ChatNookApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Supabase.clientOrNull()?.handleDeeplinks(intent)
    }
}
