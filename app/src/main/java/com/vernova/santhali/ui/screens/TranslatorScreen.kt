package com.vernova.santhali.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vernova.santhali.ui.theme.*
import com.vernova.santhali.viewmodel.Direction
import com.vernova.santhali.viewmodel.Phase
import com.vernova.santhali.viewmodel.TranslatorViewModel

private enum class InputTab { SPEECH, TEXT }

@Composable
fun TranslatorScreen(viewModel: TranslatorViewModel) {
    val state by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableStateOf(InputTab.SPEECH) }

    SanthaliTranslatorTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0D1117), Color(0xFF0A1628)))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ───────────────────────────────────────────────
                AppHeader()
                Spacer(Modifier.height(16.dp))

                // ── Feature badges ────────────────────────────────────────
                FeatureBadges()
                Spacer(Modifier.height(20.dp))

                // ── Language direction bar ────────────────────────────────
                LanguageDirectionBar(
                    direction = state.direction,
                    onSwap    = viewModel::swapDirection
                )
                Spacer(Modifier.height(20.dp))

                // ── Input mode tabs ────────────────────────────────────────
                InputTabRow(activeTab = activeTab, onSelect = { activeTab = it })
                Spacer(Modifier.height(20.dp))

                // ── Model loading card ─────────────────────────────────────
                AnimatedVisibility(visible = state.copyingModels) {
                    Column {
                        ModelLoadingCard()
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Speech tab ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = activeTab == InputTab.SPEECH,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MicButton(
                            phase          = state.phase,
                            isRecording    = state.isRecording,
                            modelsReady    = state.modelsReady && !state.copyingModels,
                            onPressStart   = viewModel::startRecording,
                            onPressRelease = viewModel::stopRecordingAndTranslate
                        )
                        Spacer(Modifier.height(8.dp))
                        PhaseLabel(state.phase)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text     = "Tap mic, speak, release — works offline",
                            color    = OnSurfaceDim,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Text tab ───────────────────────────────────────────────
                AnimatedVisibility(
                    visible = activeTab == InputTab.TEXT,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    TextInputPanel(
                        direction  = state.direction,
                        phase      = state.phase,
                        modelsReady = state.modelsReady && !state.copyingModels,
                        onTranslate = { text -> viewModel.translateText(text) },
                        onReset     = viewModel::reset
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Result cards ───────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.sourceText.isNotBlank(),
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        ResultCard(
                            label    = if (state.direction == Direction.HINDI_TO_SANTHALI)
                                           "हिन्दी  ·  Hindi"
                                       else
                                           "ᱥᱟᱱᱛᱟᱲᱤ  ·  Santhali",
                            text     = state.sourceText,
                            accent   = Teal500,
                            onSpeak  = viewModel::speakSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                AnimatedVisibility(
                    visible = state.translatedText.isNotBlank(),
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        TranslationResultCard(
                            label    = if (state.direction == Direction.HINDI_TO_SANTHALI)
                                           "ᱥᱟᱱᱛᱟᱲᱤ  ·  Santhali"
                                       else
                                           "हिन्दी  ·  Hindi",
                            text     = state.translatedText,
                            phase    = state.phase,
                            onSpeak  = viewModel::speakTranslation,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // ── Error card ─────────────────────────────────────────────
                state.errorMessage?.let { msg ->
                    ErrorCard(msg, onDismiss = viewModel::dismissError)
                }

                // ── RAM info footer ────────────────────────────────────────
                Spacer(Modifier.height(20.dp))
                RamInfoFooter()
            }
        }
    }
}

// ── Input tab row ─────────────────────────────────────────────────────────────

@Composable
private fun InputTabRow(activeTab: InputTab, onSelect: (InputTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(50.dp))
            .padding(4.dp)
    ) {
        InputTab.values().forEach { tab ->
            val selected = activeTab == tab
            val bgColor by animateColorAsState(
                if (selected) Indigo700 else Color.Transparent, label = "tabBg"
            )
            Button(
                onClick  = { onSelect(tab) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape    = RoundedCornerShape(46.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = bgColor,
                    contentColor   = if (selected) Color.White else OnSurfaceDim
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(
                    imageVector         = if (tab == InputTab.SPEECH) Icons.Default.Mic else Icons.Default.Keyboard,
                    contentDescription  = null,
                    modifier            = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = if (tab == InputTab.SPEECH) "Speech" else "Text",
                    fontSize   = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Text input panel ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextInputPanel(
    direction: Direction,
    phase: Phase,
    modelsReady: Boolean,
    onTranslate: (String) -> Unit,
    onReset: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val isTranslating = phase == Phase.TRANSLATING || phase == Phase.SPEAKING

    val placeholder = if (direction == Direction.HINDI_TO_SANTHALI)
        "हिंदी में टाइप करें…  (Type Hindi text)"
    else
        "ᱥᱟᱱᱛᱟᱲᱤ ᱨᱮ ᱚᱞᱮᱭᱟ…  (Type Santhali text)"

    Column {
        // Input box
        OutlinedTextField(
            value         = inputText,
            onValueChange = { inputText = it },
            modifier      = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp),
            placeholder   = {
                Text(placeholder, color = OnSurfaceDim, fontSize = 14.sp)
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction      = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    if (inputText.isNotBlank() && modelsReady) onTranslate(inputText)
                }
            ),
            shape  = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Indigo500,
                unfocusedBorderColor = SurfaceBorder,
                focusedTextColor     = OnSurface,
                unfocusedTextColor   = OnSurface,
                cursorColor          = Indigo500,
                focusedContainerColor   = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            ),
            maxLines = 6,
            enabled  = !isTranslating
        )

        Spacer(Modifier.height(10.dp))

        // Quick example phrases
        Text("Quick test phrases:", color = OnSurfaceDim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))

        val examples = if (direction == Direction.HINDI_TO_SANTHALI)
            listOf("आप कहाँ जा रहे हैं?", "मेरा नाम क्या है?", "पानी दो", "धन्यवाद")
        else
            listOf("ᱤᱫᱤ ᱢᱤ ᱨᱮ ᱮᱢ?", "ᱟᱢ ᱠᱚᱯᱟ", "ᱡᱚᱦᱟᱨ")

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            examples.take(4).forEach { phrase ->
                SuggestionChip(
                    onClick = { inputText = phrase },
                    label   = { Text(phrase, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f, fill = false),
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SurfaceCard,
                        labelColor     = OnSurface
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = SurfaceBorder
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Translate button
        Button(
            onClick  = {
                keyboard?.hide()
                if (inputText.isNotBlank()) onTranslate(inputText)
            },
            enabled  = inputText.isNotBlank() && modelsReady && !isTranslating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Indigo700,
                disabledContainerColor = Color(0xFF2A2A40)
            )
        ) {
            if (isTranslating) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = when (phase) {
                        Phase.TRANSLATING -> "Translating with IndicTrans2…"
                        Phase.SPEAKING    -> "Speaking result…"
                        else              -> "Processing…"
                    },
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Translate", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Clear button (shown when there's input)
        AnimatedVisibility(visible = inputText.isNotBlank()) {
            TextButton(
                onClick  = { inputText = ""; onReset() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp), tint = OnSurfaceDim)
                Spacer(Modifier.width(4.dp))
                Text("Clear", color = OnSurfaceDim, fontSize = 12.sp)
            }
        }
    }
}

// ── App header ────────────────────────────────────────────────────────────────

@Composable
private fun AppHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🇮🇳  Offline Hindi ↔ Santhali",
            fontSize   = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = OnSurface
        )
        Text(
            "Voice Translator  ·  100% On-Device  ·  < 2 GB RAM",
            fontSize = 11.sp,
            color    = OnSurfaceDim
        )
    }
}

// ── Feature badges ────────────────────────────────────────────────────────────

@Composable
private fun FeatureBadges() {
    data class Badge(val icon: ImageVector, val label: String, val color: Color)

    val badges = listOf(
        Badge(Icons.Default.WifiOff,  "No Internet",     Color(0xFFEF5350)),
        Badge(Icons.Default.Memory,   "Lightweight",     Color(0xFFFF8F00)),
        Badge(Icons.Default.Eco,      "Low RAM",         Color(0xFF43A047)),
        Badge(Icons.Default.SyncAlt,  "Both Ways",       Indigo500),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { b ->
            Card(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(
                    Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).background(b.color.copy(.15f), CircleShape),
                        Alignment.Center
                    ) {
                        Icon(b.icon, null, tint = b.color, modifier = Modifier.size(16.dp))
                    }
                    Text(b.label, fontSize = 9.sp, color = OnSurfaceDim,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Language direction bar ────────────────────────────────────────────────────

@Composable
private fun LanguageDirectionBar(direction: Direction, onSwap: () -> Unit) {
    val srcLabel = if (direction == Direction.HINDI_TO_SANTHALI) "हिन्दी\nHindi" else "ᱥᱟᱱᱛᱟᱲᱤ\nSanthali"
    val tgtLabel = if (direction == Direction.HINDI_TO_SANTHALI) "ᱥᱟᱱᱛᱟᱲᱤ\nSanthali" else "हिन्दी\nHindi"
    val srcColor = if (direction == Direction.HINDI_TO_SANTHALI) Color(0xFFFFC107) else Teal500
    val tgtColor = if (direction == Direction.HINDI_TO_SANTHALI) Teal500 else Color(0xFFFFC107)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LangChip(srcLabel, srcColor, Modifier.weight(1f))
        IconButton(onClick = onSwap, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.SwapHoriz, "Swap", tint = OnSurface, modifier = Modifier.size(26.dp))
        }
        LangChip(tgtLabel, tgtColor, Modifier.weight(1f))
    }
}

@Composable
private fun LangChip(label: String, accent: Color, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(.1f))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ── Mic button ────────────────────────────────────────────────────────────────

@Composable
private fun MicButton(
    phase: Phase, isRecording: Boolean, modelsReady: Boolean,
    onPressStart: () -> Unit, onPressRelease: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition("pulse")
    val pulseScale by pulseAnim.animateFloat(1f, 1.2f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), "ps")

    val bgColor by animateColorAsState(when {
        isRecording                  -> Color(0xFFB71C1C)
        phase == Phase.TRANSCRIBING  -> Indigo700
        phase == Phase.TRANSLATING   -> Color(0xFF1B5E20)
        phase == Phase.SPEAKING      -> Teal500
        !modelsReady                 -> Color(0xFF37474F)
        else                         -> Indigo700
    }, tween(300), "micBg")

    Box(Modifier.size(160.dp), Alignment.Center) {
        if (isRecording) {
            repeat(3) { i ->
                val ra = rememberInfiniteTransition("r$i")
                val alpha by ra.animateFloat(.4f - i * .1f, 0f,
                    infiniteRepeatable(tween(1200 + i * 300), RepeatMode.Restart), "ra$i")
                val scale by ra.animateFloat(1f, 1.8f + i * .2f,
                    infiniteRepeatable(tween(1200 + i * 300), RepeatMode.Restart), "rs$i")
                Box(Modifier.size(120.dp).scale(scale)
                    .background(Color(0xFFEF5350).copy(alpha), CircleShape))
            }
        }
        Button(
            onClick        = if (isRecording) onPressRelease else onPressStart,
            enabled        = modelsReady || isRecording,
            shape          = CircleShape,
            colors         = ButtonDefaults.buttonColors(containerColor = bgColor),
            modifier       = Modifier.size(120.dp).scale(if (isRecording) pulseScale else 1f),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                when {
                    isRecording              -> Icons.Default.Stop
                    phase == Phase.TRANSCRIBING -> Icons.Default.HourglassTop
                    phase == Phase.TRANSLATING  -> Icons.Default.Translate
                    phase == Phase.SPEAKING     -> Icons.Default.VolumeUp
                    else                        -> Icons.Default.Mic
                },
                "Mic", Modifier.size(52.dp), Color.White
            )
        }
    }
}

@Composable
private fun PhaseLabel(phase: Phase) {
    val (text, color) = when (phase) {
        Phase.IDLE         -> "Tap mic and speak" to OnSurfaceDim
        Phase.RECORDING    -> "🎙 Listening…" to Color(0xFFEF5350)
        Phase.TRANSCRIBING -> "Converting speech to text…" to Indigo500
        Phase.TRANSLATING  -> "Translating with IndicTrans2…" to Color(0xFF43A047)
        Phase.SPEAKING     -> "🔊 Playing translation…" to Teal500
        Phase.DONE         -> "✓  Done" to Teal500
        Phase.ERROR        -> "Something went wrong" to Color(0xFFEF5350)
    }
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

// ── Result cards ──────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(label: String, text: String, accent: Color,
                       onSpeak: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(.3f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.VolumeUp, "Play", tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text, color = OnSurface, fontSize = 18.sp, lineHeight = 26.sp)
        }
    }
}

@Composable
private fun TranslationResultCard(label: String, text: String, phase: Phase,
                                  onSpeak: () -> Unit, modifier: Modifier) {
    val pulse = rememberInfiniteTransition("tp")
    val alpha by pulse.animateFloat(.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), "ta")
    val borderAlpha = if (phase == Phase.SPEAKING) alpha else 1f

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1E2E)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo500.copy(borderAlpha))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Indigo500, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (phase == Phase.SPEAKING) {
                        Spacer(Modifier.width(8.dp))
                        Text("🔊 Playing…", color = Teal500, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.VolumeUp, "Play", tint = Indigo500, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text, color = OnSurface, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Utility cards ─────────────────────────────────────────────────────────────

@Composable
private fun ModelLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2744))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Indigo500,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Loading AI models…", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Copying models to device storage (one-time setup)", color = OnSurfaceDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1215))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = OnSurfaceDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RamInfoFooter() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RamChip("ASR",         "~250–400 MB", Teal500)
        RamChip("Translation", "~350–600 MB", Indigo500)
        RamChip("TTS",         "~50–150 MB",  Color(0xFFFFC107))
        Column {
            Text("Peak", fontSize = 9.sp, color = OnSurfaceDim)
            Text("≤ 1.5 GB", fontSize = 10.sp, color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RamChip(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 9.sp, color = OnSurfaceDim)
        Text(value, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
