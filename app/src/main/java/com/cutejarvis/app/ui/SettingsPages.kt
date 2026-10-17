package com.cutejarvis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutejarvis.app.BuildConfig
import com.cutejarvis.app.MainViewModel
import com.cutejarvis.app.data.Provider
import com.cutejarvis.app.data.SPEECH_LANGS
import com.cutejarvis.app.data.speechTag
import com.cutejarvis.app.voice.Phrases
import com.cutejarvis.app.voice.VoiceHub
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class Page { Main, Assistant, Voice, Language, Api, AddApi, ProviderDetail, Permissions, Phone, Privacy, Dev, History, About }

@Composable fun SettingsScreen(vm: MainViewModel, page: Page, setPage: (Page) -> Unit, sel: String, setSel: (String) -> Unit) {
    val back = { setPage(if (page == Page.AddApi || page == Page.ProviderDetail) Page.Api else Page.Main) }
    when (page) {
        Page.Main -> SettingsMain(vm, setPage)
        Page.Assistant -> SubPage(t("Assistant"), back) { AssistantSettings(vm) }
        Page.Voice -> SubPage(t("Voice Settings"), back) { VoiceSettings(vm) }
        Page.Language -> SubPage(t("Appearance & Language"), back) { LanguagePage(vm) }
        Page.Api -> SubPage(t("API & Integrations"), back, scroll = false) { ApiCenter(vm, { setSel(it); setPage(Page.AddApi) }, { setSel(it); setPage(Page.ProviderDetail) }) }
        Page.AddApi -> SubPage("Add provider", back) { AddProviderForm(vm, sel) { setPage(Page.Api) } }
        Page.ProviderDetail -> SubPage("Provider", back) { ProviderDetail(vm, sel) { setPage(Page.Api) } }
        Page.Permissions -> SubPage(t("Permissions Center"), back) { PermissionsPage() }
        Page.Phone -> SubPage(t("Phone Control"), back) { PhoneControl(vm) }
        Page.Privacy -> SubPage(t("Privacy & Security"), back) { PrivacyInfo(vm) }
        Page.Dev -> SubPage(t("Developer Tools"), back) { DevTools(vm) }
        Page.History -> SubPage(t("Chat History"), back, scroll = false) { HistoryPage(vm) }
        Page.About -> SubPage(t("About"), back) { AboutPage() }
    }
}

@Composable private fun SubPage(title: String, back: () -> Unit, scroll: Boolean = true, content: @Composable () -> Unit) = Column(Modifier.fillMaxSize()) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
        IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cj.Cyan) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
}

@Composable private fun SettingsMain(vm: MainViewModel, go: (Page) -> Unit) {
    val s by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(t("Settings"))
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(56); Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.settings.assistantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Wake phrase: ${s.settings.wakePhrase}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        NavRow(Icons.Filled.Face, Cj.Cyan, t("Assistant"), "Name, wake phrase, sleep phrase") { go(Page.Assistant) }
        NavRow(Icons.Filled.RecordVoiceOver, Cj.Purple, t("Voice Settings"), "Hands-free, wake word, voice, speed") { go(Page.Voice) }
        NavRow(Icons.Filled.Language, Cj.Blue, t("Appearance & Language"), "English • नेपाली") { go(Page.Language) }
        NavRow(Icons.Filled.Cloud, Cj.Cyan, t("API & Integrations"), "AI providers, keys, connection tests") { go(Page.Api) }
        NavRow(Icons.Filled.Smartphone, Cj.Mint, t("Phone Control"), "Wi-Fi, Bluetooth, flashlight, volume") { go(Page.Phone) }
        NavRow(Icons.Filled.Security, Cj.Purple, t("Permissions Center"), "What the app can access") { go(Page.Permissions) }
        NavRow(Icons.Filled.History, Cj.Blue, t("Chat History"), "Search this session") { go(Page.History) }
        NavRow(Icons.Filled.Lock, Cj.Mint, t("Privacy & Security"), "Retention, data, backend server") { go(Page.Privacy) }
        NavRow(Icons.Filled.Terminal, Cj.Amber, t("Developer Tools"), "App info, logs, diagnostics") { go(Page.Dev) }
        NavRow(Icons.Filled.Info, Cj.Cyan, t("About"), "Version and info") { go(Page.About) }
    }
}

