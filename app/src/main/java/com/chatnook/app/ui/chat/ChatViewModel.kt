package com.chatnook.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatnook.app.data.Repositories
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = Repositories.chat,
    val currentUserId: String = "me",
) : ViewModel() {

    val channels: StateFlow<List<ChatChannel>> = repository.getUserChannels(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // One shared flow per channel, created the first time it is opened.
    private val messagesByChannel = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _sendFailed = MutableStateFlow(false)
    val sendFailed: StateFlow<Boolean> = _sendFailed.asStateFlow()

    fun messages(channelId: String): StateFlow<List<ChatMessage>> =
        messagesByChannel.getOrPut(channelId) {
            repository.getChannelMessages(channelId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun updateDraft(text: String) {
        _draft.value = text
    }

    fun send(channelId: String) {
        val content = _draft.value.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.sendMessage(channelId, currentUserId, content) }
                // Only clear what was actually sent, so text typed while in flight is kept.
                .onSuccess { if (_draft.value.trim() == content) _draft.value = "" }
                .onFailure { _sendFailed.value = true }
        }
    }

    fun dismissSendError() {
        _sendFailed.value = false
    }
}
