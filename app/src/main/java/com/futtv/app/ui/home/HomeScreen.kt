package com.futtv.app.ui.home

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import java.util.Calendar
import java.util.TimeZone
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.futtv.app.data.model.Channel
import com.futtv.app.data.model.EventStatus
import com.futtv.app.data.model.SportEvent
import com.futtv.app.data.model.StreamServer
import com.futtv.app.data.model.UiState
import com.futtv.app.ui.components.FocusableCard
import com.futtv.app.ui.player.PlayerActivity
import com.futtv.app.ui.theme.*
import com.futtv.app.updater.AppUpdater
import com.futtv.app.updater.UpdateInfo
import kotlin.math.abs

// ─── Paleta determinista por nombre de equipo ─────────────────────────────────
private val teamPalette = listOf(
    Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF2E7D32),
    Color(0xFF6A1B9A), Color(0xFFEF6C00), Color(0xFF00838F),
    Color(0xFF283593), Color(0xFF558B2F), Color(0xFFAD1457),
    Color(0xFF37474F), Color(0xFF4527A0), Color(0xFF00695C),
    Color(0xFF4E342E), Color(0xFF827717), Color(0xFF01579B)
)

private fun teamColor(name: String) = teamPalette[abs(name.hashCode()) % teamPalette.size]

private fun teamInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> words.take(2).joinToString("") { it.first().uppercase() }
        words.size == 1 -> words[0].take(3).uppercase()
        else -> "?"
    }
}

// ─── Filtro de partidos importantes ──────────────────────────────────────────

private val IMPORTANT_LEAGUES = setOf(
    // Argentina
    "liga profesional", "copa de la liga", "copa argentina", "primera division",
    "primera división", "reserva", "torneo federal",
    // Sudamérica
    "copa libertadores", "copa sudamericana", "recopa sudamericana",
    "eliminatorias", "copa america", "copa américa",
    // Europa
    "champions league", "europa league", "conference league",
    "premier league", "la liga", "serie a", "bundesliga", "ligue 1",
    "nations league", "eurocopa", "euro",
    // Mundial
    "world cup", "mundial",
    // Otras ligas importantes
    "brasileirao", "brasileirão", "liga mx", "mls",
    "primera division chile", "primera division colombia",
    "liga betplay", "primera division uruguay", "primera division paraguay",
)

private val IMPORTANT_TEAMS = setOf(
    // Argentina – equipos grandes
    "river plate", "river", "boca juniors", "boca",
    "racing club", "racing", "independiente", "san lorenzo",
    "huracán", "huracan", "vélez", "velez", "estudiantes",
    "lanús", "lanus", "talleres", "belgrano", "godoy cruz",
    "atletico tucuman", "atlético tucumán", "san martin tucuman", "san martín",
    "newells", "newell", "rosario central", "central",
    "banfield", "defensa y justicia", "platense", "sarmiento",
    "barracas central", "riestra", "colon", "colón", "unión", "union",
    "gimnasia", "patronato", "instituto", "tigre", "arsenal",
    "central cordoba", "central córdoba", "aldosivi", "deportivo riestra",
    // Selección argentina y sudamericanas
    "argentina", "brasil", "brazil", "uruguay", "colombia", "chile", "paraguay",
    "peru", "perú", "ecuador", "bolivia", "venezuela",
    // Europa – clubes top
    "real madrid", "barcelona", "atletico madrid", "atlético madrid",
    "manchester city", "manchester united", "liverpool", "chelsea",
    "arsenal fc", "tottenham", "newcastle", "aston villa",
    "psg", "paris saint-germain", "lyon", "marseille",
    "juventus", "inter milan", "inter", "ac milan", "napoli", "roma", "atalanta",
    "bayern munich", "borussia dortmund", "bayer leverkusen", "rb leipzig",
    "ajax", "porto", "benfica", "sporting",
    "celtic", "rangers",
    // Sudamérica – clubes top
    "flamengo", "palmeiras", "atletico mineiro", "fluminense", "botafogo",
    "corinthians", "gremio", "grêmio", "internacional", "santos", "sao paulo",
    "nacional", "penarol", "peñarol", "colo colo",
    "universidad de chile", "olimpia", "cerro porteno", "cerro porteño",
    "libertad", "liga de quito", "independiente del valle",
    "junior", "america de cali", "once caldas",
)

private fun normEv(s: String) = s.lowercase()
    .replace(Regex("[áàäâã]"), "a").replace(Regex("[éèëê]"), "e")
    .replace(Regex("[íìïî]"), "i").replace(Regex("[óòöôõ]"), "o")
    .replace(Regex("[úùüû]"), "u").replace(Regex("[ñ]"), "n")
    .replace(Regex("[^a-z0-9 ]"), " ").trim()

