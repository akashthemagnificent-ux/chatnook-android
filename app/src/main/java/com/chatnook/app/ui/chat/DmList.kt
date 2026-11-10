package com.chatnook.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatnook.app.R
import com.chatnook.app.domain.model.DmSummary
import com.chatnook.app.domain.model.UnreadState
import com.chatnook.app.ui.theme.ChatNookColors

/** The home column: conversations, newest first, with the same badges as channels. */
@Composable
internal fun DmList(
    dms: List<DmSummary>,
    unread: Map<String, UnreadState>,
    onOpen: (channelId: String, name: String) -> Unit,
) {
    if (dms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.dm_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(dms, key = { it.channelId }) { dm ->
            DmRow(
                dm = dm,
                unread = unread[dm.channelId] ?: UnreadState.None,
                onClick = { onOpen(dm.channelId, dm.name) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DmRow(dm: DmSummary, unread: UnreadState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("dm_${dm.name}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            url = dm.avatarUrl,
            size = 40,
            accent = ChatNookColors.Blurple,
            fallbackIcon = Icons.Filled.Person,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dm.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread.visible) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dm.lastMessage ?: stringResource(R.string.dm_no_messages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        UnreadBadge(unread, tag = "dm_${dm.name}")
    }
}
