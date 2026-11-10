package com.chatnook.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatnook.app.R
import com.chatnook.app.domain.model.AuditEntry
import com.chatnook.app.domain.model.BannedUser
import com.chatnook.app.ui.theme.ChatNookColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val moderationDateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

/** Who is banned and what moderators have done, both kept live by Realtime. */
@Composable
fun ModerationScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val bans by viewModel.bans.collectAsStateWithLifecycle()
    val audit by viewModel.auditLog.collectAsStateWithLifecycle()

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
                Text(
                    text = stringResource(R.string.moderation_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(R.string.moderation_bans),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                if (bans.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.moderation_no_bans),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                items(bans, key = { it.userId }) { user ->
                    BanRow(user = user, onUnban = { viewModel.unban(user) })
                }

                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.moderation_activity),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                if (audit.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.moderation_no_activity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                items(audit, key = { it.id }) { entry -> AuditRow(entry) }
            }
        }
    }
}

@Composable
private fun BanRow(user: BannedUser, onUnban: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            url = user.avatarUrl,
            size = 32,
            accent = ChatNookColors.Blurple,
            fallbackIcon = Icons.Filled.Person,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    user.reason,
                    moderationDateFormat.format(Date(user.bannedAt)),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onUnban, modifier = Modifier.testTag("unban_${user.name}")) {
            Text(stringResource(R.string.moderation_unban))
        }
    }
}

@Composable
private fun AuditRow(entry: AuditEntry) {
    val verb = stringResource(
        when (entry.action) {
            "kick" -> R.string.moderation_kick
            "ban" -> R.string.moderation_ban
            else -> R.string.moderation_unban_action
        },
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(
                R.string.moderation_entry,
                entry.actorName ?: stringResource(R.string.moderation_unknown),
                verb,
                entry.targetName ?: stringResource(R.string.moderation_unknown),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = listOfNotNull(
                entry.detail,
                moderationDateFormat.format(Date(entry.createdAt)),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
