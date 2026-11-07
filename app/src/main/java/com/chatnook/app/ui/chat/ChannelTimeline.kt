package com.chatnook.app.ui.chat

import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.OutgoingImage
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One channel's message window: the live newest page, older pages loaded on demand, and
 * local rows the server has not acknowledged yet.
 *
 * Merging by id is what makes an optimistic bubble vanish the instant Realtime echoes the
 * real row: the server copy replaces the local one without a gap or a duplicate.
 */
class ChannelTimeline(
    private val channelId: String,
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
    private val myProfile: () -> Profile?,
    private val myUserId: () -> String,
) {
    private companion object {
        const val PAGE_SIZE = 50
    }

    private val live: StateFlow<List<ChatMessage>> =
        repository.getChannelMessages(channelId)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val older = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val local = MutableStateFlow<Map<String, ChatMessage>>(emptyMap())

    /** Bytes for messages still in flight; dropped as soon as the server owns the row. */
    private val pendingImages = mutableMapOf<String, OutgoingImage>()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> =
        combine(older, live, local) { old, fresh, pending ->
            val byId = LinkedHashMap<String, ChatMessage>(old.size + fresh.size + pending.size)
            old.forEach { byId[it.messageId] = it }
            // Server rows win: same id, authoritative timestamp and reactions.
            fresh.forEach { byId[it.messageId] = it }
            pending.values.forEach { byId.putIfAbsent(it.messageId, it) }
            byId.values.sortedWith(compareBy({ it.createdAt }, { it.messageId }))
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Drop a local copy once the server echoes the same id.
        scope.launch {
            live.collect { rows ->
                if (local.value.isEmpty()) return@collect
                val ids = rows.mapTo(HashSet()) { it.messageId }
                local.update { copy -> copy.filterKeys { it !in ids } }
            }
        }
    }

    /** Loads one page above the oldest known message. Returns how many were prepended. */
    suspend fun loadOlder(): Int {
        if (_loadingOlder.value || !_hasMore.value) return 0
        val boundary = older.value.firstOrNull() ?: live.value.firstOrNull() ?: return 0
        _loadingOlder.value = true
        return try {
            val page = repository.loadOlderMessages(channelId, boundary, PAGE_SIZE)
            if (page.isEmpty()) {
                _hasMore.value = false
                0
            } else {
                older.update { current ->
                    (page + current).distinctBy(ChatMessage::messageId)
                }
                if (page.size < PAGE_SIZE) _hasMore.value = false
                page.size
            }
        } catch (_: Throwable) {
            0
        } finally {
            _loadingOlder.value = false
        }
    }

    /**
     * Queues a message locally, then inserts it under the same id. [localImageUri] is only
     * what the optimistic bubble shows; the upload happens in [deliver].
     */
    fun send(
        content: String,
        replyToId: String?,
        image: OutgoingImage? = null,
        localImageUri: String? = null,
    ): String {
        val text = content.trim()
        if (text.isEmpty() && image == null) return ""
        val profile = myProfile()
        val optimistic = ChatMessage(
            messageId = UUID.randomUUID().toString(),
            channelId = channelId,
            senderId = myUserId(),
            content = text,
            messageType = if (image != null) ChatMessage.MessageType.image else ChatMessage.MessageType.text,
            createdAt = System.currentTimeMillis(),
            replyToId = replyToId,
            senderName = profile?.authorLabel,
            senderAvatarUrl = profile?.avatarUrl,
            localImageUri = localImageUri,
            sendState = ChatMessage.SendState.Pending,
        )
        if (image != null) pendingImages[optimistic.messageId] = image
        local.update { it + (optimistic.messageId to optimistic) }
        deliver(optimistic)
        return optimistic.messageId
    }

    fun retry(messageId: String) {
        val failed = local.value[messageId] ?: return
        local.update { it + (messageId to failed.copy(sendState = ChatMessage.SendState.Pending)) }
        deliver(failed)
    }

    private fun deliver(message: ChatMessage) {
        scope.launch {
            runCatching {
                repository.sendMessage(
                    channelId = channelId,
                    content = message.content,
                    replyToId = message.replyToId,
                    messageId = message.messageId,
                    image = pendingImages[message.messageId],
                )
            }.onSuccess { authoritative ->
                pendingImages.remove(message.messageId)
                // Kept local until Realtime echoes it, so the bubble never blinks out.
                local.update { it + (message.messageId to authoritative) }
            }.onFailure {
                local.update { copy ->
                    val current = copy[message.messageId] ?: return@update copy
                    copy + (message.messageId to current.copy(sendState = ChatMessage.SendState.Failed))
                }
            }
        }
    }
}
