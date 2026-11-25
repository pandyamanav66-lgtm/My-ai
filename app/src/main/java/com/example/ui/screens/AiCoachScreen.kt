package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.DrstCard
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDrstColors
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.viewmodel.AiCoachUiState

/**
 * Clean, minimal Liquid Glass Gemini AI Study Coach Chat.
 * Supports 1-tap switching between Gemini Flash (ultra-fast) & Gemini Deep (detailed) + Photo Doubt Solver.
 */
@Composable
fun AiCoachScreen(
    profile: UserProfile,
    aiState: AiCoachUiState,
    onSendChatMessage: (String) -> Unit,
    onClearChatHistory: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onStopAi: () -> Unit,
    onSelectChatMode: (AiChatMode) -> Unit = {},
    onDismissError: () -> Unit = {},
    onCopyOutput: (String) -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onClearPhoto: () -> Unit = {}
) {
    val c = LocalDrstColors.current
    var chatDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickPrompts = remember(profile.exams) {
        val nextExam = profile.exams.firstOrNull()?.name
        listOf(
            "Analyze my week" to "Review my study routine and completion this week and give me 3 practical tips.",
            if (nextExam != null) "Plan for $nextExam" to "Create a focused revision plan for my upcoming $nextExam exam."
            else "Exam strategy" to "Help me create a focused 14-day exam revision plan based on my study hours.",
            "Explain a concept" to "Help me understand a tough topic step by step with an easy example.",
            "Fix my routine" to "Suggest how to optimize my daily routine for better concentration and less burnout."
        )
    }

    LaunchedEffect(aiState.chatMessages.size, aiState.streamingChatReply.length) {
        val count = aiState.chatMessages.size + (if (aiState.isChatStreaming) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem((count - 1).coerceAtLeast(0))
        }
    }

    val isImeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = if (isImeVisible) 10.dp else 98.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Compact Liquid Glass AI Coach Header + Gemini Flash / Deep Model Switcher
        DrstCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(c.prismCyan.copy(alpha = 0.28f), c.prismViolet.copy(alpha = 0.32f))))
                            .border(1.dp, c.prismCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (c.isDark) c.prismCyan else c.band,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gemini Study Coach",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = aiState.chatMode.description,
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted),
                            maxLines = 1
                        )
                    }

                    // Simple Hinglish / English toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(99.dp))
                            .padding(2.dp)
                    ) {
                        listOf("Hinglish", "English").forEach { lang ->
                            val active = profile.aiLanguage.equals(lang, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (active) c.band else Color.Transparent)
                                    .clickable { onSetLanguage(lang) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = lang,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (active) c.onBand else c.muted
                                    )
                                )
                            }
                        }
                    }

                    if (aiState.chatMessages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChatHistory,
                            modifier = Modifier.size(34.dp).testTag("ai_clear_chat_button")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear chat",
                                tint = c.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 1-Tap Gemini Model Selector: Flash (Ultra-Fast) vs Deep (Detailed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiChatMode.entries.forEach { mode ->
                        val selected = aiState.chatMode == mode
                        val isFast = mode == AiChatMode.FAST
                        val activeBrush = if (isFast) {
                            Brush.horizontalGradient(listOf(c.prismCyan, c.ok))
                        } else {
                            Brush.horizontalGradient(listOf(c.prismViolet, c.band))
                        }
                        val activeContentColor = if (isFast) Color(0xFF061322) else Color.White
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .then(
                                    if (selected) Modifier.background(activeBrush)
                                    else Modifier.background(Color.Transparent)
                                )
                                .clickable { onSelectChatMode(mode) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("ai_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isFast) Icons.Rounded.Bolt else Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = if (selected) activeContentColor else c.muted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (isFast) "Gemini Flash" else "Gemini Deep",
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (selected) activeContentColor else c.muted
                                )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (isFast) "Fast" else "Detailed",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = if (selected) activeContentColor.copy(alpha = 0.85f) else c.muted.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Dismissible Error Banner
        if (!aiState.errorMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("ai_error_banner"),
                shape = RoundedCornerShape(16.dp),
                color = c.badBg,
                border = BorderStroke(1.dp, c.badInk.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.badInk, modifier = Modifier.size(18.dp))
                    Text(
                        text = aiState.errorMessage,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.badInk)
                    )
                    IconButton(
                        onClick = onDismissError,
                        modifier = Modifier.size(28.dp).testTag("ai_error_dismiss")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss error", tint = c.badInk, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Messages Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (aiState.chatMessages.isEmpty() && !aiState.isChatStreaming) {
                item(key = "empty_welcome") {
                    DrstCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = c.prismCyan,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "Hi ${profile.name.substringBefore(" ")}! What are we studying?",
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = c.ink
                                )
                            )
                            Text(
                                text = "Ask any doubt, get a study plan, or tap a quick suggestion below.",
                                textAlign = TextAlign.Center,
                                style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = c.muted)
                            )
                        }
                    }
                }
            }

            items(aiState.chatMessages, key = { it.id }) { msg ->
                val badgeLabel = if (msg.role != "user") {
                    if (msg.modelUsed.contains("lite", ignoreCase = true)) "Gemini Flash" else "Gemini Deep"
                } else null
                ChatMessageBubble(
                    isUser = msg.role == "user",
                    text = msg.text,
                    modelBadge = badgeLabel,
                    onCopy = if (msg.role != "user") ({ onCopyOutput(msg.text) }) else null
                )
            }

            if (aiState.isChatStreaming) {
                item(key = "streaming_bubble") {
                    ChatMessageBubble(
                        isUser = false,
                        text = aiState.streamingChatReply.ifBlank {
                            if (aiState.chatMode == AiChatMode.FAST) "Flash answering..." else "Thinking deeply..."
                        },
                        modelBadge = aiState.chatMode.label,
                        isStreaming = true,
                        onStop = onStopAi
                    )
                }
            }
        }

        // Compact 1-tap Starter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { (label, prompt) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (c.isDark) Color(0x55101836) else Color(0xCCFFFFFF))
                        .border(1.dp, c.glassBorderTop, RoundedCornerShape(99.dp))
                        .clickable(enabled = !aiState.isChatStreaming) { onSendChatMessage(prompt) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = c.ink
                        )
                    )
                }
            }
        }

        // Attached Photo Doubt Preview Pill
        val attachedPhoto = aiState.attachedPhotoBitmap
        AnimatedVisibility(visible = attachedPhoto != null) {
            if (attachedPhoto != null) {
                val previewShape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(previewShape)
                        .background(if (c.isDark) Color(0x88101836) else Color(0xE6FFFFFF))
                        .border(1.dp, c.prismCyan.copy(alpha = 0.65f), previewShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        bitmap = attachedPhoto.asImageBitmap(),
                        contentDescription = "Attached question photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.glassBorderTop, RoundedCornerShape(10.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Photo doubt attached",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = c.ink
                            )
                        )
                        Text(
                            text = "Tap Send for step-by-step solution",
                            style = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = c.muted)
                        )
                    }
                    IconButton(
                        onClick = onClearPhoto,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("ai_clear_photo_button")
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove attached photo",
                            tint = c.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Frosted Glass Input Composer
        val canSend = chatDraft.isNotBlank() || attachedPhoto != null
        val composerShape = RoundedCornerShape(22.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(composerShape)
                .background(if (c.isDark) Color(0x880E1633) else Color(0xE6FFFFFF))
                .border(1.1.dp, c.glassBorderTop, composerShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onPickPhoto,
                enabled = !aiState.isChatStreaming,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (attachedPhoto != null) c.prismCyan.copy(alpha = 0.22f)
                        else c.surface2
                    )
                    .testTag("ai_pick_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = "Attach question photo",
                    tint = if (attachedPhoto != null) c.prismCyan else c.ink,
                    modifier = Modifier.size(20.dp)
                )
            }

            OutlinedTextField(
                value = chatDraft,
                onValueChange = { chatDraft = it },
                placeholder = {
                    Text(
                        text = if (attachedPhoto != null) "Add note (optional)..." else "Ask or snap a doubt...",
                        fontSize = 13.sp,
                        color = c.muted
                    )
                },
                modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend && !aiState.isChatStreaming) {
                            onSendChatMessage(chatDraft)
                            chatDraft = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (aiState.isChatStreaming) {
                IconButton(
                    onClick = onStopAi,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c.prismCoral.copy(alpha = 0.2f))
                        .testTag("ai_stop_button")
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop AI", tint = c.prismCoral)
                }
            } else {
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSendChatMessage(chatDraft)
                            chatDraft = ""
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.horizontalGradient(listOf(c.prismCyan, c.ok))
                            else Brush.horizontalGradient(listOf(c.track, c.track))
                        )
                        .testTag("ai_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) Color(0xFF061322) else c.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    isUser: Boolean,
    text: String,
    modelBadge: String? = null,
    isStreaming: Boolean = false,
    onStop: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    val c = LocalDrstColors.current
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(c.band, c.band2))
                    } else {
                        Brush.verticalGradient(
                            if (c.isDark) listOf(Color(0x80101836), Color(0x660B1228))
                            else listOf(Color(0xE6FFFFFF), Color(0xCCEEF5FF))
                        )
                    }
                )
                .border(
                    1.dp,
                    if (isUser) c.prismCyan.copy(alpha = 0.55f) else c.glassBorderTop,
                    shape
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isUser) c.onBand else c.ink
                )
            )
            if (isStreaming && onStop != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = c.prismCyan)
                    Text(
                        text = if (modelBadge != null) "$modelBadge generating..." else "Generating...",
                        fontSize = 11.sp,
                        color = c.muted
                    )
                }
            } else if (onCopy != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!modelBadge.isNullOrBlank()) {
                        val isFlash = modelBadge.contains("Flash", ignoreCase = true)
                        Text(
                            text = modelBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFlash) c.prismCyan else c.prismViolet
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy reply", tint = c.muted, modifier = Modifier.size(13.dp))
                        Text("Copy", fontSize = 11.sp, color = c.muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