@Composable private fun AssistantSettings(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    var name by remember(s.settings.assistantName) { mutableStateOf(s.settings.assistantName) }
    var wake by remember(s.settings.wakePhrase) { mutableStateOf(s.settings.wakePhrase) }
    var sleep by remember(s.settings.sleepPhrase) { mutableStateOf(s.settings.sleepPhrase) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) { GlowCircle(180); Mascot(100) }
        OutlinedTextField(name, { name = it }, label = { Text(t("Assistant name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(wake, { wake = it }, label = { Text(t("Wake phrase")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(sleep, { sleep = it }, label = { Text(t("Sleep phrase")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Text("Built-in phrases always work: “${name.ifBlank { "Jarvis" }}, go to sleep / sleep mode / stop listening” and “Hey ${name.ifBlank { "Jarvis" }}” / “${name.ifBlank { "Jarvis" }}, wake up”. Your phrases above are added to them; separate several with |. The wake phrase is used by the on-device detector and must be plain English letters. Turn the detector on in Voice Settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientButton(t("Save Changes"), { vm.saveAssistant(name, wake, sleep) }, Modifier.fillMaxWidth(), enabled = !s.busy)
    }
}

@Composable private fun Picker(label: String, current: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text("$label: $current", Modifier.weight(1f), maxLines = 1) ; Icon(Icons.Filled.ArrowDropDown, null) }
        DropdownMenu(open, { open = false }) {
            options.forEach { (code, name) -> DropdownMenuItem({ Text(name) }, { onPick(code); open = false }) }
        }
    }
}

@Composable private fun VoiceSettings(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.ui.collectAsState()
    val st = s.settings
    var rate by remember(st.speechRate) { mutableFloatStateOf(st.speechRate) }
    var vol by remember(st.volume) { mutableFloatStateOf(st.volume) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    fun enableWake() { VoiceHub.setWakeEnabled(ctx, true)?.let { vm.showError(it) } }
    fun needsNotifPermission() = Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    // If notifications are denied the listening service still runs (and shows in Android's task manager); we just can't draw the indicator.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enableWake() }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) vm.showError("Microphone permission is needed for the wake word.")
        else if (needsNotifPermission()) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else enableWake()
    }
    fun toggleWake(on: Boolean) {
        if (!on) { VoiceHub.setWakeEnabled(ctx, false); return }
        if (!VoiceHub.modelInstalled) { vm.showError("Download the wake-word model first."); return }
        if (!VoiceHub.micGranted(ctx)) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else if (needsNotifPermission()) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else enableWake()
    }

    val tag = st.speechTag()
    val ready = VoiceHub.session.ttsReady // re-read when the TTS engine finishes starting
    val voices = remember(tag, ready) { VoiceHub.session.voicesFor(tag) }
    val progress = VoiceHub.modelProgress

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.RecordVoiceOver, Cj.Cyan); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hands-Free Mode", fontWeight = FontWeight.SemiBold)
                    Text("After Jarvis speaks, it listens again, so you can keep talking without touching the screen. Saying “Jarvis, go to sleep” turns this off; waking turns it back on.", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                Switch(st.handsFree, { v -> vm.updateSettings { it.copy(handsFree = v) } })
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Hearing, Cj.Amber); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wake-word detection", fontWeight = FontWeight.SemiBold)
                        Text("Listens on this phone for “${Phrases.wake(st.assistantName, st.wakePhrase).first()}” while the app is in the background. Audio is analysed on the device and never sent anywhere until you are woken.", style = MaterialTheme.typography.bodySmall, color = dim)
                    }
                    Switch(st.wakeWordEnabled, { v -> toggleWake(v) }, enabled = VoiceHub.modelInstalled || st.wakeWordEnabled)
                }
                if (VoiceHub.wakeStatus.isNotBlank() && st.wakeWordEnabled) Text(VoiceHub.wakeStatus, style = MaterialTheme.typography.bodySmall, color = Cj.Cyan)
                if (!VoiceHub.modelInstalled) {
                    Text("Needs a one-time ~40 MB English speech model (Vosk, runs offline) downloaded over HTTPS from alphacephei.com. Wake phrases must be English words.", style = MaterialTheme.typography.bodySmall, color = dim)
                    if (progress >= 0) { LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth()); Text("Downloading… $progress%", style = MaterialTheme.typography.bodySmall) }
                    else GradientButton("Download wake-word model", { VoiceHub.downloadModel() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Download)
                    VoiceHub.modelError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cj.Coral) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("Model installed", Cj.Mint); Spacer(Modifier.weight(1f))
                        TextButton({ VoiceHub.deleteModel() }) { Text("Remove") }
                    }
                }
                Text("While it is on, Android shows a “microphone is on” notification with a Turn off button. After a phone restart, open Jarvis once to switch listening back on (Android doesn't allow apps to start the microphone in the background). Some phone makers stop background apps to save battery; if it stops responding, exclude Jarvis from battery optimisation.", style = MaterialTheme.typography.bodySmall, color = dim)
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.BatterySaver, Cj.Mint); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Battery saving", fontWeight = FontWeight.SemiBold)
                    Text("The detector only analyses sound while it hears something, shorter retries after silence, and wake listening switches itself off after 30 minutes.", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                Switch(st.batterySaver, { v -> vm.updateSettings { it.copy(batterySaver = v) } })
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speech language", fontWeight = FontWeight.SemiBold)
                Picker("Language", SPEECH_LANGS.firstOrNull { it.first == st.speechLang }?.second ?: st.speechLang, SPEECH_LANGS) { c -> vm.updateSettings { it.copy(speechLang = c, voiceName = "") } }
                Text("Used to understand you and to read answers aloud (now: $tag). Which languages work depends on the speech engines installed on your phone; Nepali needs the Nepali pack in the Google app and a Nepali text-to-speech voice.", style = MaterialTheme.typography.bodySmall, color = dim)
                Picker("Voice", st.voiceName.ifBlank { "System default" }, listOf("" to "System default") + voices.map { it to it }) { v -> vm.updateSettings { it.copy(voiceName = v) } }
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.AutoMirrored.Filled.VolumeUp, Cj.Cyan); Spacer(Modifier.width(12.dp))
                    Text(t("Speech speed"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("%.1fx".format(rate), color = Cj.Cyan)
                }
                Slider(rate, { rate = it }, valueRange = 0.5f..1.5f, onValueChangeFinished = { vm.setSpeechRate(rate) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${(vol * 100).toInt()}%", color = Cj.Cyan)
                }
                Slider(vol, { vol = it }, valueRange = 0.1f..1f, onValueChangeFinished = { vm.updateSettings { it.copy(volume = vol) } })
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Bedtime, Cj.Purple); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep & wake sound", fontWeight = FontWeight.SemiBold)
                    Text("A short beep when Jarvis goes to sleep or wakes up. Sleep mode keeps your chat history and only stops listening.", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                Switch(st.soundCues, { v -> vm.updateSettings { it.copy(soundCues = v) } })
            }
        }
    }
}

