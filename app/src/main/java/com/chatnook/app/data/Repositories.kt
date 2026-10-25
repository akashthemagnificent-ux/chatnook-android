package com.chatnook.app.data

import com.chatnook.app.data.repository.SupabaseChatRepository
import com.chatnook.app.domain.repository.ChatRepository

/**
 * Hand-rolled service locator. One instance keeps the in-memory caches alive
 * across screens; swap in DI only if this graph outgrows a single object.
 */
object Repositories {
    val chat: ChatRepository by lazy { SupabaseChatRepository() }
}
