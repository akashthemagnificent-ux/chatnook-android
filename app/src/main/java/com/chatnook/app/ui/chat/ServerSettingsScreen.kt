package com.chatnook.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.R
import com.chatnook.app.domain.model.GuildRole
import com.chatnook.app.domain.model.Invite
import com.chatnook.app.domain.model.PermissionBits
import com.chatnook.app.domain.model.has
import com.chatnook.app.domain.model.withPermission
import com.chatnook.app.ui.theme.ChatNookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** Server settings: identity, roles, invite codes and the delete switch. */
@Composable
fun ServerSettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenModeration: () -> Unit,
) {
    val guilds by viewModel.guilds.collectAsStateWithLifecycle()
    val selectedGuildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val guildMuted by viewModel.guildMuted.collectAsStateWithLifecycle()
    val guild = guilds.firstOrNull { it.guildId == selectedGuildId }
    val context = LocalContext.current

    // RLS and the RPCs are the real gate; these only decide what to draw.
    val canManageGuild = permissions has PermissionBits.MANAGE_GUILD
    val canManageRoles = permissions has PermissionBits.MANAGE_ROLES
    val canInvite = permissions has PermissionBits.CREATE_INVITE
    val canKick = permissions has PermissionBits.KICK_MEMBERS

    var name by remember(guild?.guildId) { mutableStateOf(guild?.name.orEmpty()) }
    var deleting by remember { mutableStateOf(false) }
    var creatingInvite by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<GuildRole?>(null) }
    var creatingRole by remember { mutableStateOf(false) }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val bytes = uri?.let(context::readBytes) ?: return@rememberLauncherForActivityResult
        viewModel.uploadGuildIcon(bytes, context.extensionOf(uri))
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                Text(
                    text = stringResource(R.string.server_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (canManageGuild) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        url = guild?.iconUrl,
                        size = 64,
                        accent = ChatNookColors.Blurple,
                        fallbackIcon = Icons.Filled.Person,
                        fallbackLabel = guild?.name,
                    )
                    OutlinedButton(onClick = { pickIcon.launch("image/*") }) {
                        Text(stringResource(R.string.server_icon))
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("server_name"),
                )
                Button(
                    onClick = { viewModel.updateGuild(name = name) },
                    enabled = name.isNotBlank() && name != guild?.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("server_save"),
                ) {
                    Text(stringResource(R.string.server_save))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            if (canManageRoles) {
                SectionTitle(stringResource(R.string.roles_title))
                OutlinedButton(
                    onClick = { creatingRole = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("role_create"),
                ) {
                    Text(stringResource(R.string.roles_create))
                }
                if (roles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.roles_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    roles.forEach { role ->
                        RoleRow(role = role, onClick = { editingRole = role })
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            if (canInvite) {
                SectionTitle(stringResource(R.string.invites_title))
                OutlinedButton(
                    onClick = { creatingInvite = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("invite_create"),
                ) {
                    Text(stringResource(R.string.invites_create))
                }
                if (invites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.invites_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    invites.forEach { invite ->
                        InviteRow(invite = invite, onRevoke = { viewModel.revokeInvite(invite.code) })
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            SectionTitle(stringResource(R.string.notifications_settings_title))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notifications_mute_server),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.notifications_mute_server_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = guildMuted,
                    onCheckedChange = viewModel::setGuildMuted,
                    modifier = Modifier.testTag("server_mute"),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (canKick) {
                SectionTitle(stringResource(R.string.moderation_title))
                OutlinedButton(
                    onClick = onOpenModeration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .testTag("server_moderation"),
                ) {
                    Text(stringResource(R.string.moderation_open))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            if (canManageGuild) {
                SectionTitle(stringResource(R.string.server_danger_zone))
                Button(
                    onClick = { deleting = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ChatNookColors.Red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("server_delete"),
                ) {
                    Text(stringResource(R.string.server_delete))
                }
            }
        }
    }

    if (deleting && guild != null) {
        DeleteGuildDialog(
            guildName = guild.name,
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                viewModel.deleteGuild()
                onBack()
            },
        )
    }

    if (creatingInvite) {
        InviteExpiryDialog(
            onDismiss = { creatingInvite = false },
            onPick = { days ->
                creatingInvite = false
                viewModel.createInvite(days)
            },
        )
    }

    if (creatingRole) {
        RoleEditorDialog(
            role = null,
            onDismiss = { creatingRole = false },
            onSave = { roleName, color, bits ->
                creatingRole = false
                viewModel.createRole(roleName, color, bits)
            },
            onDelete = null,
        )
    }

    editingRole?.let { role ->
        RoleEditorDialog(
            role = role,
            onDismiss = { editingRole = null },
            onSave = { roleName, color, bits ->
                editingRole = null
                viewModel.updateRole(role, roleName, color, bits)
            },
            onDelete = if (role.isEveryone) {
                null
            } else {
                {
                    editingRole = null
                    viewModel.deleteRole(role)
                }
            },
        )
    }
}

@Composable
private fun RoleRow(role: GuildRole, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("role_${role.name}")
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(role.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (role.isEveryone) {
                    stringResource(R.string.roles_everyone_note)
                } else {
                    pluralStringResource(
                        R.plurals.roles_member_count,
                        role.memberCount,
                        role.memberCount,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (role.isAdmin) {
            Text(
                text = stringResource(R.string.perm_administrator),
                style = MaterialTheme.typography.labelSmall,
                color = ChatNookColors.Yellow,
            )
        }
    }
}

/** Colours Discord-like enough to tell roles apart without a full picker. */
private val rolePalette = listOf(
    0xFF5865F2.toInt(),
    0xFFEB459E.toInt(),
    0xFFED4245.toInt(),
    0xFFFEE75C.toInt(),
    0xFF57F287.toInt(),
    0xFF00A8FC.toInt(),
    0xFF9B59B6.toInt(),
    0xFF95A5A6.toInt(),
)

private val permissionRows = listOf(
    PermissionBits.VIEW_CHANNEL to R.string.perm_view_channel,
    PermissionBits.SEND_MESSAGES to R.string.perm_send_messages,
    PermissionBits.ATTACH_FILES to R.string.perm_attach_files,
    PermissionBits.ADD_REACTIONS to R.string.perm_add_reactions,
    PermissionBits.MENTION_EVERYONE to R.string.perm_mention_everyone,
    PermissionBits.CREATE_INVITE to R.string.perm_create_invite,
    PermissionBits.MANAGE_MESSAGES to R.string.perm_manage_messages,
    PermissionBits.MANAGE_CHANNELS to R.string.perm_manage_channels,
    PermissionBits.MANAGE_ROLES to R.string.perm_manage_roles,
    PermissionBits.KICK_MEMBERS to R.string.perm_kick_members,
    PermissionBits.MANAGE_GUILD to R.string.perm_manage_guild,
    PermissionBits.ADMINISTRATOR to R.string.perm_administrator,
)

@Composable
private fun RoleEditorDialog(
    role: GuildRole?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int?, permissions: Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(role?.id) { mutableStateOf(role?.name.orEmpty()) }
    var color by remember(role?.id) { mutableStateOf(role?.color) }
    var bits by remember(role?.id) {
        mutableStateOf(
            role?.permissions ?: (PermissionBits.VIEW_CHANNEL or PermissionBits.SEND_MESSAGES),
        )
    }
    var confirmingDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = {
            Text(
                stringResource(
                    if (role == null) R.string.roles_new_title else R.string.roles_editor_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.roles_name)) },
                    singleLine = true,
                    enabled = role?.isEveryone != true,
                    modifier = Modifier.fillMaxWidth().testTag("role_name"),
                )

                Text(
                    text = stringResource(R.string.roles_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rolePalette.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    width = if (color == swatch) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = swatch }
                                .testTag("role_color_${swatch.toUInt().toString(16)}"),
                        )
                    }
                }
                TextButton(onClick = { color = null }, modifier = Modifier.testTag("role_color_none")) {
                    Text(stringResource(R.string.roles_color_none))
                }

                permissionRows.forEach { (bit, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = bits has bit,
                            onCheckedChange = { on -> bits = bits.withPermission(bit, on) },
                            modifier = Modifier.testTag("perm_$bit"),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.roles_admin_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.testTag("role_delete"),
                    ) {
                        Text(
                            text = stringResource(R.string.roles_delete),
                            color = ChatNookColors.Red,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), color, bits) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("role_save"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (confirmingDelete && onDelete != null && role != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.roles_delete)) },
            text = { Text(stringResource(R.string.roles_delete_body, role.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("role_delete_confirm"),
                ) {
                    Text(stringResource(R.string.channel_delete_confirm), color = ChatNookColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun InviteRow(invite: Invite, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.code,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (invite.isDead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.testTag("invite_${invite.code}"),
            )
            Text(
                text = inviteSummary(invite),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.invites_revoke),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun inviteSummary(invite: Invite): String {
    val uses = if (invite.maxUses != null) {
        stringResource(R.string.invites_uses_max, invite.uses, invite.maxUses)
    } else {
        stringResource(R.string.invites_uses, invite.uses)
    }
    val expiry = when {
        invite.expiresAt == null -> stringResource(R.string.invites_never)
        invite.expiresAt <= System.currentTimeMillis() -> stringResource(R.string.invites_expired)
        else -> dayFormat.format(Date(invite.expiresAt))
    }
    return "$uses · $expiry"
}

@Composable
private fun InviteExpiryDialog(onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.invites_expiry_title)) },
        text = {
            Column {
                TextButton(onClick = { onPick(1) }, modifier = Modifier.testTag("expiry_1")) {
                    Text(stringResource(R.string.invites_expiry_day))
                }
                TextButton(onClick = { onPick(7) }, modifier = Modifier.testTag("expiry_7")) {
                    Text(stringResource(R.string.invites_expiry_week))
                }
                TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("expiry_never")) {
                    Text(stringResource(R.string.invites_expiry_never))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Typed confirmation: the name must match exactly, so a stray tap cannot delete a server. */
@Composable
private fun DeleteGuildDialog(guildName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.server_delete)) },
        text = {
            Column {
                Text(stringResource(R.string.server_delete_body, guildName))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp).testTag("server_delete_confirm_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed == guildName,
                modifier = Modifier.testTag("server_delete_confirm"),
            ) {
                Text(stringResource(R.string.server_delete_confirm), color = ChatNookColors.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