@Composable private fun LanguagePage(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("The interface uses the nav, titles and main buttons in your language. More languages can be added in I18n.kt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LANGUAGES.forEach { (code, label) ->
            SoftCard(Modifier.fillMaxWidth(), onClick = { vm.setLanguage(code) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (s.settings.language == code) Icon(Icons.Filled.CheckCircle, null, tint = Cj.Mint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Permissions (real Android checks)

@Composable private fun PermissionsPage() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val rescanned = tick // read so the list re-checks after Scan/Grant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Jarvis only asks for what it needs, when it needs it. Phone actions open Android's own apps, so they need no extra permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @Composable fun row(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, why: String, ok: Boolean, ask: (() -> Unit)?) = SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (ok) Pill(t("Granted"), Cj.Mint)
                else if (ask != null) Pill(t("Grant"), Cj.Cyan, Modifier.clickable { ask() })
                else Pill(t("Not granted"), Cj.Amber)
            }
        }
        val mic = granted(Manifest.permission.RECORD_AUDIO)
        row(Icons.Filled.Mic, Cj.Cyan, "Microphone", "For voice commands and listening", mic) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
        if (Build.VERSION.SDK_INT >= 33) row(Icons.Filled.Notifications, Cj.Amber, "Notifications", "Shows the “microphone is on” indicator while the wake word is listening", granted(Manifest.permission.POST_NOTIFICATIONS)) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        row(Icons.Filled.Alarm, Cj.Purple, "Set alarms", "Lets Jarvis send alarms to your clock app (granted at install)", granted("com.android.alarm.permission.SET_ALARM"), null)
        row(Icons.Filled.Wifi, Cj.Blue, "Internet", "To reach your Jarvis backend and AI provider (granted at install)", granted(Manifest.permission.INTERNET), null)
        GradientButton(t("Scan Permissions"), { tick++ }, Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Filled.Refresh)
    }
}

// ---------------------------------------------------------------- Phone control (user-tapped, runs validated DeviceActions)

@Composable private fun PhoneControl(vm: MainViewModel) {
    var torch by remember { mutableStateOf(false) }
    var vol by remember { mutableFloatStateOf(50f) }
    fun settings(screen: String) = vm.deviceAction("open_settings", buildJsonObject { put("screen", screen) })
    val tiles = listOf(
        Triple("Wi-Fi", Icons.Filled.Wifi, Cj.Cyan) to { settings("wifi") },
        Triple("Bluetooth", Icons.Filled.Bluetooth, Cj.Blue) to { settings("bluetooth") },
        Triple("Display", Icons.Filled.BrightnessMedium, Cj.Amber) to { settings("display") },
        Triple("Sound", Icons.AutoMirrored.Filled.VolumeUp, Cj.Purple) to { settings("sound") },
        Triple("Apps", Icons.Filled.Apps, Cj.Mint) to { settings("apps") },
        Triple("Settings", Icons.Filled.Settings, Cj.Blue) to { settings("") },
        Triple(if (torch) "Flashlight: on" else "Flashlight: off", Icons.Filled.FlashlightOn, if (torch) Cj.Amber else Cj.Cyan) to {
            torch = !torch; vm.deviceAction("toggle_flashlight", buildJsonObject { put("on", torch) })
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap a tile to run it on this phone. Wi-Fi, Bluetooth, display, sound and apps open Android's own settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (tile, action) ->
                    SoftCard(Modifier.weight(1f), onClick = { action() }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(tile.second, tile.third, 46); Spacer(Modifier.height(8.dp)); Text(tile.first, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.AutoMirrored.Filled.VolumeUp, Cj.Cyan); Spacer(Modifier.width(12.dp))
                    Text("Media volume", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("${vol.toInt()}%", color = Cj.Cyan)
                }
                Slider(vol, { vol = it }, valueRange = 0f..100f, onValueChangeFinished = { vm.deviceAction("set_volume", buildJsonObject { put("percent", vol.toInt()) }) })
            }
        }
    }
}

// ---------------------------------------------------------------- Privacy / About / Developer tools

@Composable private fun PrivacyInfo(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(Icons.Filled.Info, Cj.Blue, "Chat history is deleted automatically after 30 days (server-side). Saved memories are kept until you delete them."),
            Triple(Icons.Filled.Lock, Cj.Mint, "AI provider keys are stored encrypted on the backend and are never sent back to this app."),
            Triple(Icons.Filled.Person, Cj.Cyan, "No account needed. This phone gets a random private ID on first launch; your memories and provider keys on the backend are tied to it. Clearing the app's data creates a new ID, and the old server data can no longer be reached."),
            Triple(Icons.Filled.Smartphone, Cj.Amber, "Adding an AI key does not give the AI any extra phone access. Phone actions only run through listed skills, and risky ones ask you first."),
            Triple(Icons.Filled.Mic, Cj.Purple, "Voice uses your phone's speech recognition and text-to-speech engines. Depending on the engine, audio may be processed by Google or your device vendor."),
        ).forEach { (icon, tint, text) ->
            SoftCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) { IconBadge(icon, tint, 36); Spacer(Modifier.width(12.dp)); Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text("Backend server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        BackendUrlEditor(vm)
    }
}

@Composable private fun AboutPage() = Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(contentAlignment = Alignment.Center) { GlowCircle(230); Mascot(140) }
    Text("Jarvis AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Your Personal AI Assistant", textAlign = TextAlign.Center, color = Cj.Cyan)
}

