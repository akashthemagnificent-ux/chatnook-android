package com.chatnook.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import coil3.compose.AsyncImage
import com.chatnook.app.R
import com.chatnook.app.domain.model.ChatMessage
import com.chatnook.app.domain.model.CustomEmoji
import com.chatnook.app.domain.model.Reaction
import com.chatnook.app.domain.model.TypingUser
import com.chatnook.app.domain.model.isCustomEmoji
import com.chatnook.app.domain.model.mentionsUser
import com.chatnook.app.ui.theme.ChatNookColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Quick reactions offered on every message, Discord's default set. */
private val DEFAULT_REACTIONS = listOf("\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDD25")

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    channelId: String,
    channelName: String,
    isDm: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages(channelId).collectAsStateWithLifecycle()
    val loadingOlder by viewModel.isLoadingOlder(channelId).collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val guildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val customEmojis by viewModel.emojis(guildId).collectAsStateWithLifecycle()
    val typing by viewModel.typing(channelId).collectAsStateWithLifecycle()
    val online by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val myUsername = viewModel.myProfile.collectAsStateWithLifecycle().value?.username
    val myId = viewModel.currentUserId
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val zone = remember { TimeZone.getDefault() }
    val context = LocalContext.current

    val items = remember(messages, zone) { buildTimeline(messages, zone) }
    val latestItems by rememberUpdatedState(items)
    val messageCount = messages.size

    var replyTo by remember(channelId) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(channelId) { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember(channelId) { mutableStateOf<ChatMessage?>(null) }

    var initialScrollDone by remember(channelId) { mutableStateOf(false) }
    var newBelow by remember(channelId) { mutableIntStateOf(0) }
    var seenCount by remember(channelId) { mutableIntStateOf(0) }
    // Set right before prepending a page; the list scrolls back to it once the rows land.
    var anchorKey by remember(channelId) { mutableStateOf<String?>(null) }
    var anchorOffset by remember(channelId) { mutableIntStateOf(0) }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1
        }
    }

    // The list hides this channel's badge while it is open; the server gets the write
    // once the newest row is actually on screen.
    DisposableEffect(channelId) {
        viewModel.openChannel(channelId)
        onDispose { viewModel.closeChannel(channelId) }
    }

    // Reaching the newest row by hand clears the "new messages" counter.
    LaunchedEffect(atBottom, messageCount) {
        if (atBottom) {
            newBelow = 0
            seenCount = messageCount
            viewModel.markRead(channelId, messages.lastOrNull())
        }
    }

    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            listState.scrollToItem(items.lastIndex)
            initialScrollDone = true
            seenCount = messageCount
            return@LaunchedEffect
        }
        val key = anchorKey
        if (key != null) {
            val index = items.indexOfFirst { it.key == key }
            if (index >= 0) listState.scrollToItem(index, anchorOffset)
            anchorKey = null
            return@LaunchedEffect
        }
        if (atBottom) {
            listState.animateScrollToItem(items.lastIndex)
            seenCount = messageCount
            newBelow = 0
        } else {
            newBelow = (messageCount - seenCount).coerceAtLeast(0)
        }
    }

    // Paging: the first visible row is near the top and older pages remain.
    LaunchedEffect(channelId) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: Int.MAX_VALUE
        }.distinctUntilChanged().collect { firstVisible ->
            if (firstVisible > 2) return@collect
            val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@collect
            val key = latestItems.getOrNull(anchor.index)?.key ?: return@collect
            anchorKey = key
            anchorOffset = anchor.offset
            val added = viewModel.loadOlder(channelId)
            if (added == 0) anchorKey = null
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .semantics { testTagsAsResourceId = true },
        ) {
            ChatHeader(
                channelName = channelName,
                isDm = isDm,
                typing = typing,
                online = online.size,
                onBack = onBack,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.chat_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (loadingOlder) {
                            item(key = "loading-older") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                            }
                        }
                        items(items, key = { it.key }) { item ->
                            when (item) {
                                is TimelineItem.Day -> DaySeparator(item.dayKey, zone)
                                is TimelineItem.Msg -> MessageRow(
                                    message = item.message,
                                    isMine = item.message.senderId == myId,
                                    mentioned = item.message.senderId != myId &&
                                        mentionsUser(item.message.content, myUsername),
                                    grouped = item.grouped,
                                    onReply = { replyTo = item.message },
                                    onEdit = { editing = item.message },
                                    onDelete = { deleting = item.message },
                                    onReact = { emoji -> viewModel.react(item.message.messageId, emoji) },
                                    onRetry = { viewModel.retry(item.message.messageId, channelId) },
                                    myId = myId,
                                    customEmojis = customEmojis,
                                )
                            }
                        }
                    }
                }

                if (newBelow > 0) {
                    Surface(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(latestItems.lastIndex) }
                            newBelow = 0
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .testTag("chat_jump"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.chat_new_messages, newBelow), style = MaterialTheme.typography.labelLarge)
                        }
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
                channelName = channelName,
                isDm = isDm,
                isEditing = editing != null,
                customEmojis = customEmojis,
                onDraftChange = { text ->
                    viewModel.updateDraft(text)
                    viewModel.notifyTyping(channelId)
                },
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
                onPickImage = { uri -> viewModel.sendImage(channelId, context, uri) },
            )
        }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.messageId)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.chat_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatHeader(
    channelName: String,
    isDm: Boolean,
    typing: List<TypingUser>,
    online: Int,
    onBack: () -> Unit,
) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isDm) "@ $channelName" else "# $channelName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typingLabel(typing) ?: stringResource(R.string.chat_online, online),
                style = MaterialTheme.typography.labelSmall,
                color = if (typing.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    ChatNookColors.Green
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("chat_subtitle"),
            )
        }
    }
}

