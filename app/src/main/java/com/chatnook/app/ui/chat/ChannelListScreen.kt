package com.chatnook.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.R
import com.chatnook.app.domain.model.ChatChannel
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.NotificationLevel
import com.chatnook.app.domain.model.PermissionBits
import com.chatnook.app.domain.model.UnreadState
import com.chatnook.app.domain.model.gradientStops
import com.chatnook.app.domain.model.has
import com.chatnook.app.ui.theme.ChatNookColors

@Composable
fun ChannelListScreen(
    viewModel: ChatViewModel,
    onOpenChannel: (channelId: String, channelName: String) -> Unit,
    onOpenDm: (channelId: String, channelName: String) -> Unit,
    onOpenMembers: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val guilds by viewModel.guilds.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedGuildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val home by viewModel.home.collectAsStateWithLifecycle()
    val dms by viewModel.dms.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val channelLevels by viewModel.channelLevels.collectAsStateWithLifecycle()
    val guildMuted by viewModel.guildMuted.collectAsStateWithLifecycle()
    val guildsLoaded by viewModel.guildsLoaded.collectAsStateWithLifecycle()
    val channelsLoaded by viewModel.channelsLoaded.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var editingChannel by remember { mutableStateOf<ChatChannel?>(null) }
    var deletingChannel by remember { mutableStateOf<ChatChannel?>(null) }
    var channelMenu by remember { mutableStateOf<ChatChannel?>(null) }
    val selectedGuild = guilds.firstOrNull { it.guildId == selectedGuildId }
    // RLS is the real gate; these only hide actions that would fail.
    val canManageChannels = permissions has PermissionBits.MANAGE_CHANNELS
    val canManageGuild = permissions has PermissionBits.MANAGE_GUILD
    val canManageRoles = permissions has PermissionBits.MANAGE_ROLES
    val canInvite = permissions has PermissionBits.CREATE_INVITE
    // Cold start and guild switches: the lists are still empty because nothing has
    // arrived yet, not because the server has nothing in it.
    val loading = !guildsLoaded || (selectedGuildId != null && !channelsLoaded)
    val context = LocalContext.current

    // Two-step emoji upload: pick the file, then name it once the URL is known.
    var newEmojiUrl by remember { mutableStateOf<String?>(null) }
    val pickEmoji = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val bytes = uri?.let(context::readBytes) ?: return@rememberLauncherForActivityResult
        viewModel.uploadCosmetic(
            bucket = "emojis",
            bytes = bytes,
            extension = context.extensionOf(uri),
        ) { newEmojiUrl = it }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            notice?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = {
                        TextButton(onClick = viewModel::dismissNotice) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                ) { Text(message) }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { testTagsAsResourceId = true },
        ) {
            GuildRail(
                guilds = guilds,
                selectedGuildId = if (home) null else selectedGuildId,
                homeSelected = home,
                onHome = viewModel::openHome,
                onSelect = viewModel::selectGuild,
                onAddGuild = { dialog = HomeDialog.CreateGuild },
                onJoin = { dialog = HomeDialog.JoinGuild },
            )

            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                Header(
                    title = if (home) {
                        stringResource(R.string.home_title)
                    } else {
                        selectedGuild?.name ?: stringResource(R.string.app_name)
                    },
                    subtitle = if (home) {
                        pluralStringResource(R.plurals.home_conversations, dms.size, dms.size)
                    } else {
                        selectedGuild?.let { stringResource(R.string.channels_subtitle) }
                            ?: if (loading) "" else stringResource(R.string.channels_empty)
                    },
                    onOpenProfile = onOpenProfile,
                    avatarUrl = profile?.avatarUrl,
                    accent = profile?.gradientStops?.firstOrNull()
                        ?.let { androidx.compose.ui.graphics.Color(it.toInt()) }
                        ?: ChatNookColors.Blurple,
                    muted = !home && guildMuted,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (!home && selectedGuild != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canManageChannels) {
                            TextButton(onClick = { dialog = HomeDialog.CreateChannel }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(stringResource(R.string.channels_add))
                            }
                        }
                        if (canInvite) {
                            IconButton(onClick = viewModel::invite) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = stringResource(R.string.channels_invite),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (canManageGuild) {
                            IconButton(onClick = { pickEmoji.launch("image/*") }) {
                                Icon(
                                    Icons.Filled.EmojiEmotions,
                                    contentDescription = stringResource(R.string.channels_add_emoji),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = onOpenMembers) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(R.string.members_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (canManageGuild || canManageChannels || canManageRoles) {
                            IconButton(onClick = onOpenServerSettings) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.server_settings_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (home) {
                    DmList(dms = dms, unread = unread, onOpen = onOpenDm)
                } else if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.channels_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(channels, key = { it.channelId }) { channel ->
                            Column(modifier = Modifier.animateItem()) {
                                ChannelRow(
                                    channel = channel,
                                    unread = unread[channel.channelId] ?: UnreadState.None,
                                    quiet = channelLevels[channel.channelId] == NotificationLevel.NONE,
                                    onClick = { onOpenChannel(channel.channelId, channel.name) },
                                    onLongClick = { channelMenu = channel },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        HomeDialog.CreateGuild -> InputDialog(
            title = stringResource(R.string.channels_new_server),
            label = stringResource(R.string.channels_server_name),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.createGuild(it); dialog = null },
        )

        HomeDialog.JoinGuild -> InputDialog(
            title = stringResource(R.string.channels_join_server),
            label = stringResource(R.string.channels_invite_code),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.joinGuild(it); dialog = null },
        )

        HomeDialog.CreateChannel -> InputDialog(
            title = stringResource(R.string.channels_new_channel),
            label = stringResource(R.string.channels_channel_name),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.createChannel(it, null); dialog = null },
        )

        null -> Unit
    }

    editingChannel?.let { channel ->
        val index = channels.indexOfFirst { it.channelId == channel.channelId }
        ChannelSettingsDialog(
            channel = channel,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < channels.lastIndex,
            onMove = { delta -> viewModel.moveChannel(channel.channelId, delta) },
            onDelete = { deletingChannel = channel; editingChannel = null },
            onSave = { name, topic ->
                viewModel.updateChannel(channel.channelId, name, topic)
                editingChannel = null
            },
            onDismiss = { editingChannel = null },
        )
    }

    channelMenu?.let { channel ->
        ChannelMenuSheet(
            channel = channel,
            level = channelLevels[channel.channelId] ?: NotificationLevel.ALL,
            canManage = canManageChannels,
            onLevel = { level ->
                viewModel.setChannelLevel(channel.channelId, level)
                channelMenu = null
            },
            onEdit = {
                editingChannel = channel
                channelMenu = null
            },
            onDelete = {
                deletingChannel = channel
                channelMenu = null
            },
            onDismiss = { channelMenu = null },
        )
    }

    deletingChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { deletingChannel = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.channel_delete_title)) },
            text = { Text(stringResource(R.string.channel_delete_body, channel.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChannel(channel.channelId)
                        deletingChannel = null
                    },
                    modifier = Modifier.testTag("channel_delete_confirm"),
                ) {
                    Text(stringResource(R.string.channel_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingChannel = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // The upload finishes before this dialog appears, so the name and URL match.
    val emojiUrl = newEmojiUrl
    if (emojiUrl != null) {
        InputDialog(
            title = stringResource(R.string.channels_add_emoji),
            label = stringResource(R.string.channels_emoji_name),
            onDismiss = { newEmojiUrl = null },
            onConfirm = { name ->
                selectedGuildId?.let { viewModel.addEmoji(it, name, emojiUrl) }
                newEmojiUrl = null
            },
        )
    }
}

private sealed interface HomeDialog {
    data object CreateGuild : HomeDialog
    data object JoinGuild : HomeDialog
    data object CreateChannel : HomeDialog
}

/**
 * Narrow vertical rail of servers, Discord-style: one rounded square per guild plus
 * the add/join actions pinned at the bottom.
 */
@Composable
private fun GuildRail(
    guilds: List<Guild>,
    selectedGuildId: String?,
    homeSelected: Boolean,
    onHome: () -> Unit,
    onSelect: (String) -> Unit,
    onAddGuild: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            // Fixed-width rail: fillMaxSize here would starve the channel column of width.
            .fillMaxHeight()
            .width(64.dp)
            .background(ChatNookColors.DarkBase)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Discord's home button: the direct message list, above the servers.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(if (homeSelected) RoundedCornerShape(14.dp) else CircleShape)
                .background(
                    if (homeSelected) ChatNookColors.Blurple else ChatNookColors.DarkElevated,
                )
                .clickable(onClick = onHome)
                .testTag("home"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = stringResource(R.string.home_title),
                tint = ChatNookColors.TextPrimary,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = ChatNookColors.Outline,
        )

        guilds.forEach { guild ->
            val selected = guild.guildId == selectedGuildId
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(if (selected) RoundedCornerShape(14.dp) else CircleShape)
                    .background(
                        if (selected) ChatNookColors.Blurple else ChatNookColors.DarkElevated,
                    )
                    .clickable { onSelect(guild.guildId) }
                    .testTag("guild_${guild.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = guild.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatNookColors.TextPrimary,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ChatNookColors.DarkElevated)
                .clickable(onClick = onAddGuild),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.channels_new_server),
                tint = ChatNookColors.Green,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ChatNookColors.DarkElevated)
                .clickable(onClick = onJoin),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.channels_join_server),
                tint = ChatNookColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    onOpenProfile: () -> Unit,
    avatarUrl: String?,
    accent: androidx.compose.ui.graphics.Color,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (muted) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = stringResource(R.string.notifications_muted),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(18.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenProfile) {
            Avatar(
                url = avatarUrl,
                size = 32,
                accent = accent,
                fallbackIcon = Icons.Filled.Person,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: ChatChannel,
    unread: UnreadState,
    quiet: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { open ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open()
                    }
                },
            )
            .testTag("channel_${channel.name}")
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread.visible) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            channel.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (quiet) {
            Icon(
                imageVector = Icons.Filled.NotificationsOff,
                contentDescription = stringResource(R.string.notifications_muted),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        UnreadBadge(unread, tag = channel.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelMenuSheet(
    channel: ChatChannel,
    level: NotificationLevel,
    canManage: Boolean,
    onLevel: (NotificationLevel) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.notifications_channel_sheet, channel.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            NotificationLevel.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLevel(option) }
                        .testTag("notif_${option.wire}")
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = option == level, onClick = null)
                    Column {
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(option.hintRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (canManage) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().testTag("channel_edit"),
                ) { Text(stringResource(R.string.channel_edit)) }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.channel_delete), color = ChatNookColors.Red) }
            }
        }
    }
}

private fun NotificationLevel.labelRes(): Int = when (this) {
    NotificationLevel.ALL -> R.string.notifications_level_all
    NotificationLevel.MENTIONS -> R.string.notifications_level_mentions
    NotificationLevel.NONE -> R.string.notifications_level_none
}

private fun NotificationLevel.hintRes(): Int = when (this) {
    NotificationLevel.ALL -> R.string.notifications_level_all_hint
    NotificationLevel.MENTIONS -> R.string.notifications_level_mentions_hint
    NotificationLevel.NONE -> R.string.notifications_level_none_hint
}

/** Discord's two badges: a red @count for mentions, a muted count for the rest. */
@Composable
internal fun UnreadBadge(unread: UnreadState, tag: String) {
    if (!unread.visible) return
    val mentions = unread.mentions > 0
    Text(
        text = if (mentions) "@${unread.mentions.coerceAtMost(99)}" else unread.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (mentions) ChatNookColors.Red else ChatNookColors.DarkHover)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("unread_$tag"),
    )
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Dialogs are their own semantics window, so the root flag has to be repeated here.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.testTag("dialog_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("dialog_confirm"),
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Rename, retopic, reorder and delete one channel. */
@Composable
private fun ChannelSettingsDialog(
    channel: ChatChannel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onSave: (name: String, topic: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(channel.name) }
    var topic by remember { mutableStateOf(channel.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.channel_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.channels_channel_name)) },
                    singleLine = true,
                    modifier = Modifier.testTag("channel_name"),
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.channel_topic)) },
                    singleLine = true,
                    modifier = Modifier.testTag("channel_topic"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onMove(-1) },
                        enabled = canMoveUp,
                        modifier = Modifier.testTag("channel_up"),
                    ) { Text(stringResource(R.string.channel_move_up)) }
                    TextButton(
                        onClick = { onMove(1) },
                        enabled = canMoveDown,
                        modifier = Modifier.testTag("channel_down"),
                    ) { Text(stringResource(R.string.channel_move_down)) }
                }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("channel_delete")) {
                    Text(
                        text = stringResource(R.string.channel_delete),
                        color = ChatNookColors.Red,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, topic) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("channel_save"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
