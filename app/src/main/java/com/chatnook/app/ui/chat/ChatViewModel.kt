package com.chatnook.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatnook.app.data.Repositories
import com.chatnook.app.data.Supabase
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.model.ProfileTheme
import com.chatnook.app.domain.repository.ChatRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository = Repositories.chat,
) : ViewModel() {

    /** Live auth id; empty until a session exists. */
    val currentUserId: String
        get() = Supabase.clientOrNull()?.auth?.currentUserOrNull()?.id.orEmpty()

    val guilds: StateFlow<List<Guild>> = repository.getGuilds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedGuildId = MutableStateFlow<String?>(null)
    val selectedGuildId: StateFlow<String?> = _selectedGuildId.asStateFlow()

    /** Channels of the selected guild; empty when no guild is picked yet. */
    val channels: StateFlow<List<ChatChannel>> = _selectedGuildId
        .filterNotNull()
        .flatMapLatest { repository.getChannels(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myProfile: StateFlow<Profile?> = repository.getMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // One shared flow per channel, created the first time it is opened.
    private val messagesByChannel = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    private val noEmojis = MutableStateFlow(emptyList<CustomEmoji>())
    private val emojisByGuild = mutableMapOf<String, StateFlow<List<CustomEmoji>>>()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    /** Transient banner text for errors and invite codes. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        // Pick the first server once the list arrives, so the channel list is never
        // stranded on an empty selection.
        viewModelScope.launch {
            guilds.collect { list ->
                if (list.none { it.guildId == _selectedGuildId.value }) {
                    _selectedGuildId.value = list.firstOrNull()?.guildId
                }
            }
        }
    }

    fun selectGuild(guildId: String) {
        _selectedGuildId.value = guildId
    }

    fun messages(channelId: String): StateFlow<List<ChatMessage>> =
        messagesByChannel.getOrPut(channelId) {
            repository.getChannelMessages(channelId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun updateDraft(text: String) {
        _draft.value = text
    }

    /** Guild uploads, offered alongside the unicode reactions. */
    fun emojis(guildId: String?): StateFlow<List<CustomEmoji>> =
        if (guildId == null) {
            noEmojis
        } else {
            emojisByGuild.getOrPut(guildId) {
                repository.getCustomEmojis(guildId)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }
        }

    fun addEmoji(guildId: String, name: String, url: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createCustomEmoji(guildId, name, url) }
                .onSuccess { _notice.value = "Added :${it.name}:" }
                .onFailure { _notice.value = "Emoji failed: ${it.friendly()}" }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun send(channelId: String, replyToId: String? = null) {
        val content = _draft.value.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.sendMessage(channelId, content, replyToId) }
                // Only clear what was actually sent, so text typed while in flight is kept.
                .onSuccess { if (_draft.value.trim() == content) _draft.value = "" }
                .onFailure { _notice.value = "Send failed: ${it.friendly()}" }
        }
    }

    fun edit(messageId: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.editMessage(messageId, content.trim()) }
                .onFailure { _notice.value = "Edit failed: ${it.friendly()}" }
        }
    }

    fun delete(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteMessage(messageId) }
                .onFailure { _notice.value = "Delete failed: ${it.friendly()}" }
        }
    }

    fun react(messageId: String, emoji: String) {
        viewModelScope.launch {
            runCatching { repository.toggleReaction(messageId, emoji) }
                .onFailure { _notice.value = "Reaction failed: ${it.friendly()}" }
        }
    }

    fun createGuild(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createGuild(name) }
                .onSuccess { guild ->
                    _selectedGuildId.value = guild.guildId
                    _notice.value = "Created ${guild.name}"
                }
                .onFailure { _notice.value = "Create failed: ${it.friendly()}" }
        }
    }

    fun createChannel(name: String, topic: String?) {
        val guildId = _selectedGuildId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createChannel(guildId, name, topic) }
                .onSuccess { _notice.value = "Created #${it.name}" }
                .onFailure { _notice.value = "Create failed: ${it.friendly()}" }
        }
    }

    fun invite() {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.createInvite(guildId) }
                .onSuccess { _notice.value = "Invite code: $it" }
                .onFailure { _notice.value = "Invite failed: ${it.friendly()}" }
        }
    }

    fun joinGuild(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.joinGuild(code) }
                .onSuccess { guildId ->
                    _selectedGuildId.value = guildId
                    _notice.value = "Joined server"
                }
                .onFailure { _notice.value = "Join failed: ${it.friendly()}" }
        }
    }

    /** Saves cosmetic fields; null arguments keep the stored value. */
    fun saveProfile(
        displayName: String? = null,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
        accentColor: String? = null,
        customStatus: String? = null,
        theme: ProfileTheme? = null,
        badges: List<String>? = null,
    ) {
        val current = myProfile.value ?: return
        val next = current.copy(
            displayName = displayName ?: current.displayName,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            bannerUrl = bannerUrl ?: current.bannerUrl,
            accentColor = accentColor ?: current.accentColor,
            customStatus = customStatus ?: current.customStatus,
            theme = theme ?: current.theme,
            badges = badges ?: current.badges,
        )
        viewModelScope.launch {
            runCatching { repository.updateProfile(next) }
                .onSuccess { _notice.value = "Profile saved" }
                .onFailure { _notice.value = "Save failed: ${it.friendly()}" }
        }
    }

    fun uploadCosmetic(
        bucket: String,
        bytes: ByteArray,
        extension: String,
        apply: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.uploadImage(bucket, bytes, extension) }
                .onSuccess(apply)
                .onFailure { _notice.value = "Upload failed: ${it.friendly()}" }
        }
    }
}

/** Postgrest errors are long JSON blobs; surface the first line only. */
private fun Throwable.friendly(): String =
    (message ?: this::class.simpleName.orEmpty()).lineSequence().first().take(160)