/** Discord's wording: one name, two names, then "several". */
@Composable
private fun typingLabel(typing: List<TypingUser>): String? = when (typing.size) {
    0 -> null
    1 -> stringResource(R.string.chat_typing_one, typing[0].name)
    2 -> stringResource(R.string.chat_typing_two, typing[0].name, typing[1].name)
    else -> stringResource(R.string.chat_typing_many)
}

@Composable
private fun DaySeparator(dayKey: String, zone: TimeZone) {
    val now = remember { System.currentTimeMillis() }
    val millis = remember(dayKey, zone) { parseDayKey(dayKey, zone) }
    val label = when (dayLabel(millis, now, zone)) {
        DayLabel.Today -> stringResource(R.string.chat_today)
        DayLabel.Yesterday -> stringResource(R.string.chat_yesterday)
        DayLabel.Date -> formatDay(millis, zone)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Inverse of [dayKey] for rendering; noon avoids DST edges. */
private fun parseDayKey(key: String, zone: TimeZone): Long {
    val parts = key.split('-')
    return Calendar.getInstance(zone).apply {
        clear()
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
    }.timeInMillis
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    mentioned: Boolean,
    grouped: Boolean,
    myId: String,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onRetry: () -> Unit,
    customEmojis: List<CustomEmoji>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pending = message.sendState != ChatMessage.SendState.Sent
    val emojiMap = remember(customEmojis) { customEmojis.associate { it.name to it.url } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (mentioned) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .alpha(if (message.sendState == ChatMessage.SendState.Pending) 0.6f else 1f)
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
                        text = clockLabel(message.createdAt),
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
                when {
                    message.isDeleted -> Text(
                        text = stringResource(R.string.chat_deleted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    message.content.isEmpty() -> Spacer(Modifier.weight(1f))
                    else -> MarkdownText(
                        text = message.content,
                        modifier = Modifier.weight(1f),
                        emojis = emojiMap,
                    )
                }
                message.editedAt?.let {
                    Text(
                        text = stringResource(R.string.chat_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // One image per message: the composer sends one, and the row shows one.
            (message.attachments.firstOrNull()?.url ?: message.localImageUri)?.let { source ->
                var zoomed by remember(message.messageId) { mutableStateOf(false) }
                val ratio = message.attachments.firstOrNull()?.aspectRatio ?: 0.75f
                AsyncImage(
                    model = source,
                    contentDescription = message.attachments.firstOrNull()?.filename
                        ?: stringResource(R.string.chat_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f / ratio)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { zoomed = true }
                        .testTag("chat_image"),
                )
                if (zoomed) {
                    Dialog(onDismissRequest = { zoomed = false }, properties = fullscreenDialog()) {
                        Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoomed = false }) {
                            AsyncImage(
                                model = source,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                            )
                        }
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                ReactionRow(reactions = message.reactions, myId = myId, onReact = onReact)
            }

            if (message.sendState == ChatMessage.SendState.Failed) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = stringResource(R.string.chat_failed_retry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
            if (isMine && message.content.isNotEmpty() && !pending) {
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
    channelName: String,
    isDm: Boolean,
    isEditing: Boolean,
    customEmojis: List<CustomEmoji>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPickImage) }

    // The field keeps the cursor; the draft in the view model is only the text.
    var field by remember { mutableStateOf(TextFieldValue(draft)) }
    LaunchedEffect(draft) {
        if (field.text != draft) field = TextFieldValue(draft, TextRange(draft.length))
    }
    var pickingEmoji by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isEditing) {
            IconButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.testTag("chat_attach"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.chat_attach_image),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onDraftChange(it.text)
            },
            modifier = Modifier.weight(1f).testTag("chat_input"),
            placeholder = {
                Text(
                    stringResource(
                        if (isDm) R.string.chat_input_hint_dm else R.string.chat_input_hint,
                        channelName,
                    ),
                )
            },
            maxLines = 4,
            trailingIcon = {
                IconButton(
                    onClick = { pickingEmoji = true },
                    modifier = Modifier.testTag("chat_emoji"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = stringResource(R.string.chat_emoji_picker),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        IconButton(
            onClick = onSend,
            enabled = draft.isNotBlank(),
            modifier = Modifier.testTag("chat_send"),
        ) {
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

    if (pickingEmoji) {
        EmojiPickerSheet(
            customEmojis = customEmojis,
            onPick = { insert ->
                val start = field.selection.start.coerceIn(0, field.text.length)
                val end = field.selection.end.coerceIn(start, field.text.length)
                val text = field.text.replaceRange(start, end, insert)
                field = TextFieldValue(text, TextRange(start + insert.length))
                onDraftChange(text)
                pickingEmoji = false
            },
            onDismiss = { pickingEmoji = false },
        )
    }
}

/** Unicode emoji for the composer sheet; a full picker is not worth the size. */
private val EMOJI_GRID = listOf(
    "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE05", "\uD83D\uDE0D", "\uD83D\uDE0E", "\uD83E\uDD14",
    "\uD83D\uDE22", "\uD83D\uDE21", "\uD83D\uDE4C", "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F",
    "\uD83D\uDE4F", "\uD83D\uDCAA", "\u270C\uFE0F", "\uD83E\uDD1D", "\u2764\uFE0F", "\uD83D\uDC94",
    "\uD83D\uDD25", "\u2728", "\uD83C\uDF89", "\uD83D\uDC80", "\uD83D\uDC40", "\uD83E\uDD37",
    "\uD83D\uDC4C", "\uD83E\uDD73", "\uD83D\uDE0A", "\uD83D\uDE10", "\uD83D\uDE2D", "\uD83D\uDE31",
    "\uD83E\uDD2F", "\uD83D\uDC51", "\uD83C\uDF55", "\u2615", "\uD83C\uDFAE", "\uD83D\uDE80",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(
    customEmojis: List<CustomEmoji>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(12.dp),
        ) {
            gridItems(EMOJI_GRID) { emoji ->
                EmojiCell(onClick = { onPick(emoji) }) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            }
            gridItems(customEmojis, key = { it.name }) { emoji ->
                EmojiCell(onClick = { onPick(":${emoji.name}:") }) {
                    AsyncImage(
                        model = emoji.url,
                        contentDescription = ":${emoji.name}:",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Edge-to-edge dialog: an image viewer should not sit inside a padded card. */
private fun fullscreenDialog() = DialogProperties(usePlatformDefaultWidth = false)

/** 12h or 24h follows the system setting, like every other chat app. */
@Composable
private fun clockLabel(epochMillis: Long): String {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    return format.format(Date(epochMillis))
}
