package com.chatnook.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.chatnook.app.R
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Reaction
import com.chatnook.app.domain.model.isCustomEmoji
import com.chatnook.app.ui.theme.ChatNookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Quick reactions offered on every message, Discord's default set. */
private val DEFAULT_REACTIONS = listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDD25")

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    channelId: String,
    channelName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages(channelId).collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val guildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val customEmojis by viewModel.emojis(guildId).collectAsStateWithLifecycle()
    val myId = viewModel.currentUserId
    val listState = rememberLazyListState()

    var replyTo by remember(channelId) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(channelId) { mutableStateOf<ChatMessage?>(null) }

    // Keep the newest message in view as the list grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            ChatHeader(channelName = channelName, onBack = onBack)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(messages, key = { _, it -> it.messageId }) { index, message ->
                        val previous = messages.getOrNull(index - 1)
                        MessageRow(
                            message = message,
                            isMine = message.senderId == myId,
                            grouped = previous?.senderId == message.senderId &&
                                message.createdAt - previous.createdAt < 5 * 60_000,
                            onReply = { replyTo = message },
                            onEdit = { editing = message },
                            onDelete = { viewModel.delete(message.messageId) },
                            onReact = { emoji -> viewModel.react(message.messageId, emoji) },
                            myId = myId,
                            customEmojis = customEmojis,
                        )
                    }
                }
            }

            (editing ?: replyTo)?.let { target ->
                ComposeBanner(
                    label = if (editing != null) {
                        stringResource(R.string.chat_edit_title)
                    } else {
                        stringResource(R.string.chat_replying_to, target.senderName ?: "")
                    },
                    onCancel = { editing = null; replyTo = null },
                )
            }

            MessageInput(
                draft = draft,
                isEditing = editing != null,
                onDraftChange = viewModel::updateDraft,
                onSend = {
                    val target = editing
                    if (target != null) {
                        viewModel.edit(target.messageId, draft)
                        viewModel.updateDraft("")
                        editing = null
                    } else {
                        viewModel.send(channelId, replyTo?.messageId)
                        replyTo = null
                    }
                },
            )
        }
    }
}

@Composable
private fun ChatHeader(channelName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.chat_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "# $channelName",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    grouped: Boolean,
    myId: String,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    customEmojis: List<CustomEmoji>,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .padding(vertical = if (grouped) 1.dp else 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Grouped messages keep the gutter empty so text lines up under the name.
        Box(modifier = Modifier.widthIn(min = 32.dp)) {
            if (!grouped) {
                Avatar(
                    url = message.senderAvatarUrl,
                    size = 32,
                    accent = ChatNookColors.Blurple,
                    fallbackIcon = Icons.Filled.Person,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!grouped) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.senderName ?: message.senderId.take(8),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMine) ChatNookColors.Blurple else ChatNookColors.Green,
                    )
                    Text(
                        text = message.createdAt.hhmm(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            message.replyToId?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = message.replyToContent ?: "\u21B3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.content.ifEmpty { stringResource(R.string.chat_deleted) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.content.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                message.editedAt?.let {
                    Text(
                        text = stringResource(R.string.chat_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (message.reactions.isNotEmpty()) {
                ReactionRow(reactions = message.reactions, myId = myId, onReact = onReact)
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_action_reply)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                onClick = { menuOpen = false; onReply() },
            )
            DEFAULT_REACTIONS.forEach { emoji ->
                DropdownMenuItem(
                    text = { Text(emoji) },
                    onClick = { menuOpen = false; onReact(emoji) },
                )
            }
            customEmojis.forEach { emoji ->
                DropdownMenuItem(
                    text = { Text(":${emoji.name}:") },
                    onClick = { menuOpen = false; onReact(emoji.code) },
                )
            }
            if (isMine && message.content.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_action_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_action_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun ReactionRow(reactions: List<Reaction>, myId: String, onReact: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            val mine = myId.isNotEmpty() && myId in reaction.userIds
            Surface(
                color = if (mine) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { onReact(reaction.emoji) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Uploaded emoji are stored as their URL; unicode ones render as text.
                    if (reaction.emoji.isCustomEmoji) {
                        AsyncImage(
                            model = reaction.emoji,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            text = reaction.emoji,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = reaction.userIds.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeBanner(label: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_cancel_reply),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageInput(
    draft: String,
    isEditing: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            maxLines = 4,
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
            Icon(
                imageVector = if (isEditing) {
                    Icons.Filled.Edit
                } else {
                    Icons.AutoMirrored.Filled.Send
                },
                contentDescription = stringResource(
                    if (isEditing) R.string.action_save else R.string.chat_send,
                ),
                tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun Long.hhmm(): String = clockFormat.format(Date(this))