private fun isImportantFootball(event: SportEvent): Boolean {
    // Solo fútbol
    val sport = event.sport.lowercase()
    if (!sport.contains("soccer") && !sport.contains("football") && !sport.contains("futbol"))
        return false

    val league = normEv(event.league)
    val home   = normEv(event.homeTeam)
    val away   = normEv(event.awayTeam)

    // Liga importante
    if (IMPORTANT_LEAGUES.any { league.contains(it) }) return true

    // Equipo importante (local o visitante)
    if (IMPORTANT_TEAMS.any { home.contains(it) || away.contains(it) }) return true

    return false
}

// ─── Detección de "en vivo" por horario ──────────────────────────────────────
private val AR_TZ_EVENTS = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

// Un evento está "en vivo" si ya comenzó (o faltan ≤5 min) y no pasaron 120 min
private fun isCurrentlyLive(event: SportEvent): Boolean {
    if (event.status == EventStatus.FINISHED) return false
    if (event.status == EventStatus.LIVE) return true
    return try {
        val now = Calendar.getInstance(AR_TZ_EVENTS)
        val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val timePart = event.dateTimeUtc.substringAfter("T").take(5)
        val parts = timePart.split(":")
        val eventMins = parts[0].toInt() * 60 + parts[1].toInt()
        nowMins >= (eventMins - 5) && nowMins <= (eventMins + 120)
    } catch (e: Exception) { false }
}

// Clave de ordenamiento: vivo primero → próximos por hora → finalizados al fondo
private fun eventSortKey(event: SportEvent): Int {
    if (isCurrentlyLive(event)) return -10000
    if (event.status == EventStatus.FINISHED) return 10000
    return try {
        val timePart = event.dateTimeUtc.substringAfter("T").take(5)
        val parts = timePart.split(":")
        parts[0].toInt() * 60 + parts[1].toInt()
    } catch (e: Exception) { 9999 }
}

