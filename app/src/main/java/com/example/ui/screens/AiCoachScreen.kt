package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.*
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.viewmodel.AiCoachUiState
import com.example.viewmodel.AiHubSection
import com.example.viewmodel.AiTool
import java.util.Locale

@Composable
fun AiCoachScreen(
    profile: UserProfile,
    aiState: AiCoachUiState,
    onSelectSection: (AiHubSection) -> Unit,
    onSelectChatMode: (AiChatMode) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChatHistory: () -> Unit,
    onStartTranscriptRecording: () -> Unit,
    onStopAndTranscribeAudio: () -> Unit,
    onCopyTranscript: (String) -> Unit,
    onSendTranscriptToChat: () -> Unit,
    onSendTranscriptToQuickAdd: () -> Unit,
    onSaveTranscriptAsDoubt: (String) -> Unit,
    onToggleVoiceAutoSpeak: () -> Unit,
    onStopVoiceSpeaking: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecordingAndRespond: () -> Unit,
    onClearVoiceConversation: () -> Unit,
    onSelectTool: (AiTool) -> Unit,
    onSetLanguage: (String) -> Unit,
    onRunWeeklyCoach: () -> Unit,
    onRunBuildRoutine: (String) -> Unit,
    onApplyPreviewRoutine: () -> Unit,
    onDiscardPreviewRoutine: () -> Unit,
    onUndoAppliedRoutine: () -> Unit,
    onRunQuickAdd: (String) -> Unit,
    onUndoQuickAdd: () -> Unit,
    onRunExamPlanner: (String, Int, String) -> Unit,
    onUpdateDoubtDraft: (String, String) -> Unit,
    onRunDoubtSolver: (String, String) -> Unit,
    onStopAi: () -> Unit
) {
    val c = LocalDrstColors.current
    val context = LocalContext.current

    // Runtime RECORD_AUDIO permission handling
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var micPermissionDenied by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            pendingMicAction?.invoke()
        } else {
            micPermissionDenied = true
        }
        pendingMicAction = null
    }

    fun requireMicPermissionThen(action: () -> Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            micPermissionDenied = false
            action()
        } else {
            pendingMicAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Tool inputs
    val insights = remember(profile) { Analytics.localSmartInsights(profile) }
    var chatDraft by remember { mutableStateOf("") }
    var routineGoalsInput by remember { mutableStateOf("") }
    var quickAddInput by remember { mutableStateOf("") }
    var customExamName by remember(profile.exams) {
        mutableStateOf(profile.exams.firstOrNull()?.name ?: "")
    }
    var customExamDays by remember(profile.exams) {
        val first = profile.exams.firstOrNull()
        val d = if (first != null) daysBetween(todayKey(), first.date).coerceAtLeast(1) else 30
        mutableStateOf(d.toString())
    }
    var examWeakAreas by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. 4-Mode Liquid Glass AI Hub Switcher + Language Bar
        item(key = "ai_hub_switcher") {
            DrstCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c.gold.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = c.goldInk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Gemini AI Studio",
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = c.ink
                                )
                            )
                            Text(
                                text = "Chat • Live Voice • Transcribe • Study Tools",
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                            )
                        }
                    }

                    // Compact language pill toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.gold.copy(alpha = 0.22f))
                            .border(1.dp, c.gold.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                            .clickable {
                                val idx = AI_LANGUAGES.indexOf(profile.aiLanguage)
                                val next = AI_LANGUAGES[(idx + 1) % AI_LANGUAGES.size]
                                onSetLanguage(next)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("ai_language_cycle")
                    ) {
                        Text(
                            text = profile.aiLanguage,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = c.goldInk
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 4-section glass pill bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiHubSection.entries.forEach { section ->
                        val active = aiState.activeSection == section
                        val icon = when (section) {
                            AiHubSection.CHAT -> Icons.Rounded.Forum
                            AiHubSection.VOICE -> Icons.Rounded.GraphicEq
                            AiHubSection.TRANSCRIBE -> Icons.Rounded.Mic
                            AiHubSection.TOOLS -> Icons.Rounded.Psychology
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.band else Color.Transparent)
                                .clickable { onSelectSection(section) }
                                .padding(vertical = 6.dp, horizontal = 2.dp)
                                .testTag("ai_section_${section.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = section.label,
                                tint = if (active) c.gold else c.muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = section.label,
                                maxLines = 1,
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = if (active) c.onBand else c.muted
                                )
                            )
                        }
                    }
                }
            }
        }

        // Mic permission banner if user denied
        if (micPermissionDenied) {
            item(key = "mic_perm_banner") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = c.warnBg,
                    border = BorderStroke(1.dp, c.warn)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = c.warnInk)
                        Text(
                            text = "Microphone permission is required for Voice Live and Audio Transcription. Tap the mic button to grant permission.",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.warnInk)
                        )
                    }
                }
            }
        }

        // Error Box (shared across all AI sections)
        if (aiState.errorMessage != null) {
            item(key = "ai_error") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = c.badBg,
                    border = BorderStroke(1.dp, c.badInk.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.badInk)
                        Text(
                            text = aiState.errorMessage,
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.badInk)
                        )
                    }
                }
            }
        }

        when (aiState.activeSection) {
            // =========================================================
            // SECTION 1: MULTI-TURN GEMINI CHATBOT (Firestore Synced)
            // =========================================================
            AiHubSection.CHAT -> {
                item(key = "chat_mode_bar") {
                    DrstCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiChatMode.entries.forEach { mode ->
                                    val selected = aiState.chatMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectChatMode(mode) },
                                        label = { Text(mode.label, fontSize = 12.sp) },
                                        modifier = Modifier.testTag("chat_mode_${mode.name.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = c.band,
                                            selectedLabelColor = c.onBand,
                                            containerColor = c.surface2,
                                            labelColor = c.ink
                                        )
                                    )
                                }
                            }
                            if (aiState.chatMessages.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearChatHistory,
                                    modifier = Modifier.testTag("clear_chat_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = "Clear chat history",
                                        tint = c.muted
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Chat thread
                        if (aiState.chatMessages.isEmpty() && !aiState.isChatStreaming) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ask your personal Gemini Study Tutor anything — it knows your live routine, streak, and upcoming exams.",
                                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                                )
                                val starters = listOf(
                                    "How can I stay focused for my study blocks today?",
                                    "Make a quick 2-hour revision strategy for my weak topics",
                                    "Quiz me with 3 fast questions on Maths"
                                )
                                starters.forEach { prompt ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSendChatMessage(prompt) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = c.surface2,
                                        border = BorderStroke(1.dp, c.line)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = c.goldInk, modifier = Modifier.size(16.dp))
                                            Text(prompt, fontSize = 12.sp, color = c.ink)
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                aiState.chatMessages.forEach { msg ->
                                    ChatBubble(
                                        isUser = msg.role == "user",
                                        text = msg.text,
                                        tagLabel = if (msg.role == "user") "You" else "Gemini"
                                    )
                                }
                                if (aiState.isChatStreaming) {
                                    ChatBubble(
                                        isUser = false,
                                        text = aiState.streamingChatReply.ifBlank { "Thinking..." },
                                        tagLabel = "Gemini • Streaming",
                                        isStreaming = true
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Composer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatDraft,
                                onValueChange = { chatDraft = it },
                                placeholder = { Text("Ask Gemini in ${profile.aiLanguage}...", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                                shape = RoundedCornerShape(16.dp),
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (chatDraft.isNotBlank() && !aiState.isChatStreaming) {
                                            onSendChatMessage(chatDraft)
                                            chatDraft = ""
                                        }
                                    }
                                )
                            )
                            if (aiState.isChatStreaming) {
                                FilledIconButton(
                                    onClick = onStopAi,
                                    modifier = Modifier.size(48.dp).testTag("ai_chat_stop"),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = c.badBg,
                                        contentColor = c.badInk
                                    )
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop reply")
                                }
                            } else {
                                FilledIconButton(
                                    onClick = {
                                        if (chatDraft.isNotBlank()) {
                                            onSendChatMessage(chatDraft)
                                            chatDraft = ""
                                        }
                                    },
                                    enabled = chatDraft.isNotBlank(),
                                    modifier = Modifier.size(48.dp).testTag("ai_chat_send"),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = c.band,
                                        contentColor = c.gold
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // SECTION 2: REAL-TIME VOICE CONVERSATION STUDIO
            // =========================================================
            AiHubSection.VOICE -> {
                item(key = "voice_live_studio") {
                    DrstCard(title = "Real-Time Voice Conversation") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = aiState.voiceAutoSpeak,
                                    onCheckedChange = { onToggleVoiceAutoSpeak() },
                                    modifier = Modifier.testTag("voice_autospeak_switch")
                                )
                                Text(
                                    text = "Auto-Speak Reply",
                                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.ink)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (aiState.isVoiceSpeaking) {
                                    TextButton(onClick = onStopVoiceSpeaking) {
                                        Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.badInk)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mute", color = c.badInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (aiState.voiceTurns.isNotEmpty()) {
                                    IconButton(onClick = onClearVoiceConversation, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear voice turns", tint = c.muted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Interactive Reactive Voice Orb
                        VoiceReactiveOrb(
                            isRecording = aiState.isVoiceRecording,
                            isThinking = aiState.isVoiceThinking,
                            isSpeaking = aiState.isVoiceSpeaking,
                            amplitude = aiState.micAmplitude,
                            recordingSeconds = aiState.recordingSeconds,
                            onTapOrb = {
                                if (aiState.isVoiceRecording) {
                                    onStopVoiceRecordingAndRespond()
                                } else if (!aiState.isVoiceThinking) {
                                    requireMicPermissionThen { onStartVoiceRecording() }
                                }
                            }
                        )

                        Spacer(Modifier.height(14.dp))

                        if (aiState.voiceStreamingText.isNotBlank()) {
                            ChatBubble(
                                isUser = false,
                                text = aiState.voiceStreamingText,
                                tagLabel = "Gemini Voice • Live",
                                isStreaming = true
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (aiState.voiceTurns.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                aiState.voiceTurns.reversed().forEach { turn ->
                                    ChatBubble(
                                        isUser = turn.role == "user",
                                        text = turn.text,
                                        tagLabel = if (turn.role == "user") "Voice Note" else "Gemini Mentor"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // SECTION 3: AUDIO TRANSCRIPTION
            // =========================================================
            AiHubSection.TRANSCRIBE -> {
                item(key = "audio_transcribe_studio") {
                    DrstCard(title = "Audio Transcription • Gemini Multimodal") {
                        Text(
                            text = "Record lecture notes, study doubts, or voice tasks in Hindi, Hinglish, or English. Gemini transcribes cleanly and lets you send it straight to Chat or Quick Add.",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Live Waveform Bars + Record Button
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AudioWaveformStrip(
                                active = aiState.isRecordingTranscript,
                                amplitude = aiState.micAmplitude
                            )

                            Spacer(Modifier.height(12.dp))

                            val mm = aiState.recordingSeconds / 60
                            val ss = aiState.recordingSeconds % 60
                            Text(
                                text = when {
                                    aiState.isTranscribing -> "Transcribing with Gemini..."
                                    aiState.isRecordingTranscript -> String.format(Locale.US, "Recording • %02d:%02d", mm, ss)
                                    else -> "Tap to record audio"
                                },
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (aiState.isRecordingTranscript) c.badInk else c.ink,
                                    fontFeatureSettings = "tnum"
                                )
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (aiState.isRecordingTranscript) {
                                        onStopAndTranscribeAudio()
                                    } else {
                                        requireMicPermissionThen { onStartTranscriptRecording() }
                                    }
                                },
                                enabled = !aiState.isTranscribing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("transcribe_record_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (aiState.isRecordingTranscript) c.badInk else c.band,
                                    contentColor = Color.White
                                )
                            ) {
                                if (aiState.isTranscribing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.gold)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Transcribing...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = if (aiState.isRecordingTranscript) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = if (aiState.isRecordingTranscript) Color.White else c.gold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (aiState.isRecordingTranscript) "Stop & Transcribe" else "Start Audio Recording",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (aiState.transcriptText.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = c.surface2,
                                border = BorderStroke(1.dp, c.gold.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "TRANSCRIPT",
                                        style = TextStyle(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.9.sp,
                                            color = c.goldInk
                                        )
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = aiState.transcriptText,
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp,
                                            color = c.ink
                                        ),
                                        modifier = Modifier.testTag("transcript_output_text")
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onCopyTranscript(aiState.transcriptText) },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, c.line)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = c.ink)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy", fontSize = 11.sp, color = c.ink)
                                        }
                                        Button(
                                            onClick = onSendTranscriptToChat,
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
                                        ) {
                                            Text("Ask Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onSendTranscriptToQuickAdd,
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, c.gold)
                                        ) {
                                            Text("Quick Add Task", fontSize = 11.sp, color = c.goldInk, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { onSaveTranscriptAsDoubt(aiState.transcriptText) },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, c.line)
                                        ) {
                                            Text("Save Doubt", fontSize = 11.sp, color = c.ink)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // SECTION 4: COACH TOOLS (Smart Insights + 5 Gemini Tools)
            // =========================================================
            AiHubSection.TOOLS -> {
                // 1. Smart Insights (100% local, works offline)
                item(key = "smart_insights") {
                    DrstCard(title = "Smart Insights (Instant • Works Offline)") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            insights.forEach { (heading, body) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.surface2)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(c.gold)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = heading,
                                            style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                                        )
                                        Text(
                                            text = body,
                                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 5 AI Coach Tools
                item(key = "ai_controls") {
                    DrstCard(title = "AI Study Coach Tools") {
                        val tools = listOf(
                            AiTool.WEEKLY_COACH to "Weekly Coach",
                            AiTool.BUILD_ROUTINE to "Build Routine",
                            AiTool.QUICK_ADD to "Quick Add",
                            AiTool.EXAM_PLANNER to "Exam Planner",
                            AiTool.DOUBT_SOLVER to "Doubt Solver"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tools.forEach { (tool, label) ->
                                val active = aiState.selectedTool == tool
                                FilterChip(
                                    selected = active,
                                    onClick = { onSelectTool(tool) },
                                    label = { Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) },
                                    modifier = Modifier.testTag("ai_tool_${tool.name.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = c.band,
                                        selectedLabelColor = c.onBand,
                                        containerColor = c.surface2,
                                        labelColor = c.ink
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        when (aiState.selectedTool) {
                            AiTool.WEEKLY_COACH -> {
                                Text(
                                    text = "Analyzes your real 7-day routine completion, study hours, subject balance, streak, and upcoming exams.",
                                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, color = c.muted)
                                )
                                Spacer(Modifier.height(10.dp))
                                AiActionRow(
                                    primaryLabel = "Analyze My Week",
                                    isStreaming = aiState.isStreaming,
                                    onPrimary = onRunWeeklyCoach,
                                    onStop = onStopAi,
                                    testTag = "ai_weekly_coach_run"
                                )
                            }

                            AiTool.BUILD_ROUTINE -> {
                                OutlinedTextField(
                                    value = routineGoalsInput,
                                    onValueChange = { routineGoalsInput = it },
                                    placeholder = { Text("e.g. Wake 6am, 7h JEE prep, coaching 4-7pm, sleep 11pm", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("ai_build_routine_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2
                                )
                                Spacer(Modifier.height(10.dp))
                                AiActionRow(
                                    primaryLabel = "Build My Routine",
                                    isStreaming = aiState.isStreaming,
                                    onPrimary = { onRunBuildRoutine(routineGoalsInput) },
                                    onStop = onStopAi,
                                    testTag = "ai_build_routine_run"
                                )

                                if (aiState.previousRoutineForUndo != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = c.okBg,
                                        border = BorderStroke(1.dp, c.ok.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("New routine applied!", fontSize = 13.sp, color = c.okInk, fontWeight = FontWeight.SemiBold)
                                            TextButton(onClick = onUndoAppliedRoutine, modifier = Modifier.testTag("undo_routine_button")) {
                                                Text("Undo", color = c.okInk, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (aiState.previewRoutine != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = c.surface2,
                                        border = BorderStroke(1.dp, c.gold)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "ROUTINE PREVIEW (${aiState.previewRoutine.size} tasks)",
                                                style = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.goldInk)
                                            )
                                            if (!aiState.previewSummary.isNullOrBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(aiState.previewSummary, fontSize = 12.sp, color = c.muted)
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            aiState.previewRoutine.forEach { t ->
                                                Text(
                                                    text = "• ${formatTime12h(t.start)}–${formatTime12h(t.end)}: ${t.title} (${t.category})",
                                                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.ink),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = onApplyPreviewRoutine,
                                                    modifier = Modifier.weight(1f).testTag("apply_preview_routine"),
                                                    colors = ButtonDefaults.buttonColors(containerColor = c.ok, contentColor = Color.White),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("Apply", fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = onDiscardPreviewRoutine,
                                                    modifier = Modifier.weight(1f).testTag("discard_preview_routine"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, c.line)
                                                ) {
                                                    Text("Discard", color = c.ink)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AiTool.QUICK_ADD -> {
                                OutlinedTextField(
                                    value = quickAddInput,
                                    onValueChange = { quickAddInput = it },
                                    placeholder = { Text("e.g. maths revision 7 to 8 pm", fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("ai_quick_add_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                AiActionRow(
                                    primaryLabel = "Quick Add to Routine",
                                    isStreaming = aiState.isStreaming,
                                    enabled = quickAddInput.isNotBlank(),
                                    onPrimary = {
                                        onRunQuickAdd(quickAddInput)
                                        quickAddInput = ""
                                    },
                                    onStop = onStopAi,
                                    testTag = "ai_quick_add_run"
                                )
                                if (aiState.lastQuickAddedSummary != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = c.okBg,
                                        border = BorderStroke(1.dp, c.ok.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = aiState.lastQuickAddedSummary,
                                                fontSize = 12.sp,
                                                color = c.okInk,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = onUndoQuickAdd, modifier = Modifier.testTag("undo_quick_add_button")) {
                                                Text("Undo", color = c.okInk, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            AiTool.EXAM_PLANNER -> {
                                if (profile.exams.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        profile.exams.forEach { ex ->
                                            val d = daysBetween(todayKey(), ex.date).coerceAtLeast(1)
                                            AssistChip(
                                                onClick = {
                                                    customExamName = ex.name
                                                    customExamDays = d.toString()
                                                },
                                                label = { Text("${ex.name} (${d}d)", fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = customExamName,
                                        onValueChange = { customExamName = it },
                                        label = { Text("Exam") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = customExamDays,
                                        onValueChange = { customExamDays = it.filter { ch -> ch.isDigit() }.take(3) },
                                        label = { Text("Days") },
                                        singleLine = true,
                                        modifier = Modifier.width(86.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = examWeakAreas,
                                    onValueChange = { examWeakAreas = it },
                                    placeholder = { Text("Weak chapters or topics to prioritize (optional)", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                AiActionRow(
                                    primaryLabel = "Build Exam Plan",
                                    isStreaming = aiState.isStreaming,
                                    enabled = customExamName.isNotBlank(),
                                    onPrimary = {
                                        onRunExamPlanner(
                                            customExamName.trim(),
                                            customExamDays.toIntOrNull()?.coerceAtLeast(1) ?: 14,
                                            examWeakAreas
                                        )
                                    },
                                    onStop = onStopAi,
                                    testTag = "ai_exam_plan_run"
                                )
                            }

                            AiTool.DOUBT_SOLVER -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    profile.subjects.forEach { s ->
                                        val active = s == aiState.doubtSubject
                                        FilterChip(
                                            selected = active,
                                            onClick = { onUpdateDoubtDraft(s, aiState.doubtQuestion) },
                                            label = { Text(s, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = c.gold.copy(alpha = 0.24f),
                                                selectedLabelColor = c.ink,
                                                containerColor = c.surface2,
                                                labelColor = c.muted
                                            )
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = aiState.doubtQuestion,
                                    onValueChange = { onUpdateDoubtDraft(aiState.doubtSubject, it) },
                                    placeholder = { Text("Paste your question or concept doubt...", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("ai_doubt_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 3
                                )
                                Spacer(Modifier.height(10.dp))
                                AiActionRow(
                                    primaryLabel = "Solve Doubt",
                                    isStreaming = aiState.isStreaming,
                                    enabled = aiState.doubtQuestion.isNotBlank(),
                                    onPrimary = { onRunDoubtSolver(aiState.doubtSubject, aiState.doubtQuestion) },
                                    onStop = onStopAi,
                                    testTag = "ai_doubt_solve_run"
                                )
                            }
                        }
                    }
                }

                // 3. Streaming Output Card for Tools
                if (aiState.streamOutput.isNotBlank() || aiState.isStreaming) {
                    item(key = "ai_output") {
                        DrstCard(title = if (aiState.isStreaming) "Gemini is thinking..." else "AI Coach Response") {
                            if (aiState.isStreaming && aiState.streamOutput.isBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = c.gold
                                    )
                                    Text("Streaming answer in ${profile.aiLanguage}...", fontSize = 13.sp, color = c.muted)
                                }
                            } else {
                                Text(
                                    text = aiState.streamOutput,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = c.ink
                                    ),
                                    modifier = Modifier.testTag("ai_stream_output")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    isUser: Boolean,
    text: String,
    tagLabel: String,
    isStreaming: Boolean = false
) {
    val c = LocalDrstColors.current
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = tagLabel,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = c.muted
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .background(if (isUser) c.band else c.surface2)
                .border(
                    width = 1.dp,
                    color = if (isUser) c.gold.copy(alpha = 0.55f) else c.line,
                    shape = bubbleShape
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isStreaming && text == "Thinking...") {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = c.gold)
                }
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isUser) c.onBand else c.ink
                    )
                )
            }
        }
    }
}

@Composable
private fun VoiceReactiveOrb(
    isRecording: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    amplitude: Float,
    recordingSeconds: Int,
    onTapOrb: () -> Unit
) {
    val c = LocalDrstColors.current
    val infinite = rememberInfiniteTransition(label = "voice_idle")
    val idlePulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_pulse"
    )
    val dynamicScale by animateFloatAsState(
        targetValue = when {
            isRecording -> 1.0f + amplitude.coerceIn(0f, 1f) * 0.28f
            isSpeaking -> 1.08f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "orb_amp"
    )

    val orbColor = when {
        isRecording -> Color(0xFFEF4444)
        isThinking -> c.gold
        isSpeaking -> c.ok
        else -> c.band
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(148.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer reactive glow halo
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(if (isRecording) dynamicScale else idlePulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(orbColor.copy(alpha = 0.42f), Color.Transparent)
                        )
                    )
            )
            // Inner clickable Liquid Glass orb
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(if (isRecording) 1f + amplitude * 0.10f else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(orbColor, c.band2)
                        )
                    )
                    .border(2.dp, c.gold.copy(alpha = 0.85f), CircleShape)
                    .clickable(enabled = !isThinking, onClick = onTapOrb)
                    .testTag("voice_live_orb"),
                contentAlignment = Alignment.Center
            ) {
                if (isThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp,
                        color = c.gold
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isRecording -> Icons.Rounded.Stop
                            isSpeaking -> Icons.Rounded.GraphicEq
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = if (isRecording) "Stop and send voice" else "Start speaking",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val statusTitle = when {
            isRecording -> String.format(Locale.US, "Listening... %02d:%02d (Tap orb to send)", recordingSeconds / 60, recordingSeconds % 60)
            isThinking -> "Gemini is listening & preparing reply..."
            isSpeaking -> "Speaking reply aloud..."
            else -> "Tap the orb and speak naturally"
        }
        Text(
            text = statusTitle,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = c.ink
            )
        )
    }
}

@Composable
private fun AudioWaveformStrip(
    active: Boolean,
    amplitude: Float
) {
    val c = LocalDrstColors.current
    val weights = remember { listOf(0.35f, 0.65f, 0.9f, 0.55f, 1.0f, 0.72f, 0.85f, 0.48f, 0.78f, 0.52f, 0.35f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weights.forEach { w ->
            val targetH = if (active) {
                (8f + (28f * w * (0.25f + amplitude * 0.75f))).coerceIn(8f, 40f)
            } else {
                8f
            }
            val barH by animateFloatAsState(
                targetValue = targetH,
                animationSpec = tween(90),
                label = "wave_bar"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(barH.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (active) c.gold else c.track)
            )
        }
    }
}

@Composable
private fun AiActionRow(
    primaryLabel: String,
    isStreaming: Boolean,
    enabled: Boolean = true,
    onPrimary: () -> Unit,
    onStop: () -> Unit,
    testTag: String
) {
    val c = LocalDrstColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPrimary,
            enabled = enabled && !isStreaming,
            modifier = Modifier.weight(1f).height(46.dp).testTag(testTag),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.band, contentColor = c.onBand)
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp), tint = c.gold)
            Spacer(Modifier.width(6.dp))
            Text(primaryLabel, fontWeight = FontWeight.Bold)
        }

        if (isStreaming) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.height(46.dp).testTag("ai_stop_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, c.badInk)
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop AI", tint = c.badInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop", color = c.badInk, fontWeight = FontWeight.Bold)
            }
        }
    }
}
