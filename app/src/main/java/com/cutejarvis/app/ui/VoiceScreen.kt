package com.cutejarvis.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutejarvis.app.MainViewModel
import com.cutejarvis.app.voice.Phrases
import com.cutejarvis.app.voice.VoiceHub
import com.cutejarvis.app.voice.VoiceState
import com.cutejarvis.app.voice.WakeWordService

/** Full-screen voice assistant: idle, listening, thinking, speaking, sleeping and error states backed by real STT/TTS. */
@Composable fun VoiceScreen(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.ui.collectAsState()
    val session = VoiceHub.session
    val wakeOn = VoiceHub.wakeRunning
    DisposableEffect(Unit) { onDispose { VoiceHub.onVoiceScreenClosed() } }
    BackHandler(onBack = onClose)

    var hasMic by remember { mutableStateOf(VoiceHub.micGranted(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (ok) session.pressMic() else session.fail("Microphone permission was denied. You can still type in Chat.")
    }
    fun mic() { if (hasMic) session.pressMic() else launcher.launch(Manifest.permission.RECORD_AUDIO) }

    // Opening the screen starts a conversation, unless one is already running (e.g. the wake word started it in the background).
    LaunchedEffect(Unit) {
        val st = session.state
        if (hasMic && (st == VoiceState.Idle || st == VoiceState.Error)) session.pressMic()
    }

    val st = session.state
    val accent = if (st == VoiceState.Speaking) Cj.Purple else if (st == VoiceState.Error) Cj.Coral else if (st == VoiceState.Sleeping) Cj.Blue else Cj.Cyan
    val wakeHint = Phrases.wake(s.settings.assistantName, s.settings.wakePhrase).first()
    val breathe by rememberInfiniteTransition(label = "sleep").animateFloat(
        0.94f, 1.04f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val zAlpha by rememberInfiniteTransition(label = "z").animateFloat(
        0.15f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "zalpha",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1740), Cj.Deep, Color(0xFF040817))))) {
        IconButton(onClose, Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Filled.Close, "Close") }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.settings.handsFree, { vm.updateSettings { it.copy(handsFree = !it.handsFree) } }, { Text("Hands-free") })
            if (wakeOn) Pill("Wake word on", Cj.Mint, Modifier.align(Alignment.CenterVertically))
        }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.SpaceBetween, Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    GlowCircle(300, accent)
                    Box(if (st == VoiceState.Sleeping) Modifier.scale(breathe).alpha(0.55f) else Modifier) { Mascot(190) }
                    if (st == VoiceState.Sleeping) Text("z z z", Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 30.dp).alpha(zAlpha), style = MaterialTheme.typography.headlineSmall, color = Cj.Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    when (st) {
                        VoiceState.Listening -> t("Listening…"); VoiceState.Processing -> t("Thinking…")
                        VoiceState.Waking -> "Waking up…"; VoiceState.Executing -> "Executing…"
                        VoiceState.Speaking -> t("Speaking…"); VoiceState.Idle -> t("Standby")
                        VoiceState.Sleeping -> "Sleeping"; VoiceState.Error -> "Oops"
                    },
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
                Text(
                    when (st) {
                        VoiceState.Listening -> session.partial.ifBlank { t("Say something") }
                        VoiceState.Processing -> "Let me find the best answer for you."
                        VoiceState.Waking -> "Hi! Getting ready to listen…"; VoiceState.Executing -> "Running that on your phone."
                        VoiceState.Speaking -> session.note ?: "I'm reading my answer aloud."
                        VoiceState.Idle -> if (wakeOn) "Say “$wakeHint” or tap the mic" else t("Tap the mic to talk")
                        VoiceState.Sleeping -> if (wakeOn) "Say “$wakeHint” to wake me, or tap the mic" else "Tap the mic to wake me"
                        VoiceState.Error -> session.error ?: "Something went wrong."
                    },
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
                )
            }
            Waveform(active = st == VoiceState.Listening || st == VoiceState.Speaking || st == VoiceState.Processing || st == VoiceState.Executing || st == VoiceState.Waking,
                level = if (st == VoiceState.Listening) session.level else 0.6f, color = accent)
            Box(
                Modifier.size(76.dp).shadow(14.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape)
                    .background(Cj.Navy).border(2.dp, accent, CircleShape).clickable(enabled = st != VoiceState.Processing && st != VoiceState.Executing && st != VoiceState.Waking) { mic() },
                Alignment.Center,
            ) {
                if (st == VoiceState.Processing || st == VoiceState.Executing || st == VoiceState.Waking) CircularProgressIndicator(Modifier.size(32.dp), color = accent, strokeWidth = 3.dp)
                else Icon(if (st == VoiceState.Listening || st == VoiceState.Speaking) Icons.Filled.Stop else Icons.Filled.Mic, "Microphone", tint = accent, modifier = Modifier.size(34.dp))
            }
        }
    }
}