// ─── HomeScreen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val channelsState    by viewModel.channelsState.collectAsState()
    val eventsState      by viewModel.eventsState.collectAsState()
    val selectedChannel  by viewModel.selectedChannel.collectAsState()
    val selectedEvent    by viewModel.selectedEvent.collectAsState()
    val updateInfo       by viewModel.updateInfo.collectAsState()
    val updateProgress   by viewModel.updateProgress.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val upToDate         by viewModel.upToDate.collectAsState()
    val context = LocalContext.current

    var showExitDialog   by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Cuando el chequeo manual confirma que no hay actualización → Toast y reset
    LaunchedEffect(upToDate) {
        if (upToDate) {
            Toast.makeText(context, "Ya tenés la última versión \uD83D\uDC4D", Toast.LENGTH_SHORT).show()
            viewModel.dismissUpToDate()
        }
    }

    // Cuando el chequeo manual encuentra una actualización → abrir diálogo automáticamente
    LaunchedEffect(updateInfo) {
        if (updateInfo != null && !isCheckingUpdate) {
            showUpdateDialog = true
        }
    }

    val firstChannelFocus = remember { FocusRequester() }
    var initialFocusDone  by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var backPressEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        backPressEnabled = true
    }

    BackHandler(enabled = backPressEnabled) {
        showExitDialog = true
    }

    // Refresca eventos cuando la app vuelve al frente (ej: regreso de PlayerActivity)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        // verticalScroll da altura ilimitada a los hijos — ninguna sección puede aplastar a la otra
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AppHeader(
                onRefresh = viewModel::refresh,
                onDownPressed = {},
                updateInfo = updateInfo,
                onUpdateClick = { showUpdateDialog = true },
                isCheckingUpdate = isCheckingUpdate,
                onCheckUpdate = viewModel::checkForUpdateManually
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 60.dp, bottom = 36.dp)
            ) {
                EventsSection(
                    state = eventsState,
                    getLocalTime = viewModel::getLocalTime,
                    onEventSelected = { event -> viewModel.selectEvent(event) }
                )
                Spacer(modifier = Modifier.height(44.dp))
                ChannelsSection(
                    state = channelsState,
                    firstItemFocusRequester = firstChannelFocus,
                    onChannelSelected = viewModel::selectChannel
                )
            }
        }

        selectedEvent?.let { event ->
            EventChannelSelectionDialog(
                event = event,
                onChannelSelected = { channel ->
                    viewModel.dismissEventDetail()
                    viewModel.selectChannel(channel)
                },
                onDismiss = viewModel::dismissEventDetail
            )
        }

        selectedChannel?.let { channel ->
            ServerSelectionDialog(channel = channel, onDismiss = viewModel::dismissChannelDetail)
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                onConfirm = { (context as? Activity)?.finish() },
                onDismiss = { showExitDialog = false }
            )
        }

        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                info = updateInfo!!,
                progress = updateProgress,
                onConfirm = {
                    if (!AppUpdater.canInstallPackages(context)) {
                        AppUpdater.openInstallPermissionSettings(context)
                    } else {
                        viewModel.downloadAndInstall(context)
                    }
                },
                onDismiss = {
                    viewModel.dismissUpdateProgress()
                    showUpdateDialog = false
                }
            )
        }
    }

    LaunchedEffect(channelsState) {
        if (!initialFocusDone && channelsState is UiState.Success) {
            initialFocusDone = true
            delay(300)
            try { firstChannelFocus.requestFocus() } catch (_: Exception) {}
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun AppHeader(
    onRefresh: () -> Unit,
    onDownPressed: () -> Unit,
    updateInfo: UpdateInfo?,
    onUpdateClick: () -> Unit,
    isCheckingUpdate: Boolean = false,
    onCheckUpdate: () -> Unit = {}
) {
    val appCtx = LocalContext.current
    val appVersion = remember {
        runCatching { appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "" }.getOrDefault("")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0A0D17), Color(0x00080910))))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 52.dp, vertical = 16.dp)
        ) {
            // ── Izquierda: logo app ───────────────────────────────────────────
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PrimaryRed, Color(0xFFAA00FF)),
                                start = Offset(0f, 0f), end = Offset(44f, 44f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SportsSoccer, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text("FutTV", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp)
                    if (appVersion.isNotEmpty()) {
                        Text(
                            text = "v$appVersion",
                            color = TextPrimary.copy(alpha = 0.28f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            // ── Centro: reloj ─────────────────────────────────────────────────
            LiveClock(modifier = Modifier.align(Alignment.Center))

            // ── Derecha: update + badge EN VIVO + refresh ────────────────────
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (updateInfo != null) {
                    UpdateAvailableBadge(
                        versionName = updateInfo.versionName,
                        onClick = onUpdateClick
                    )
                }
                PulsingLiveBadge()
                // Botón discreto para chequear actualizaciones manualmente
                UpdateCheckButton(
                    isChecking = isCheckingUpdate,
                    onClick = onCheckUpdate
                )
                // Botón de refresh de eventos (existente, sin cambios)
                FocusableCard(
                    onClick = onRefresh,
                    modifier = Modifier.size(40.dp).focusProperties {
                        down = FocusRequester.Default
                        up = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    cornerRadius = 10.dp
                ) { focused ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Refresh, "Actualizar eventos", tint = if (focused) FocusBorder else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─── Botón de actualización disponible ───────────────────────────────────────

@Composable
private fun UpdateAvailableBadge(versionName: String, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "update_pulse")
    val borderAlpha by inf.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        label = "update_border"
    )
    val updateColor = Color(0xFF4CAF50)
    FocusableCard(
        onClick = onClick,
        modifier = Modifier.height(40.dp).focusProperties {
            down = FocusRequester.Default
            up = FocusRequester.Cancel
        },
        cornerRadius = 10.dp
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    if (focused) updateColor
                    else updateColor.copy(alpha = 0.14f),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, updateColor.copy(alpha = if (focused) 1f else borderAlpha), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                Icons.Filled.SystemUpdate, null,
                tint = if (focused) Color.White else updateColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "v$versionName disponible",
                color = if (focused) Color.White else updateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ─── Botón discreto de chequeo manual de actualizaciones ─────────────────────

@Composable
private fun UpdateCheckButton(isChecking: Boolean, onClick: () -> Unit) {
    val checkColor = Color(0xFF546E7A)   // gris azulado, discreto
    FocusableCard(
        onClick = { if (!isChecking) onClick() },
        modifier = Modifier
            .size(40.dp)
            .focusProperties {
                down = FocusRequester.Default
                up = FocusRequester.Cancel
            },
        cornerRadius = 10.dp
    ) { focused ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (focused) checkColor.copy(alpha = 0.22f) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = if (focused) FocusBorder else checkColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Buscar actualización",
                    tint = if (focused) FocusBorder else checkColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Dialog de actualización ──────────────────────────────────────────────────

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    progress: Float?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    val isDownloading = progress != null && progress >= 0f && progress <= 1f
    val isError = progress == -1f
    val updateColor = Color(0xFF4CAF50)

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        BackHandler(enabled = !isDownloading) { onDismiss() }
        Surface(
            modifier = Modifier.width(420.dp).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D1120),
            border = BorderStroke(1.dp, updateColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(updateColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = updateColor, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Actualización disponible",
                    color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Versión ${info.versionName}",
                    color = updateColor, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            info.changelog,
                            color = TextSecondary, fontSize = 13.sp,
                            textAlign = TextAlign.Center, lineHeight = 19.sp
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                when {
                    isDownloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { progress!! },
                                modifier = Modifier.fillMaxWidth(),
                                color = updateColor,
                                trackColor = SurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Descargando... ${(progress!! * 100).toInt()}%",
                                color = TextMuted, fontSize = 13.sp
                            )
                        }
                    }
                    isError -> {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryRed.copy(alpha = 0.12f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Error al descargar. Verificá tu conexión e intentá de nuevo.",
                                color = PrimaryRed, fontSize = 13.sp, textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FocusableCard(onDismiss, Modifier.weight(1f).height(48.dp), 10.dp) { focused ->
                                Box(Modifier.fillMaxSize().background(if (focused) SurfaceVariant else Color.Transparent), Alignment.Center) {
                                    Text("CANCELAR", color = if (focused) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            FocusableCard(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f).height(48.dp).focusRequester(confirmFocus),
                                cornerRadius = 10.dp
                            ) { focused ->
                                Box(Modifier.fillMaxSize().background(if (focused) updateColor else updateColor.copy(alpha = 0.3f)), Alignment.Center) {
                                    Text("REINTENTAR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FocusableCard(onDismiss, Modifier.weight(1f).height(48.dp), 10.dp) { focused ->
                                Box(Modifier.fillMaxSize().background(if (focused) SurfaceVariant else Color.Transparent), Alignment.Center) {
                                    Text("CANCELAR", color = if (focused) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            FocusableCard(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f).height(48.dp).focusRequester(confirmFocus),
                                cornerRadius = 10.dp
                            ) { focused ->
                                Box(Modifier.fillMaxSize().background(if (focused) updateColor else updateColor.copy(alpha = 0.3f)), Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("INSTALAR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!isDownloading) {
        LaunchedEffect(isError) {
            try { confirmFocus.requestFocus() } catch (_: Exception) {}
        }
    }
}

// ─── Reloj en tiempo real ─────────────────────────────────────────────────────

@Composable
private fun LiveClock(modifier: Modifier = Modifier) {
    var cal by remember { mutableStateOf(Calendar.getInstance()) }

    // Actualiza cada minuto sincronizado al cambio de minuto real
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val secondsUntilNextMinute = 60 - now.get(Calendar.SECOND)
            delay(secondsUntilNextMinute * 1000L)
            cal = Calendar.getInstance()
        }
    }

    val hour   = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
    val minute = String.format("%02d", cal.get(Calendar.MINUTE))
    val day    = cal.get(Calendar.DAY_OF_MONTH)
    val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> "Lunes"
        Calendar.TUESDAY   -> "Martes"
        Calendar.WEDNESDAY -> "Miércoles"
        Calendar.THURSDAY  -> "Jueves"
        Calendar.FRIDAY    -> "Viernes"
        Calendar.SATURDAY  -> "Sábado"
        else               -> "Domingo"
    }
    val monthName = when (cal.get(Calendar.MONTH)) {
        Calendar.JANUARY   -> "Enero"
        Calendar.FEBRUARY  -> "Febrero"
        Calendar.MARCH     -> "Marzo"
        Calendar.APRIL     -> "Abril"
        Calendar.MAY       -> "Mayo"
        Calendar.JUNE      -> "Junio"
        Calendar.JULY      -> "Julio"
        Calendar.AUGUST    -> "Agosto"
        Calendar.SEPTEMBER -> "Septiembre"
        Calendar.OCTOBER   -> "Octubre"
        Calendar.NOVEMBER  -> "Noviembre"
        else               -> "Diciembre"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hora grande con separador
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hour,
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Text(
                text = ":",
                color = PrimaryRed,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            Text(
                text = minute,
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
        }
        // Fecha debajo
        Text(
            text = "$dayName $day de $monthName",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun PulsingLiveBadge() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val alpha by inf.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(PrimaryRed.copy(alpha = 0.14f))
            .border(1.dp, PrimaryRed.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(PrimaryRed.copy(alpha = alpha)))
        Spacer(modifier = Modifier.width(7.dp))
        Text("EN VIVO", color = PrimaryRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
    }
}

// ─── Sección Eventos ─────────────────────────────────────────────────────────

@Composable
private fun EventsSection(
    state: UiState<List<SportEvent>>,
    getLocalTime: (String) -> String,
    onEventSelected: (SportEvent) -> Unit
) {
    Column {
        SectionHeader("EVENTOS DE HOY", Icons.Filled.Event)
        Spacer(modifier = Modifier.height(18.dp))
        when (state) {
            is UiState.Loading ->
                Box(modifier = Modifier.height(200.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PrimaryRed, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Cargando eventos...", color = TextMuted, fontSize = 14.sp)
                    }
                }
            is UiState.Error ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                        .clip(RoundedCornerShape(12.dp)).background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.message, color = TextMuted, fontSize = 13.sp)
                    }
                }
            is UiState.Success -> {
                val filteredEvents = remember(state.data) {
                    val important = state.data.filter { isImportantFootball(it) }
                    // Si el filtro deja pocos eventos, mostrar todos de fútbol como fallback
                    val result = if (important.size >= 3) important else
                        state.data.filter { it.sport.contains("soccer", ignoreCase = true) || it.sport.contains("football", ignoreCase = true) }
                    result.sortedBy { eventSortKey(it) }
                }
                val eventsListState = rememberLazyListState()
                LazyRow(
                    modifier = Modifier.height(250.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                    state = eventsListState,
                    flingBehavior = rememberSnapFlingBehavior(eventsListState)
                ) {
                    items(filteredEvents, key = { it.id }) { event ->
                        val isLive = isCurrentlyLive(event)
                        EventCard(event, getLocalTime(event.dateTimeUtc), isLive) { onEventSelected(event) }
                    }
                }
            }
        }
    }
}

// ─── Tarjeta de Evento ────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: SportEvent, localTime: String, isLive: Boolean, onClick: () -> Unit) {
    val liveAnim = rememberInfiniteTransition(label = "live_card")
    val liveBorderAlpha by liveAnim.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "live_border"
    )
    val liveBgAlpha by liveAnim.animateFloat(
        initialValue = 0.05f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse),
        label = "live_bg"
    )

    Box(
        modifier = Modifier
            .width(380.dp).height(250.dp)
            .then(
                if (isLive) Modifier.border(2.dp, PrimaryRed.copy(alpha = liveBorderAlpha), RoundedCornerShape(18.dp))
                else Modifier
            )
    ) {
        if (isLive) {
            Box(modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(PrimaryRed.copy(alpha = liveBgAlpha)))
        }
    FocusableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 18.dp
    ) { isFocused ->
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Fondo ─────────────────────────────────────────────────────────
            val sportBg = sportColor(event.sport)
            if (event.thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = event.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x99000000), Color(0xEE000000)),
                        startY = 0f, endY = Float.POSITIVE_INFINITY
                    )
                ))
            } else {
                // Fondo con gradiente sutil basado en deporte
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(
                            sportBg.copy(alpha = if (isFocused) 0.22f else 0.14f),
                            Background.copy(alpha = 0.98f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                ))
                // Icono decorativo translucido
                Icon(sportIcon(event.sport), null,
                    tint = sportBg.copy(alpha = if (isFocused) 0.10f else 0.05f),
                    modifier = Modifier.size(200.dp).align(Alignment.CenterEnd).offset(x = 30.dp))
                // Línea de acento izquierda
                Box(
                    modifier = Modifier.width(3.dp).fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.verticalGradient(listOf(
                                Color.Transparent, sportBg.copy(alpha = if (isFocused) 0.8f else 0.4f), Color.Transparent
                            ))
                        )
                )
            }

            // ── Contenido ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Fila top: liga + estado/hora ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (event.leagueBadgeUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(event.leagueBadgeUrl).crossfade(200).build(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(sportIcon(event.sport), null,
                                tint = sportBg.copy(alpha = 0.9f),
                                modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(event.league, color = TextSecondary, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    EventStatusBadge(event, localTime, isLive)
                }

                // ── Centro: escudos + marcador / VS ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(120.dp)
                    ) {
                        TeamDisplay(name = event.homeTeam, badgeUrl = event.homeTeamBadgeUrl, size = 72.dp, focused = isFocused)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(event.homeTeam, color = if (isFocused) TextPrimary else TextSecondary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    }

                    // VS / Marcador central
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if ((event.status == EventStatus.LIVE || event.status == EventStatus.FINISHED)
                            && event.homeScore != null && event.awayScore != null) {
                            ScoreDisplay(event.homeScore, event.awayScore, event.status == EventStatus.LIVE)
                        } else {
                            Text("VS",
                                color = if (isFocused) Color.White.copy(alpha = 0.7f) else TextMuted,
                                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(120.dp)
                    ) {
                        TeamDisplay(name = event.awayTeam, badgeUrl = event.awayTeamBadgeUrl, size = 72.dp, focused = isFocused)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(event.awayTeam, color = if (isFocused) TextPrimary else TextSecondary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    }
                }

                // ── Fila bottom: chips de canales ─────────────────────────────
                if (event.channels.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        event.channels.take(3).forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(ch.displayColor.copy(alpha = if (isFocused) 0.9f else 0.6f))
                                    .border(0.5.dp, ch.displayColor.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Tv, null, tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(9.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(ch.name, color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }
    }
    } // cierra Box externo (borde live)
}

// ─── Badge de estado (EN VIVO / hora / FINALIZADO) ───────────────────────────

@Composable
private fun EventStatusBadge(event: SportEvent, localTime: String, isLive: Boolean) {
    val inf = rememberInfiniteTransition(label = "badge")
    val dotAlpha by inf.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot"
    )
    val badgeScale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )

    when {
        isLive -> {
            // Badge grande y pulsante — "bastante notable"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .scale(badgeScale)
                    .clip(RoundedCornerShape(7.dp))
                    .background(PrimaryRed)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = dotAlpha)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("EN VIVO", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }
        event.status == EventStatus.FINISHED ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(TextMuted.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("FINALIZADO", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        else ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(PrimaryRed.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(localTime, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
    }
}

// ─── Marcador ─────────────────────────────────────────────────────────────────

@Composable
private fun ScoreDisplay(homeScore: Int, awayScore: Int, isLive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$homeScore",
            color = if (isLive) PrimaryRed else TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text("—", color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "$awayScore",
            color = if (isLive) PrimaryRed else TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ─── Escudo del equipo: logo real con fallback a círculo de iniciales ─────────

@Composable
private fun TeamDisplay(name: String, badgeUrl: String, size: Dp, focused: Boolean) {
    // remember(badgeUrl) resetea el estado de error cuando cambia el evento
    var imageError by remember(badgeUrl) { mutableStateOf(false) }
    val context = LocalContext.current

    if (badgeUrl.isNotBlank() && !imageError) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(badgeUrl)
                .crossfade(300)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
            onError = { imageError = true }   // si falla → muestra el círculo
        )
    } else {
        TeamBadge(name = name, size = size, focused = focused)
    }
}

@Composable
private fun TeamBadge(name: String, size: Dp, focused: Boolean) {
    val color    = teamColor(name)
    val initials = teamInitials(name)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = if (focused) 0.28f else 0.18f))
            .border(
                width = if (focused) 2.dp else 1.5.dp,
                color = color.copy(alpha = if (focused) 0.75f else 0.45f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = color, fontSize = (size.value * 0.27f).sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}

// ─── Sección Canales ──────────────────────────────────────────────────────────

@Composable
private fun ChannelsSection(
    state: UiState<List<Channel>>,
    firstItemFocusRequester: FocusRequester,
    onChannelSelected: (Channel) -> Unit
) {
    Column {
        SectionHeader("CANALES EN VIVO", Icons.Filled.LiveTv)
        Spacer(modifier = Modifier.height(18.dp))
        when (state) {
            is UiState.Loading ->
                Box(modifier = Modifier.height(118.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PrimaryRed, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Cargando canales...", color = TextMuted, fontSize = 14.sp)
                    }
                }
            is UiState.Error -> Text(state.message, color = TextMuted, fontSize = 13.sp)
            is UiState.Success -> {
                val lazyListState = rememberLazyListState()
                LazyRow(
                    modifier = Modifier.height(175.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
                    state = lazyListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState),
                    reverseLayout = false
                ) {
                    items(state.data, key = { it.id }) { channel ->
                        val isFirst = state.data.firstOrNull()?.id == channel.id
                        ChannelCard(
                            channel = channel,
                            modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                            onClick = { onChannelSelected(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableCard(
        onClick = onClick,
        modifier = modifier.width(155.dp).height(175.dp),
        cornerRadius = 16.dp
    ) { isFocused ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Fondo sutil con color del canal
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        channel.displayColor.copy(alpha = if (isFocused) 0.28f else 0.10f),
                        Color.Transparent
                    )
                )
            ))
            // Línea de acento superior
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(3.dp)
                    .background(channel.displayColor.copy(alpha = if (isFocused) 1f else 0.45f))
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo del canal en círculo
                var logoError by remember(channel.logoUrl) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (isFocused) 0.15f else 0.28f))
                        .border(1.5.dp, channel.displayColor.copy(alpha = if (isFocused) 0.6f else 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank() && !logoError) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(channel.logoUrl).crossfade(300).build(),
                            contentDescription = channel.name,
                            modifier = Modifier.size(54.dp).padding(4.dp),
                            contentScale = ContentScale.Fit,
                            onError = { logoError = true }
                        )
                    } else {
                        Text(channel.name.take(3).uppercase(), color = channel.displayColor,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }

                // Nombre + servidores
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(channel.name,
                        color = if (isFocused) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (channel.servers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${channel.servers.size} servidores",
                            color = if (isFocused) AccentBlueBright else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Dialog Selección de Canal (desde Evento) ─────────────────────────────────

@Composable
private fun EventChannelSelectionDialog(
    event: SportEvent,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    val firstChannelFocus = remember { FocusRequester() }
    val channels = event.channels.ifEmpty {
        com.futtv.app.data.model.ChannelRegistry.getChannelsForLeague(event.league)
            .ifEmpty { com.futtv.app.data.model.ChannelRegistry.channels }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0C0F1C),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column {
                // ── Header: partido con escudos ───────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(
                                PrimaryRed.copy(alpha = 0.12f),
                                Color.Transparent
                            ))
                        )
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                ) {
                    Column {
                        // Liga
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SportsSoccer, null, tint = PrimaryRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(event.league, color = TextSecondary, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        // Escudos + nombres
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                TeamDisplay(event.homeTeam, event.homeTeamBadgeUrl, 52.dp, false)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(event.homeTeam, color = TextPrimary, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
                            }
                            Text("VS", color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                TeamDisplay(event.awayTeam, event.awayTeamBadgeUrl, 52.dp, false)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(event.awayTeam, color = TextPrimary, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Elegí un canal para ver el partido",
                            color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.06f)))

                // ── Lista de canales ──────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    channels.forEachIndexed { index, channel ->
                        FocusableCard(
                            onClick = { onChannelSelected(channel) },
                            modifier = (if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier)
                                .fillMaxWidth().height(64.dp),
                            cornerRadius = 12.dp
                        ) { isFocused ->
                            Row(
                                modifier = Modifier.fillMaxSize()
                                    .background(
                                        if (isFocused)
                                            Brush.horizontalGradient(listOf(
                                                channel.displayColor.copy(alpha = 0.80f),
                                                channel.displayColor.copy(alpha = 0.50f)
                                            ))
                                        else
                                            Brush.horizontalGradient(listOf(SurfaceVariant, SurfaceVariant))
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Logo del canal
                                var logoErr by remember(channel.logoUrl) { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = if (isFocused) 0.2f else 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (channel.logoUrl.isNotBlank() && !logoErr) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(channel.logoUrl).crossfade(200).build(),
                                            contentDescription = channel.name,
                                            modifier = Modifier.size(30.dp).padding(2.dp),
                                            contentScale = ContentScale.Fit,
                                            onError = { logoErr = true }
                                        )
                                    } else {
                                        Text(channel.name.take(2).uppercase(),
                                            color = if (isFocused) Color.White else channel.displayColor,
                                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(channel.name,
                                        color = if (isFocused) Color.White else TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (channel.servers.isNotEmpty()) {
                                        Text("${channel.servers.size} servidores",
                                            color = if (isFocused) Color.White.copy(0.7f) else TextMuted,
                                            fontSize = 11.sp)
                                    }
                                }
                                if (isFocused) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("VER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                        if (index < channels.size - 1) Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    FocusableCard(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp), cornerRadius = 10.dp) { focused ->
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                                .background(if (focused) SurfaceVariant else Color.Transparent)) {
                            Text("CANCELAR", color = if (focused) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { firstChannelFocus.requestFocus() } catch (_: Exception) {} }
}

// ─── Dialog Servidores ────────────────────────────────────────────────────────

@Composable
private fun ServerSelectionDialog(channel: Channel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val firstServerFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier.fillMaxWidth(0.42f).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D1120),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column {
                // Cabecera
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(channel.displayColor.copy(alpha = 0.25f), Color.Transparent)))
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var logoErr by remember(channel.logoUrl) { mutableStateOf(false) }
                        Box(
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                                .background(channel.displayColor.copy(alpha = 0.18f))
                                .border(1.dp, channel.displayColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (channel.logoUrl.isNotBlank() && !logoErr) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(channel.logoUrl).crossfade(300).build(),
                                    contentDescription = channel.name,
                                    modifier = Modifier.size(44.dp).padding(4.dp),
                                    contentScale = ContentScale.Fit,
                                    onError = { logoErr = true }
                                )
                            } else {
                                Text(channel.name.take(2).uppercase(), color = channel.displayColor,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(channel.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text("Seleccioná un servidor para reproducir", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(DividerColor))
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                    if (channel.servers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Canal no disponible", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Este canal no tiene stream gratuito disponible",
                                    color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FocusableCard(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), cornerRadius = 10.dp) { focused ->
                            Box(contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().background(if (focused) SurfaceVariant else Color.Transparent)) {
                                Text("CERRAR", color = if (focused) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        return@Column
                    }
                    val servers = channel.servers
                    servers.forEachIndexed { index, server ->
                        ServerButton(
                            server = server,
                            index = index,
                            modifier = if (index == 0) Modifier.focusRequester(firstServerFocus) else Modifier,
                            onClick = {
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java).apply {
                                        putExtra(PlayerActivity.EXTRA_URL, server.url)
                                        putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.name)
                                        putExtra(PlayerActivity.EXTRA_SERVER_LABEL, server.label)
                                        putExtra(PlayerActivity.EXTRA_SERVERS_LABELS, servers.map { it.label }.toTypedArray())
                                        putExtra(PlayerActivity.EXTRA_SERVERS_URLS, servers.map { it.url }.toTypedArray())
                                        putExtra(PlayerActivity.EXTRA_SERVER_INDEX, index)
                                    }
                                )
                                onDismiss()
                            }
                        )
                        if (index < servers.size - 1) Spacer(modifier = Modifier.height(9.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FocusableCard(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), cornerRadius = 10.dp) { focused ->
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().background(if (focused) SurfaceVariant else Color.Transparent)) {
                            Text("CANCELAR", color = if (focused) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { firstServerFocus.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun ServerButton(server: StreamServer, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(60.dp), cornerRadius = 11.dp) { isFocused ->
        Row(
            modifier = Modifier.fillMaxSize()
                .background(if (isFocused)
                    Brush.horizontalGradient(listOf(PrimaryRed.copy(alpha = 0.85f), Color(0xFF8A0000)))
                else Brush.horizontalGradient(listOf(SurfaceVariant, SurfaceVariant)))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(if (isFocused) Color.White.copy(alpha = 0.18f) else CardBackground),
                contentAlignment = Alignment.Center) {
                Text("${index + 1}", color = if (isFocused) Color.White else TextSecondary,
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(server.label, color = if (isFocused) Color.White else TextPrimary,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (isFocused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("REPRODUCIR", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

// ─── Dialog de salida ─────────────────────────────────────────────────────────

@Composable
private fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier.width(360.dp).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D1120),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(PrimaryRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = PrimaryRed, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("¿Salir de FutTV?", color = TextPrimary, fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("¿Querés cerrar la aplicación?", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusableCard(onDismiss, Modifier.weight(1f).height(48.dp).focusRequester(cancelFocus), 10.dp) { focused ->
                        Box(
                            modifier = Modifier.fillMaxSize().background(if (focused) SurfaceVariant else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("CANCELAR", color = if (focused) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    FocusableCard(onConfirm, Modifier.weight(1f).height(48.dp), 10.dp) { focused ->
                        Box(
                            modifier = Modifier.fillMaxSize().background(if (focused) PrimaryRed else PrimaryRed.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SALIR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { cancelFocus.requestFocus() } catch (_: Exception) {} }
}

// ─── Utilidades compartidas ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(22.dp).background(PrimaryRed, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(10.dp))
        Icon(icon, null, tint = PrimaryRed, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 2.sp)
    }
}

private fun sportIcon(sport: String): ImageVector = when {
    sport.contains("soccer", ignoreCase = true) ||
    sport.contains("football", ignoreCase = true) && !sport.contains("american", ignoreCase = true)
                                                -> Icons.Filled.SportsSoccer
    sport.contains("basketball", ignoreCase = true) -> Icons.Filled.SportsBasketball
    sport.contains("american", ignoreCase = true)   -> Icons.Filled.SportsFootball
    sport.contains("tennis", ignoreCase = true)     -> Icons.Filled.SportsTennis
    sport.contains("rugby", ignoreCase = true)      -> Icons.Filled.SportsRugby
    sport.contains("baseball", ignoreCase = true)   -> Icons.Filled.SportsBaseball
    sport.contains("motor", ignoreCase = true) ||
    sport.contains("formula", ignoreCase = true)    -> Icons.Filled.SportsMotorsports
    else -> Icons.Filled.SportsSoccer
}

private fun sportColor(sport: String): Color = when {
    sport.contains("soccer", ignoreCase = true) ||
    sport.contains("football", ignoreCase = true) && !sport.contains("american", ignoreCase = true)
                                                -> Color(0xFF1565C0)
    sport.contains("basketball", ignoreCase = true) -> Color(0xFFEF6C00)
    sport.contains("american", ignoreCase = true)   -> Color(0xFF2E7D32)
    sport.contains("tennis", ignoreCase = true)     -> Color(0xFFF9A825)
    sport.contains("rugby", ignoreCase = true)      -> Color(0xFF6A1B9A)
    sport.contains("motor", ignoreCase = true) ||
    sport.contains("formula", ignoreCase = true)    -> Color(0xFFB71C1C)
    else -> Color(0xFF1565C0)
}
