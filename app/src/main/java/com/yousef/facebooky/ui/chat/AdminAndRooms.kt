package com.yousef.facebooky.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.yousef.facebooky.data.model.RoomInfo
import com.yousef.facebooky.ui.ChatViewModel
import com.yousef.facebooky.ui.icons.AppIcons
import com.yousef.facebooky.ui.theme.ChatThemes
import com.yousef.facebooky.ui.theme.chatThemeById
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The fast rainbow "ADMIN" tag shown next to the admin's own messages. */
@Composable
fun AdminTag(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "admin")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "hue",
    )
    val rainbow = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
        Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF7C4DFF), Color(0xFFFF1744),
    )
    val shift = (t * rainbow.size).toInt().coerceIn(0, rainbow.size - 1)
    val rotated = rainbow.drop(shift) + rainbow.take(shift)
    val brush = Brush.horizontalGradient(rotated)
    Box(modifier.padding(start = 5.dp)) {
        // Black outline: the same text drawn slightly offset in four directions.
        val outline = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Black, color = Color.Black,
        )
        listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f).forEach { (dx, dy) ->
            Text("ADMIN", style = outline, modifier = Modifier.offset(dx.dp, dy.dp))
        }
        Text(
            "ADMIN",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Black,
                brush = brush,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsSheet(vm: ChatViewModel, onDismiss: () -> Unit) {
    var idInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Your ID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                vm.myShortId.ifBlank { "…" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Share this ID so a friend can start a private chat with you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = idInput,
                onValueChange = { idInput = it.uppercase(); error = null },
                label = { Text("Enter a friend's ID") },
                placeholder = { Text("ABC-123") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    busy = true
                    vm.startChatWithId(idInput) { result ->
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
                enabled = idInput.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Start chat with your favourite person")
            }

            Spacer(Modifier.height(20.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text("Chats", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            RoomRow("My Space (everyone)", "Shared lobby", onClick = { vm.openLobby(); onDismiss() })
            vm.myRooms.forEach { room ->
                val otherUid = room.otherUid(vm.myUid ?: "")
                val name = vm.people[otherUid]?.name?.takeIf { it.isNotBlank() } ?: "Private chat"
                RoomRow(name, "Tap to open", onClick = { vm.openRoom(room); onDismiss() })
            }
        }
    }
}

@Composable
private fun RoomRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.User, null, Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Chat theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ChatThemes.forEach { theme ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(theme.id); onDismiss() }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(theme.colors)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (theme.id == current) Icon(AppIcons.User, "Selected", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AdminLoginDialog(onLogin: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Admin") },
        text = {
            Column {
                Text("Enter the admin password.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank() && !busy, onClick = {
                busy = true
                onLogin(password) { result ->
                    busy = false
                    if (result == null) onClose() else error = result
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enter")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun AdminScreen(vm: ChatViewModel, onClose: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(AppIcons.Close, "Close", Modifier.size(22.dp)) }
                Spacer(Modifier.width(4.dp))
                AdminTag()
                Spacer(Modifier.width(8.dp))
                Text("Console", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Show my admin badge here", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (vm.isAdmin) "This device is the admin. The rainbow ADMIN tag shows on your messages."
                                    else "This device isn't the admin yet. Enter the password from the Welcome menu.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = vm.adminBadgeOn, onCheckedChange = { vm.toggleAdminBadge() })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    Text("All chats (${vm.adminRooms.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(vm.adminRooms, key = { it.id }) { room ->
                    AdminRoomCard(vm, room, fmt) { vm.adminOpenRoom(room) }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("All devices (${vm.adminUsers.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(vm.adminUsers, key = { it.uid }) { u ->
                    AdminDeviceRow(u, myRegion = vm.myRegion, fmt = fmt)
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

/** A chat shown as a rounded card with the two members' photos side by side. */
@Composable
private fun AdminRoomCard(vm: ChatViewModel, room: RoomInfo, fmt: SimpleDateFormat, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (room.isMain) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("👥", style = MaterialTheme.typography.titleLarge) }
            } else {
                // Two member photos inside one rectangle.
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    room.members.take(2).forEach { uid ->
                        val photo = vm.people[uid]?.photoUrl.orEmpty()
                        Box(Modifier.size(52.dp)) {
                            if (photo.isNotBlank()) {
                                AsyncImage(model = photo, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.User, null, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val names = room.members.map { uid -> vm.people[uid]?.name?.takeIf { it.isNotBlank() } ?: uid.take(6) }
                Text(
                    if (room.isMain) "My Space (everyone)" else names.joinToString("  ↔  "),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (room.isMain) "shared lobby" else "${room.members.size} people") +
                        (if (room.lastActivityMs > 0) " · ${fmt.format(Date(room.lastActivityMs))}" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Tap to open and read from the start", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AdminDeviceRow(u: com.yousef.facebooky.data.model.Presence, myRegion: String, fmt: SimpleDateFormat) {
    // Flag a device whose timezone differs from mine (possible different location).
    val different = myRegion.isNotBlank() && u.region.isNotBlank() && u.region != myRegion
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (u.photoUrl.isNotBlank()) {
                AsyncImage(model = u.photoUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(AppIcons.User, null, Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(u.name.ifBlank { "(no name yet)" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "ID ${u.shortId.ifBlank { "—" }} · ${u.device.ifBlank { "device" }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (u.region.ifBlank { "region —" }) + (if (u.language.isNotBlank()) " · ${u.language}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (different) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (different) {
                    Spacer(Modifier.width(6.dp))
                    Text("⚠ different area", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text("last seen ${if (u.lastSeenMs > 0) fmt.format(Date(u.lastSeenMs)) else "—"}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Up to 3 member photos stacked into one 40dp circle (for group headers). */
@Composable
fun GroupAvatar(photos: List<String>, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val shown = photos.take(3)
        when (shown.size) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(AppIcons.User, null, Modifier.size(20.dp)) }
            1 -> AsyncImage(shown[0], null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Row(Modifier.fillMaxSize()) {
                shown.forEach { p ->
                    AsyncImage(p, null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
fun PersonCardDialog(profile: com.yousef.facebooky.data.model.UserProfile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (profile.photoUrl.isNotBlank()) {
                        AsyncImage(profile.photoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.User, null, Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (profile.isAdmin) AdminTag()
                }
                if (profile.shortId.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("ID ${profile.shortId}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersSheet(vm: ChatViewModel, onAdd: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Members", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            vm.currentMembers().forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (m.photoUrl.isNotBlank()) AsyncImage(m.photoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(AppIcons.User, null, Modifier.size(20.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        m.name.ifBlank { "…" } + if (m.uid == vm.myUid) " (you)" else "",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    )
                    if (m.isAdmin) AdminTag()
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.AddUser, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add new members")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.leaveCurrentRoom(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Leave chat", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "You can only remove yourself. No one can remove anybody else.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AddMemberDialog(onAdd: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Add member") },
        text = {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it.uppercase(); error = null },
                label = { Text("Friend's ID") },
                placeholder = { Text("ABC-123") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = id.isNotBlank() && !busy, onClick = {
                busy = true
                onAdd(id) { result -> busy = false; if (result == null) onClose() else error = result }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } },
    )
}
