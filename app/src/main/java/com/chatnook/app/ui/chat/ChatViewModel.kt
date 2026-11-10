package com.chatnook.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatnook.app.data.Repositories
import com.chatnook.app.data.Supabase
import com.chatnook.app.data.media.prepareImage
import com.chatnook.app.domain.model.AuditEntry
import com.chatnook.app.domain.model.BannedUser
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.DmSummary
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.GuildRole
import com.chatnook.app.domain.model.Invite
import com.chatnook.app.domain.model.Member
import com.chatnook.app.domain.model.MemberGroup
import com.chatnook.app.domain.model.NotificationLevel
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.model.ProfileTheme
import com.chatnook.app.domain.model.TypingUser
import com.chatnook.app.domain.model.UnreadState
import com.chatnook.app.domain.model.groupMembers
import com.chatnook.app.domain.model.movedOrder
import com.chatnook.app.domain.model.sortDms
import com.chatnook.app.domain.model.unreadBadges
import com.chatnook.app.domain.repository.ChatRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository = Repositories.chat,
) : ViewModel() {

    /** Live auth id; empty until a session exists. */
    val currentUserId: String
        get() = Supabase.clientOrNull()?.auth?.currentUserOrNull()?.id.orEmpty()

    /** False until the server list has been fetched once; drives the cold-start spinner. */
    private val _guildsLoaded = MutableStateFlow(false)
    val guildsLoaded: StateFlow<Boolean> = _guildsLoaded.asStateFlow()

    val guilds: StateFlow<List<Guild>> = repository.getGuilds()
        .onEach { _guildsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedGuildId = MutableStateFlow<String?>(null)
    val selectedGuildId: StateFlow<String?> = _selectedGuildId.asStateFlow()

    /** True once the selected guild's channels have arrived, so "empty" means empty. */
    private val _channelsLoaded = MutableStateFlow(false)
    val channelsLoaded: StateFlow<Boolean> = _channelsLoaded.asStateFlow()

    /** Channels of the selected guild; empty when no guild is picked yet. */
    val channels: StateFlow<List<ChatChannel>> = _selectedGuildId
        .filterNotNull()
        .onEach { _channelsLoaded.value = false }
        .flatMapLatest { repository.getChannels(it) }
        .onEach { _channelsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myProfile: StateFlow<Profile?> = repository.getMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Channel the chat screen is showing; its badge stays hidden while it is open. */
    private val _openChannelId = MutableStateFlow<String?>(null)

    /** Unread badges for every reachable channel, minus the channel being read. */
    val unread: StateFlow<Map<String, UnreadState>> = myProfile
        .map { it?.username }
        .distinctUntilChanged()
        .flatMapLatest { repository.getUnreadCounts(it) }
        .combine(_openChannelId) { counts, open -> unreadBadges(counts, open) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** True while the rail's home button is selected, which shows the DM list. */
    private val _home = MutableStateFlow(false)
    val home: StateFlow<Boolean> = _home.asStateFlow()

    val dms: StateFlow<List<DmSummary>> = repository.getDms()
        .map(::sortDms)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Members of the selected guild, grouped the way the list renders them. */
    val memberGroups: StateFlow<List<MemberGroup>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getMembers(it) }
        .map(::groupMembers)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Members connected to the selected guild right now. */
    val onlineUsers: StateFlow<Set<String>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getOnlineUsers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Roles of the selected guild, highest first. */
    val roles: StateFlow<List<GuildRole>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getRoles(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The caller's permission bits in the selected guild. Drives which actions the UI
     * offers; every write is still checked by RLS and the RPCs.
     */
    val permissions: StateFlow<Long> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getMyPermissions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // One timeline per channel, created the first time it is opened. It keeps the live
    // window, loaded older pages and optimistic rows together.
    private val timelines = mutableMapOf<String, ChannelTimeline>()

    private fun timeline(channelId: String): ChannelTimeline =
        timelines.getOrPut(channelId) {
            ChannelTimeline(
                channelId = channelId,
                repository = repository,
                scope = viewModelScope,
                myProfile = { myProfile.value },
                myUserId = { currentUserId },
            )
        }

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
        _home.value = false
        _selectedGuildId.value = guildId
    }

    fun openHome() {
        _home.value = true
    }

    /** Finds or creates the DM with [userId]; null when the server refuses. */
    suspend fun openDm(userId: String): String? =
        runCatching { repository.openDm(userId) }
            .onFailure { _notice.value = "Could not open the DM: ${it.friendly()}" }
            .getOrNull()

    fun messages(channelId: String): StateFlow<List<ChatMessage>> = timeline(channelId).messages

    fun hasMore(channelId: String): StateFlow<Boolean> = timeline(channelId).hasMore

    fun isLoadingOlder(channelId: String): StateFlow<Boolean> = timeline(channelId).loadingOlder

    /** Returns how many rows were prepended, so the list can keep its scroll anchor. */
    suspend fun loadOlder(channelId: String): Int = timeline(channelId).loadOlder()

    fun retry(messageId: String, channelId: String) {
        timeline(channelId).retry(messageId)
    }

    fun typing(channelId: String): StateFlow<List<TypingUser>> = timeline(channelId).typing

    fun notifyTyping(channelId: String) {
        timeline(channelId).notifyTyping()
    }

    /** Called whenever the newest message is on screen; a no-op for an unread id. */
    fun markRead(channelId: String, message: ChatMessage?) {
        timeline(channelId).markRead(message)
    }

    fun openChannel(channelId: String) {
        _openChannelId.value = channelId
    }

    fun closeChannel(channelId: String) {
        if (_openChannelId.value == channelId) _openChannelId.value = null
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
        // Cleared at once: the bubble is already on screen and carries the text for retry.
        _draft.value = ""
        timeline(channelId).send(content, replyToId)
    }

    /**
     * Downscales [uri] on the IO dispatcher, then sends it with whatever is in the draft.
     * The bubble appears immediately with the local file so the picker feels instant.
     */
    fun sendImage(channelId: String, context: Context, uri: Uri) {
        val caption = _draft.value.trim()
        viewModelScope.launch {
            val image = prepareImage(context, uri)
            if (image == null) {
                _notice.value = "Could not read that picture"
                return@launch
            }
            _draft.value = ""
            timeline(channelId).send(caption, null, image, uri.toString())
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

    /** Invite codes of the selected guild, newest first. */
    /** Banned accounts, for the moderation screen. */
    val bans: StateFlow<List<BannedUser>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getBans(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moderation history, newest first. */
    val auditLog: StateFlow<List<AuditEntry>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getAuditLog(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val invites: StateFlow<List<Invite>> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getInvites(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [expiresInDays] null means the code never expires. */
    fun createInvite(expiresInDays: Int?) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            val expiresAt = expiresInDays?.let {
                kotlin.time.Instant
                    .fromEpochMilliseconds(System.currentTimeMillis() + it * 86_400_000L)
                    .toString()
            }
            runCatching { repository.createInvite(guildId, expiresAt) }
                .onSuccess { _notice.value = "Invite code: $it" }
                .onFailure { _notice.value = "Invite failed: ${it.friendly()}" }
        }
    }

    fun revokeInvite(code: String) {
        viewModelScope.launch {
            runCatching { repository.revokeInvite(code) }
                .onSuccess { _notice.value = "Revoked $code" }
                .onFailure { _notice.value = "Revoke failed: ${it.friendly()}" }
        }
    }

    fun updateGuild(name: String? = null, iconUrl: String? = null) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.updateGuild(guildId, name, iconUrl) }
                .onSuccess { if (name != null) _notice.value = "Server renamed" }
                .onFailure { _notice.value = "Save failed: ${it.friendly()}" }
        }
    }

    fun deleteGuild() {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.deleteGuild(guildId) }
                .onSuccess {
                    _selectedGuildId.value = null
                    _notice.value = "Server deleted"
                }
                .onFailure { _notice.value = "Delete failed: ${it.friendly()}" }
        }
    }

    fun updateChannel(channelId: String, name: String?, topic: String?) {
        viewModelScope.launch {
            runCatching { repository.updateChannel(channelId, name, topic) }
                .onSuccess { _notice.value = "Channel saved" }
                .onFailure { _notice.value = "Save failed: ${it.friendly()}" }
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteChannel(channelId) }
                .onSuccess { _notice.value = "Channel deleted" }
                .onFailure { _notice.value = "Delete failed: ${it.friendly()}" }
        }
    }

    fun moveChannel(channelId: String, delta: Int) {
        val guildId = _selectedGuildId.value ?: return
        val order = movedOrder(channels.value.map { it.channelId }, channelId, delta) ?: return
        viewModelScope.launch {
            runCatching { repository.reorderChannels(guildId, order) }
                .onFailure { _notice.value = "Reorder failed: ${it.friendly()}" }
        }
    }

    fun uploadGuildIcon(bytes: ByteArray, extension: String) {
        uploadCosmetic("avatars", bytes, extension) { url -> updateGuild(iconUrl = url) }
    }

    /** Mutual servers per user id, fetched once when their card is opened. */
    private val _mutualGuilds = MutableStateFlow<Map<String, List<Guild>>>(emptyMap())
    val mutualGuilds: StateFlow<Map<String, List<Guild>>> = _mutualGuilds.asStateFlow()

    fun loadMutualGuilds(userId: String) {
        if (_mutualGuilds.value.containsKey(userId)) return
        viewModelScope.launch {
            runCatching { repository.getMutualGuilds(userId) }
                .onSuccess { list -> _mutualGuilds.update { it + (userId to list) } }
        }
    }

    /** Whether the caller muted the selected server's notifications. */
    val guildMuted: StateFlow<Boolean> = _selectedGuildId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.getGuildMuted(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Per-channel notification levels; absent means every message. */
    val channelLevels: StateFlow<Map<String, NotificationLevel>> = repository.getChannelLevels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setGuildMuted(muted: Boolean) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.setGuildMuted(guildId, muted) }
                .onFailure { _notice.value = "Could not save: ${it.friendly()}" }
        }
    }

    fun setChannelLevel(channelId: String, level: NotificationLevel) {
        viewModelScope.launch {
            runCatching { repository.setChannelLevel(channelId, level) }
                .onFailure { _notice.value = "Could not save: ${it.friendly()}" }
        }
    }

    fun createRole(name: String, color: Int?, permissions: Long) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.createRole(guildId, name, color, permissions) }
                .onSuccess { _notice.value = "Role created" }
                .onFailure { _notice.value = "Create failed: ${it.friendly()}" }
        }
    }

    fun updateRole(role: GuildRole, name: String, color: Int?, permissions: Long) {
        viewModelScope.launch {
            runCatching { repository.updateRole(role.id, name, color, permissions) }
                .onSuccess { _notice.value = "Role saved" }
                .onFailure { _notice.value = "Save failed: ${it.friendly()}" }
        }
    }

    fun deleteRole(role: GuildRole) {
        viewModelScope.launch {
            runCatching { repository.deleteRole(role.id) }
                .onSuccess { _notice.value = "Role deleted" }
                .onFailure { _notice.value = "Delete failed: ${it.friendly()}" }
        }
    }

    fun setMemberRoles(member: Member, roleIds: List<String>) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.setMemberRoles(guildId, member.userId, roleIds) }
                .onSuccess { _notice.value = "Roles updated for ${member.label}" }
                .onFailure { _notice.value = "Update failed: ${it.friendly()}" }
        }
    }

    fun kick(member: Member) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.kickMember(guildId, member.userId) }
                .onSuccess { _notice.value = "${member.label} was kicked" }
                .onFailure { _notice.value = "Kick failed: ${it.friendly()}" }
        }
    }

    fun ban(member: Member, reason: String?) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.banMember(guildId, member.userId, reason) }
                .onSuccess { _notice.value = "${member.label} was banned" }
                .onFailure { _notice.value = "Ban failed: ${it.friendly()}" }
        }
    }

    fun unban(user: BannedUser) {
        val guildId = _selectedGuildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.unbanMember(guildId, user.userId) }
                .onSuccess { _notice.value = "${user.name} was unbanned" }
                .onFailure { _notice.value = "Unban failed: ${it.friendly()}" }
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
