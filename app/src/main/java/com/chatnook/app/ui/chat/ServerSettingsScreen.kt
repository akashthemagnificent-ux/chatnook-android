package com.chatnook.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.R
import com.chatnook.app.domain.model.Invite
import com.chatnook.app.ui.theme.ChatNookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** Owner-only server settings: identity, invite codes and the delete switch. */
@Composable
fun ServerSettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val guilds by viewModel.guilds.collectAsStateWithLifecycle()
    val selectedGuildId by viewModel.selectedGuildId.collectAsStateWithLifecycle()
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val guild = guilds.firstOrNull { it.guildId == selectedGuildId }
    val context = LocalContext.current

    var name by remember(guild?.guildId) { mutableStateOf(guild?.name.orEmpty()) }
    var deleting by remember { mutableStateOf(false) }
    var creatingInvite by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("server_name"),
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
