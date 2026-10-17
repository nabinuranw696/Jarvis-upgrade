package com.cutejarvis.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutejarvis.app.MainViewModel

@Composable fun EmptyState(text: String) = Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun TabPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp)).background(Cj.Navy).border(1.dp, Cj.Blue.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (i == selected) Cj.brand else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                Alignment.Center,
            ) { Text(label, color = if (i == selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
        }
    }

// ---------------------------------------------------------------- Chat

@Composable fun ChatScreen(vm: MainViewModel, onVoice: () -> Unit) {
    val s by vm.ui.collectAsState()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(s.chat.size) { if (s.chat.isNotEmpty()) list.animateScrollToItem(s.chat.lastIndex) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.settings.assistantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Cj.Mint, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Online"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton({ vm.newChat() }) { Icon(Icons.Filled.Refresh, "New chat", tint = Cj.Cyan) }
        }
        Box(Modifier.weight(1f)) {
            if (s.chat.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) { GlowCircle(190); Mascot(100) }
                Spacer(Modifier.height(8.dp))
                Text("Say hi to ${s.settings.assistantName}!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("You need an AI provider first: Settings → API & Integrations.", textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Summarize this", "Write a poem", "Explain something simply", "Give me ideas").forEach { q -> Chip(q) { input = "$q: " } }
                }
            } else LazyColumn(state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.chat) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!m.fromUser) { Avatar(30); Spacer(Modifier.width(8.dp)) }
                        if (m.fromUser) {
                            Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(Cj.brand).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(m.text, color = Color.White)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                                color = if (m.note) MaterialTheme.colorScheme.secondaryContainer else Cj.Navy,
                                border = BorderStroke(1.dp, Cj.Cyan.copy(alpha = 0.35f)),
                                modifier = Modifier.widthIn(max = 290.dp),
                            ) { Text(m.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                        }
                    }
                }
                if (s.busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(30); Spacer(Modifier.width(8.dp))
                        Text("${s.settings.assistantName} is thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text(t("Type a message…")) }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(26.dp),
                trailingIcon = { IconButton(onVoice) { Icon(Icons.Filled.Mic, "Voice", tint = Cj.Cyan) } },
            )
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank() && !s.busy
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (canSend) Cj.brand else Brush.horizontalGradient(listOf(Color(0xFF35446B), Color(0xFF35446B))))
                    .clickable(enabled = canSend) { vm.send(input); input = "" },
                Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }
}

// ---------------------------------------------------------------- Memories

@Composable fun MemoriesScreen(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(s.memories == null) { vm.loadMemoriesIfNeeded() }
    val tints = listOf(Cj.Cyan, Cj.Blue, Cj.Purple, Cj.Mint, Cj.Amber)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(t("Memory"), "Saved only with your approval. They don't expire like chat history (30 days).")
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, placeholder = { Text(t("Add a memory")) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp))
            Spacer(Modifier.width(8.dp))
            GradientButton(t("Add"), { vm.addMemory(text); text = "" }, enabled = text.isNotBlank(), icon = Icons.Filled.Add)
        }
        Text("Don't store passwords or codes here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val m = s.memories
        when {
            m == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            m.isEmpty() -> EmptyState(t("No memories yet."))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                itemsIndexed(m, key = { _, item -> item.id }) { i, mem ->
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Memory, tints[i % tints.size], 38)
                            Spacer(Modifier.width(12.dp))
                            Text(mem.content, Modifier.weight(1f))
                            IconButton({ vm.deleteMemory(mem.id) }) { Icon(Icons.Filled.Delete, "Delete memory", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Skills store

@Composable fun SkillsScreen(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.skills == null) { if (s.skills == null) vm.loadSkills() }
    val sk = s.skills
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(t("Skills"), "Install a skill to let Jarvis use it. Risky ones always ask first.")
        TabPills(listOf(t("All"), t("Installed"), t("Available")), tab) { tab = it }
        val shown = sk?.filter { tab == 0 || (tab == 1 && it.enabled) || (tab == 2 && !it.enabled) }
        when {
            shown == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() -> EmptyState("No skills here.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { k ->
                    val key = (k.id + " " + k.category).lowercase()
                    val (icon, tint) = when {
                        "search" in key -> Icons.Filled.Search to Cj.Mint
                        "remind" in key || "alarm" in key || "notif" in key -> Icons.Filled.Notifications to Cj.Purple
                        "flash" in key -> Icons.Filled.FlashlightOn to Cj.Amber
                        k.target == "device" -> Icons.Filled.Smartphone to Cj.Cyan
                        else -> Icons.Filled.Extension to Cj.Blue
                    }
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(icon, tint)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(k.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(k.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(if (k.target == "device") "On your phone" else "On the server", Cj.Blue)
                                    if (k.requiresConfirmation) Pill("Asks first", Cj.Amber)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (k.enabled) Pill(t("Installed"), Cj.Mint, Modifier.clickable { vm.toggleSkill(k) })
                            else Pill(t("Install"), Cj.Cyan, Modifier.clickable { vm.toggleSkill(k) })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Chat history (current session; the backend has no history endpoint yet)

@Composable fun HistoryPage(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    var q by remember { mutableStateOf("") }
    val rows = s.chat.filter { it.fromUser && it.text.contains(q, ignoreCase = true) }.reversed()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, placeholder = { Text(t("Search this session…")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
        Text("Showing this session only. Saved cross-session history isn't available from the backend yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) EmptyState(if (q.isBlank()) "No conversations in this session yet." else "Nothing matches your search.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.size) { i ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.AutoMirrored.Filled.Send, Cj.Blue, 36); Spacer(Modifier.width(12.dp)); Text(rows[i].text)
                    }
                }
            }
        }
    }
}
