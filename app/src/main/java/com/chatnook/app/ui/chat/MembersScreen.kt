package com.chatnook.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.chatnook.app.R
import com.chatnook.app.domain.model.Guild
import com.chatnook.app.domain.model.GuildRole
import com.chatnook.app.domain.model.HexColor
import com.chatnook.app.domain.model.Member
import com.chatnook.app.domain.model.MemberGroup
import com.chatnook.app.domain.model.PermissionBits
import com.chatnook.app.domain.model.has
import com.chatnook.app.ui.theme.ChatNookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Guild roster: owner, then roles by position, then everyone else. */
@Composable
fun MembersScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onMessage: (channelId: String, name: String) -> Unit,
) {
    val groups by viewModel.memberGroups.collectAsStateWithLifecycle()
    val online by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val mutual by viewModel.mutualGuilds.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val me by viewModel.myProfile.collectAsStateWithLifecycle()
    val canManageRoles = permissions has PermissionBits.MANAGE_ROLES
    val canKick = permissions has PermissionBits.KICK_MEMBERS
    var selected by remember { mutableStateOf<Member?>(null) }
    LaunchedEffect(selected?.userId) {
        selected?.let { viewModel.loadMutualGuilds(it.userId) }
    }
    val scope = rememberCoroutineScope()
    val total = groups.sumOf { it.members.size }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { testTagsAsResourceId = true },
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
                        text = stringResource(R.string.members_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.members_count, total, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.members_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Roster(groups = groups, online = online, onSelect = { selected = it })
            }
        }
    }

    selected?.let { member ->
        ProfileSheet(
            member = member,
            online = member.userId in online,
            mutual = mutual[member.userId],
            assignableRoles = roles.filter { !it.isEveryone },
            canManageRoles = canManageRoles,
            canModerate = canKick && member.userId != me?.userId,
            onSaveRoles = { ids -> viewModel.setMemberRoles(member, ids) },
            onKick = { viewModel.kick(member); selected = null },
            onBan = { reason -> viewModel.ban(member, reason); selected = null },
            onDismiss = { selected = null },
            onMessage = {
                scope.launch {
                    viewModel.openDm(member.userId)?.let { channelId ->
                        selected = null
                        onMessage(channelId, member.label)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Roster(
    groups: List<MemberGroup>,
    online: Set<String>,
    onSelect: (Member) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            stickyHeader(key = "group-${group.title}") {
                Text(
                    text = "${group.title.uppercase()} — ${group.members.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(group.members, key = { it.userId }) { member ->
                MemberRow(
                    member = member,
                    online = member.userId in online,
                    onClick = { onSelect(member) },
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member, online: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("member_${member.username}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(
                url = member.avatarUrl,
                size = 36,
                accent = ChatNookColors.Blurple,
                fallbackIcon = Icons.Filled.Person,
                fallbackLabel = member.label,
            )
            PresenceDot(
                online = online,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = member.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = member.roleColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isOwner) {
                    Text(
                        text = stringResource(R.string.members_owner_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatNookColors.Yellow,
                    )
                }
            }
            member.customStatus?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
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
private fun PresenceDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (online) ChatNookColors.Green else ChatNookColors.Outline),
    )
}

/** Banner, avatar and badges, plus role assignment for managers and "message". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(
    member: Member,
    online: Boolean,
    /** Null until the count arrives; then a row of the caller's servers in common. */
    mutual: List<Guild>?,
    assignableRoles: List<GuildRole>,
    canManageRoles: Boolean,
    /** Kick/ban controls: the caller may kick and the target is not the caller. */
    canModerate: Boolean,
    onSaveRoles: (List<String>) -> Unit,
    onKick: () -> Unit,
    onBan: (String?) -> Unit,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Banner, then the avatar sitting half over its bottom edge.
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val stops = member.theme.stops.mapNotNull(HexColor::parse)
                when {
                    !member.bannerUrl.isNullOrBlank() -> AsyncImage(
                        model = member.bannerUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    stops.size >= 2 -> Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(stops.map { Color(it) }),
                        ),
                    )

                    else -> Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(ChatNookColors.Blurple, ChatNookColors.Pink),
                            ),
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box {
                    Avatar(
                        url = member.avatarUrl,
                        size = 76,
                        accent = ChatNookColors.Blurple,
                        fallbackIcon = Icons.Filled.Person,
                        fallbackLabel = member.label,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // A decoration is a transparent frame drawn over the avatar.
                    member.avatarDecorationUrl?.takeIf { it.isNotBlank() }?.let { decoration ->
                        AsyncImage(
                            model = decoration,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(92.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (online) ChatNookColors.Green else ChatNookColors.Outline,
                            ),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(
                        text = member.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "@${member.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (member.badges.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            member.badges.forEach { badge ->
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChatNookColors.TextPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ChatNookColors.DarkElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            member.customStatus?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            val facts = buildList {
                if (member.joinedAt > 0) {
                    add(
                        stringResource(
                            R.string.members_member_since,
                            SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                .format(Date(member.joinedAt)),
                        ),
                    )
                }
                mutual?.let {
                    add(pluralStringResource(R.plurals.members_mutual_servers, it.size, it.size))
                }
            }
            Text(
                text = if (online) {
                    stringResource(R.string.members_online)
                } else {
                    stringResource(R.string.members_offline)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (online) ChatNookColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .testTag("member_facts"),
                )
            }

            if (canManageRoles && assignableRoles.isNotEmpty() && !member.isOwner) {
                var picked by remember(member.userId) { mutableStateOf(member.roleIds.toSet()) }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(R.string.member_roles_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    text = stringResource(R.string.member_roles_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                assignableRoles.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                picked = if (role.id in picked) picked - role.id else picked + role.id
                            }
                            .testTag("assign_${role.name}")
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = role.id in picked,
                            onCheckedChange = null,
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    role.color?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.outline,
                                ),
                        )
                        Text(
                            text = role.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Button(
                    onClick = { onSaveRoles(picked.toList()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("member_roles_save"),
                ) {
                    Text(stringResource(R.string.member_roles_save))
                }
            }

            Button(
                onClick = onMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("member_message"),
            ) {
                Text(stringResource(R.string.members_message))
            }

            if (canModerate && !member.isOwner) {
                var confirmKick by remember { mutableStateOf(false) }
                var confirmBan by remember { mutableStateOf(false) }
                var reason by remember { mutableStateOf("") }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmKick = true },
                        modifier = Modifier.weight(1f).testTag("member_kick"),
                    ) { Text(stringResource(R.string.member_kick)) }
                    OutlinedButton(
                        onClick = { confirmBan = true },
                        modifier = Modifier.weight(1f).testTag("member_ban"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.member_ban)) }
                }

                if (confirmKick) {
                    AlertDialog(
                        onDismissRequest = { confirmKick = false },
                        title = { Text(stringResource(R.string.member_kick_title, member.label)) },
                        text = { Text(stringResource(R.string.member_kick_body)) },
                        confirmButton = {
                            TextButton(onClick = { confirmKick = false; onKick() }) {
                                Text(stringResource(R.string.member_kick))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmKick = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }

                if (confirmBan) {
                    AlertDialog(
                        onDismissRequest = { confirmBan = false },
                        title = { Text(stringResource(R.string.member_ban_title, member.label)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.member_ban_body))
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = reason,
                                    onValueChange = { reason = it.take(200) },
                                    label = { Text(stringResource(R.string.member_ban_reason)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("member_ban_reason"),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmBan = false
                                    onBan(reason.trim().ifBlank { null })
                                },
                            ) {
                                Text(
                                    stringResource(R.string.member_ban),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmBan = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}