@Composable private fun DevTools(vm: MainViewModel) {
    val s by vm.ui.collectAsState()
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("App info", fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MODEL}", style = MaterialTheme.typography.bodySmall)
                Text("Backend: ${s.settings.backendUrl}", style = MaterialTheme.typography.bodySmall)
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Diagnostics", fontWeight = FontWeight.Bold)
                val mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                Text("Microphone permission: ${if (mic) "granted" else "not granted"}", style = MaterialTheme.typography.bodySmall)
                Text("Speech recognition on this phone: ${if (SpeechRecognizer.isRecognitionAvailable(ctx)) "available" else "not available"}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ vm.checkBackend() }) { Text("Test backend connection") }
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Logs", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ vm.clearLogs() }) { Text("Clear") }
                }
                if (s.logs.isEmpty()) Text("No events yet. Errors and phone actions from this session appear here (never keys or message text).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else s.logs.takeLast(60).forEach { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
            }
        }
        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.newChat(); vm.clearLogs() }) { Text("Clear chat & logs") }
            OutlinedButton({ vm.resetLocalSettings() }) { Text("Reset settings", color = Cj.Coral) }
        }
        Text("A shell terminal is intentionally not included: this app never runs arbitrary commands.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- AI providers

@Composable private fun ApiCenter(vm: MainViewModel, onAdd: (String) -> Unit, onOpen: (String) -> Unit) {
    val s by vm.ui.collectAsState()
    LaunchedEffect(s.providers == null) { if (s.providers == null) vm.loadProviders() }
    val list = s.providers
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No providers yet. Add one to enable chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NavRow(Icons.Filled.AutoAwesome, Cj.Blue, "Google Gemini", "Recommended • free tier available") { onAdd("gemini") }
                    NavRow(Icons.Filled.Cloud, Cj.Mint, "OpenAI", "Uses the OpenAI-compatible API") { onAdd("openai") }
                    NavRow(Icons.Filled.Key, Cj.Purple, "Custom API", "Any OpenAI-compatible https:// endpoint") { onAdd("custom") }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.id }) { ProviderCard(vm, it, s.testingId == it.id) { onOpen(it.id) } } }
            }
        }
        GradientButton(t("Add New Provider"), { onAdd("custom") }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Filled.Add)
    }
}

