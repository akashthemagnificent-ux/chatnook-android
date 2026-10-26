package com.chatnook.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.chatnook.app.R
import com.chatnook.app.domain.model.HexColor
import com.chatnook.app.domain.model.Profile
import com.chatnook.app.domain.model.ProfileTheme
import com.chatnook.app.domain.model.gradientStops
import com.chatnook.app.ui.theme.ChatNookColors

/**
 * Nitro-style cosmetics editor: banner, avatar, gradient theme, accent, status, badges.
 * Every cosmetic is a plain column on `profiles`, so the server needs no special casing.
 */
@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Local edits are staged here; nothing is written until Save.
    var displayName by remember(profile?.userId) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var status by remember(profile?.userId) {
        mutableStateOf(profile?.customStatus.orEmpty())
    }
    var theme by remember(profile?.userId) { mutableStateOf(profile?.theme ?: ProfileTheme.None) }
    var accent by remember(profile?.userId) { mutableStateOf(profile?.accentColor) }
    var badges by remember(profile?.userId) { mutableStateOf(profile?.badges.orEmpty()) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.readBytes(uri) ?: return@rememberLauncherForActivityResult
        viewModel.uploadCosmetic(
            bucket = "avatars",
            bytes = bytes,
            extension = context.extensionOf(uri),
        ) { url -> viewModel.saveProfile(avatarUrl = url) }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.readBytes(uri) ?: return@rememberLauncherForActivityResult
        viewModel.uploadCosmetic(
            bucket = "banners",
            bytes = bytes,
            extension = context.extensionOf(uri),
        ) { url -> viewModel.saveProfile(bannerUrl = url) }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
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
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            ProfileCard(
                profile = profile,
                theme = theme,
                accent = accent,
                badges = badges,
                status = status,
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.profile_avatar)) }
                    OutlinedButton(
                        onClick = { pickBanner.launch("image/*") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.profile_banner)) }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.profile_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text(stringResource(R.string.profile_custom_status)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel(stringResource(R.string.profile_theme))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NitroThemes.all.forEach { preset ->
                        ThemeSwatch(
                            preset = preset,
                            label = stringResource(preset.labelRes),
                            selected = theme.preset == preset.name,
                            onClick = {
                                theme = if (preset.stops.isEmpty()) {
                                    ProfileTheme.None
                                } else {
                                    ProfileTheme(preset.name, preset.stops)
                                }
                            },
                        )
                    }
                }

                SectionLabel(stringResource(R.string.profile_accent))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NitroThemes.accents.forEach { hex ->
                        AccentSwatch(
                            hex = hex,
                            selected = accent == hex,
                            onClick = { accent = hex },
                        )
                    }
                }

                SectionLabel(stringResource(R.string.profile_badges))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NitroThemes.badges.forEach { (key, labelRes) ->
                        val owned = key in badges
                        Surface(
                            color = if (owned) ChatNookColors.Blurple
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                badges = if (owned) badges - key else badges + key
                            },
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (owned) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.saveProfile(
                            displayName = displayName,
                            customStatus = status,
                            accentColor = accent,
                            theme = theme,
                            badges = badges,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = profile != null,
                ) { Text(stringResource(R.string.profile_save)) }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.profile_sign_out),
                        color = ChatNookColors.Red,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile?,
    theme: ProfileTheme,
    accent: String?,
    badges: List<String>,
    status: String,
) {
    val stops = theme.stops.mapNotNull(HexColor::parse).map { Color(it.toInt()) }
    val brush = if (stops.size >= 2) {
        Brush.linearGradient(stops)
    } else {
        Brush.linearGradient(
            listOf(
                accent?.let(HexColor::parse)?.let { Color(it.toInt()) } ?: ChatNookColors.Blurple,
                ChatNookColors.BlurpleDark,
            ),
        )
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(brush),
        ) {
            profile?.bannerUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(brush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    url = profile?.avatarUrl,
                    size = 76,
                    accent = ChatNookColors.Blurple,
                    fallbackIcon = Icons.Filled.Person,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = profile?.authorLabel?.takeIf { it.isNotBlank() } ?: "…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "@${profile?.username.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (status.isNotBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
        }
        if (badges.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                badges.forEach { badge ->
                    Surface(
                        color = ChatNookColors.Blurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = ChatNookColors.Blurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ThemeSwatch(
    preset: NitroThemes.Preset,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stops = preset.stops.mapNotNull(HexColor::parse).map { Color(it.toInt()) }
    val brush = if (stops.size >= 2) {
        Brush.linearGradient(stops)
    } else {
        Brush.linearGradient(listOf(ChatNookColors.DarkElevated, ChatNookColors.DarkHover))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp, 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccentSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(HexColor.parse(hex)?.toInt() ?: 0xFF5865F2.toInt()))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

/** Built-in Nitro presets. Stops are hex so they round-trip through the `theme` jsonb. */
object NitroThemes {
    data class Preset(val name: String, val stops: List<String>, val labelRes: Int)

    val all = listOf(
        Preset("none", emptyList(), R.string.profile_theme_none),
        Preset("sunset", listOf("#FF7A18", "#AF002D", "#5C0A6B"), R.string.profile_theme_sunset),
        Preset("ocean", listOf("#00C6FF", "#0072FF", "#001E4D"), R.string.profile_theme_ocean),
        Preset("forest", listOf("#43C6AC", "#191654", "#0B3D2E"), R.string.profile_theme_forest),
        Preset("neon", listOf("#F72585", "#7209B7", "#3A0CA3"), R.string.profile_theme_neon),
    )

    val accents = listOf("#5865F2", "#EB459E", "#3BA55D", "#FAA61A", "#ED4245", "#9B59B6")

    val badges = listOf(
        "Early" to R.string.profile_badge_early,
        "Nitro" to R.string.profile_badge_nitro,
        "Staff" to R.string.profile_badge_staff,
    )
}

/** Reads up to the bucket's 8 MB limit; larger picks are rejected rather than OOM-ed. */
internal fun Context.readBytes(uri: Uri): ByteArray? = runCatching {
    contentResolver.openInputStream(uri)?.use { stream ->
        val buffer = stream.readBytes()
        buffer.takeIf { it.size <= 8 * 1024 * 1024 }
    }
}.getOrNull()

internal fun Context.extensionOf(uri: Uri): String {
    val mime = contentResolver.getType(uri).orEmpty()
    return when {
        mime.contains("png") -> "png"
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        else -> "jpg"
    }
}
