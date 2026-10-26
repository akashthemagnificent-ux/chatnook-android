package com.chatnook.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
import com.chatnook.app.domain.model.gradientStops
import com.chatnook.app.ui.theme.ChatNookColors

@Composable
fun ChannelListScreen(
    viewModel: ChatViewModel,
    onOpenChannel: (channelId: String, channelName: String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val guilds by viewModel.guilds.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedGuildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val selectedGuild = guilds.firstOrNull { it.guildId == selectedGuildId }
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
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            GuildRail(
                guilds = guilds,
                selectedGuildId = selectedGuildId,
                onSelect = viewModel::selectGuild,
                onAddGuild = { dialog = HomeDialog.CreateGuild },
                onJoin = { dialog = HomeDialog.JoinGuild },
            )

            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                Header(
                    title = selectedGuild?.name ?: stringResource(R.string.app_name),
                    subtitle = selectedGuild?.let { stringResource(R.string.channels_subtitle) }
                        ?: stringResource(R.string.channels_empty),
                    onOpenProfile = onOpenProfile,
                    avatarUrl = profile?.avatarUrl,
                    accent = profile?.gradientStops?.firstOrNull()
                        ?.let { androidx.compose.ui.graphics.Color(it.toInt()) }
                        ?: ChatNookColors.Blurple,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (selectedGuild != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { dialog = HomeDialog.CreateChannel }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.channels_add))
                        }
                        IconButton(onClick = viewModel::invite) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.channels_invite),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pickEmoji.launch("image/*") }) {
                            Icon(
                                Icons.Filled.EmojiEmotions,
                                contentDescription = stringResource(R.string.channels_add_emoji),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (channels.isEmpty()) {
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
                            ChannelRow(
                                channel = channel,
                                onClick = { onOpenChannel(channel.channelId, channel.name) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
    onSelect: (String) -> Unit,
    onAddGuild: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatNookColors.DarkBase)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        guilds.forEach { guild ->
            val selected = guild.guildId == selectedGuildId
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(if (selected) RoundedCornerShape(14.dp) else CircleShape)
                    .background(
                        if (selected) ChatNookColors.Blurple else ChatNookColors.DarkElevated,
                    )
                    .clickable { onSelect(guild.guildId) },
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun ChannelRow(channel: ChatChannel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    }
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
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