@Composable private fun ProviderCard(vm: MainViewModel, p: Provider, testing: Boolean, open: () -> Unit) = SoftCard(Modifier.fillMaxWidth(), onClick = open) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (p.kind == "gemini") Icons.Filled.AutoAwesome else Icons.Filled.Cloud, if (p.kind == "gemini") Cj.Blue else Cj.Mint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${if (p.kind == "gemini") "Google Gemini" else "OpenAI-compatible"} • ${p.model} • key …${p.keyLast4}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(p.enabled, { vm.toggleProvider(p) })
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (p.lastTestOk) { null -> Pill(t("Not tested"), Cj.Amber); true -> Pill(t("Connected"), Cj.Mint); false -> Pill("Test failed", Cj.Coral) }
            if (p.isDefault) Pill(t("Default"), Cj.Cyan)
        }
        if (p.lastTestOk == false) Text(p.lastError ?: "Unknown error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        TextButton({ vm.testProvider(p.id) }, enabled = !testing) { Text(if (testing) "Testing…" else t("Test Connection")) }
    }
}

@Composable private fun ProviderDetail(vm: MainViewModel, id: String, done: () -> Unit) {
    val s by vm.ui.collectAsState()
    val p = s.providers?.firstOrNull { it.id == id }
    if (p == null) { EmptyState("This provider no longer exists."); return }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(if (p.kind == "gemini") Icons.Filled.AutoAwesome else Icons.Filled.Cloud, if (p.kind == "gemini") Cj.Blue else Cj.Mint, 72)
        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (p.lastTestOk) { null -> Pill(t("Not tested"), Cj.Amber); true -> Pill(t("Connected"), Cj.Mint); false -> Pill("Test failed", Cj.Coral) }
            if (p.isDefault) Pill(t("Default"), Cj.Cyan)
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Model: ${p.model}"); Text("Key: …${p.keyLast4}")
                p.baseUrl?.let { Text("Endpoint: $it", style = MaterialTheme.typography.bodySmall) }
                if (p.lastTestOk == false) Text(p.lastError ?: "Unknown error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        GradientButton(if (s.testingId == p.id) "Testing…" else t("Test Connection"), { vm.testProvider(p.id) }, Modifier.fillMaxWidth(), enabled = s.testingId != p.id, icon = Icons.Filled.Bolt)
        if (!p.isDefault) OutlinedButton({ vm.setDefault(p.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(t("Set as default")) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Enabled", Modifier.weight(1f)); Switch(p.enabled, { vm.toggleProvider(p) })
        }
        TextButton({ vm.deleteProvider(p.id); done() }) { Text(t("Remove"), color = Cj.Coral) }
    }
}

@Composable private fun AddProviderForm(vm: MainViewModel, preset: String, done: () -> Unit) {
    val gem = preset == "gemini"
    var name by remember(preset) { mutableStateOf(when (preset) { "gemini" -> "Google Gemini"; "openai" -> "OpenAI"; else -> "My AI Provider" }) }
    var model by remember(preset) { mutableStateOf(when (preset) { "gemini" -> "gemini-2.5-flash"; "openai" -> "gpt-4o-mini"; else -> "" }) }
    var base by remember(preset) { mutableStateOf(if (preset == "openai") "https://api.openai.com/v1" else "") }
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("API name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (!gem) OutlinedTextField(base, { base = it }, label = { Text("Base URL (https://…/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(
            key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show key") } },
        )
        Text("The key is sent once to your backend, which stores it encrypted. It is never stored on this phone or written to logs. After saving, use Test Connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientButton(t("Save Provider"), { vm.addProvider(name, if (gem) "gemini" else "openai_compatible", model, base, key); done() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    }
}
