package com.cutejarvis.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutejarvis.app.MainViewModel
import java.util.Calendar

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Filled.Home), Chat("Chat", Icons.AutoMirrored.Filled.Chat), Memories("Memories", Icons.Filled.Memory),
    Skills("Skills", Icons.Filled.Extension), Settings("Settings", Icons.Filled.Settings)
}

@Composable fun App(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.error, s.info) {
        (s.error ?: s.info)?.let { snack.showSnackbar(it); vm.clearMessages() }
    }
    CompositionLocalProvider(LocalLang provides s.settings.language) {
        CjBackground {
            Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snack) }) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when {
                        !s.ready -> SplashScreen()
                        !s.settings.onboarded -> OnboardingScreen(vm) { vm.completeOnboarding() }
                        else -> MainShell(vm)
                    }
                }
            }
        }
    }
}

@Composable private fun BrandTitle(size: Int) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Jarvis", style = TextStyle(brush = Brush.horizontalGradient(listOf(Cj.Cyan, Cj.Blue, Cj.Purple)), fontSize = size.sp, fontWeight = FontWeight.ExtraBold))
    Text("AI ASSISTANT", color = Cj.Cyan, letterSpacing = 4.sp, style = MaterialTheme.typography.labelMedium)
}

@Composable private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) { GlowCircle(300); Mascot(190) }
            Spacer(Modifier.height(16.dp))
            BrandTitle(46)
            Text(t("Your Personal AI Assistant"), modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(Modifier.width(170.dp).clip(RoundedCornerShape(50)), color = Cj.Cyan, trackColor = Cj.Blue.copy(alpha = 0.25f))
            Text(t("Loading..."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun OnboardingScreen(vm: MainViewModel, onStart: () -> Unit) {
    val s by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LANGUAGES.forEach { (code, label) -> Box(Modifier.padding(start = 6.dp)) { Chip(label) { vm.setLanguage(code) } } }
        }
        Box(contentAlignment = Alignment.Center) { GlowCircle(240); Mascot(150) }
        Text(t("Welcome to"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        BrandTitle(40)
        Text(t("Your Personal AI Assistant"), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(Icons.Filled.Mic, Cj.Cyan, "Voice & Chat Assistant" to "Talk or type, get real answers"),
                Triple(Icons.Filled.Phone, Cj.Blue, "Smart Phone Help" to "Open apps, set alarms — with your OK"),
                Triple(Icons.Filled.Memory, Cj.Purple, "Memory" to "Remembers what matters, only if you approve"),
            ).forEach { (icon, tint, text) ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, tint); Spacer(Modifier.width(14.dp))
                        Column {
                            Text(text.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        var showBackend by remember { mutableStateOf(false) }
        TextButton({ showBackend = !showBackend }) { Text("Backend server settings") }
        if (showBackend) BackendUrlEditor(vm)
        Spacer(Modifier.height(16.dp))
        GradientButton(t("Get Started"), onStart, Modifier.fillMaxWidth(), icon = Icons.Filled.Bolt)
    }
}

@Composable fun MainShell(vm: MainViewModel) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var voice by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.Main) }
    var sel by remember { mutableStateOf("") }
    val s by vm.ui.collectAsState()
    val goPage = { p: Page -> page = p; tab = Tab.Settings }
    BackHandler(enabled = !voice && tab == Tab.Settings && page != Page.Main) { page = Page.Main }
    BackHandler(enabled = !voice && tab != Tab.Home && !(tab == Tab.Settings && page != Page.Main)) { tab = Tab.Home }
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Cj.Navy, modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                Tab.entries.forEach { tb ->
                    NavigationBarItem(
                        selected = tab == tb, onClick = { tab = tb; if (tb == Tab.Settings) page = Page.Main },
                        label = { Text(t(tb.label), style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(tb.icon, t(tb.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cj.Cyan, selectedTextColor = Cj.Cyan, indicatorColor = Cj.Blue.copy(alpha = 0.3f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm, openVoice = { voice = true }, goChat = { tab = Tab.Chat }, goPage = goPage)
                    Tab.Chat -> ChatScreen(vm) { voice = true }
                    Tab.Memories -> MemoriesScreen(vm)
                    Tab.Skills -> SkillsScreen(vm)
                    Tab.Settings -> SettingsScreen(vm, page, { page = it }, sel, { sel = it })
                }
            }
        }
        if (voice) VoiceScreen(vm) { voice = false }
    }
    s.pending.firstOrNull()?.let { a ->
        AlertDialog(onDismissRequest = {}, shape = RoundedCornerShape(22.dp), containerColor = Cj.Navy, title = { Text(t("Allow this action?")) },
            text = { Text("${s.settings.assistantName} wants to run \"${a.skillId}\" with:\n${a.args}") },
            confirmButton = { Button({ vm.approve(a) }) { Text(t("Allow")) } },
            dismissButton = { TextButton({ vm.deny(a) }) { Text(t("Deny")) } })
    }
}

private fun greetingKey(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good Morning"; in 12..16 -> "Good Afternoon"; in 17..21 -> "Good Evening"; else -> "Hello"
}

@Composable private fun HomeScreen(vm: MainViewModel, openVoice: () -> Unit, goChat: () -> Unit, goPage: (Page) -> Unit) {
    val s by vm.ui.collectAsState()
    LaunchedEffect(s.providers == null) { if (s.providers == null) vm.loadProviders() }
    val hasProvider = s.providers?.any { it.enabled }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) {
            val g = t(greetingKey())
            Text("$g!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(t("How can I help you today?"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) { GlowCircle(270); Mascot(160) }
        GradientButton(t("Talk to Jarvis"), openVoice, Modifier.fillMaxWidth(), icon = Icons.Filled.Mic)
        Spacer(Modifier.height(10.dp))
        GradientButton(t("Ask anything"), goChat, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Chat)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple(t("Phone Control"), Icons.Filled.Smartphone, Cj.Cyan) to Page.Phone,
                Triple(t("Chat History"), Icons.Filled.History, Cj.Blue) to Page.History,
                Triple(t("Permissions Center"), Icons.Filled.Security, Cj.Purple) to Page.Permissions,
            ).forEach { (tile, dest) ->
                SoftCard(Modifier.weight(1f), onClick = { goPage(dest) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(tile.second, tile.third, 46)
                        Spacer(Modifier.height(8.dp))
                        Text(tile.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard(Modifier.fillMaxWidth(), onClick = { goPage(Page.Api) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Cloud, if (hasProvider == true) Cj.Mint else Cj.Amber)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (hasProvider) { true -> "Ready. Chat is connected."; false -> "Not set up yet. Tap to add one."; null -> "Checking…" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasProvider == true) Pill("Active", Cj.Mint) else if (hasProvider == false) Pill("Setup", Cj.Amber)
            }
        }
    }
}

@Composable fun BackendUrlEditor(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    var url by remember(s.settings.backendUrl) { mutableStateOf(s.settings.backendUrl) }
    Column {
        OutlinedTextField(url, { url = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            supportingText = { Text("Emulator: http://10.0.2.2:3000 • Real phone: your computer's LAN IP or a deployed https:// URL") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.saveBackendUrl(url) }) { Text("Save") }
            OutlinedButton({ vm.checkBackend() }) { Text("Test") }
        }
    }
}
