package com.example.a3tuz

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.SocketManager
import kotlinx.coroutines.delay
import org.json.JSONObject

enum class AviatorGameStatus { WAITING, FLYING, CRASHED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AviatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val socket = remember { SocketManager.getSocket() }
    var serverCrashPoint by remember { mutableFloatStateOf(1.5f) }

    // Geri düyməsi funksiyası
    val handleBack = {
        onBack()
    }

    // Sistem geri düyməsini idarə etmək
    BackHandler {
        handleBack()
    }

    // Tam ekran rejimi
    DisposableEffect(Unit) {
        val window = (context as android.app.Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { 
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Səs obyektləri - Tip təyin edirik ki, xəta verməsin
    val flyMp = remember { 
        try { 
            MediaPlayer.create(context, R.raw.aviator)?.apply { isLooping = true } 
        } catch (e: Exception) { 
            null as MediaPlayer? 
        }
    }
    val crashMp = remember { 
        try { 
            MediaPlayer.create(context, R.raw.uduzmaq) 
        } catch (e: Exception) { 
            null as MediaPlayer? 
        }
    }

    LaunchedEffect(Unit) {
        socket?.on("aviatorPoint") { args ->
            val point = when (val data = args.getOrNull(0)) {
                is Number -> data.toFloat()
                is String -> data.toFloatOrNull() ?: 1.1f
                else -> 1.1f
            }
            serverCrashPoint = point
        }
        socket?.on("balance") { args ->
            val b = (args.getOrNull(0) as? Number)?.toDouble() ?: AppConfig.userBalance
            AppConfig.userBalance = b
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            socket?.off("aviatorPoint")
            socket?.off("balance")
            flyMp?.stop(); flyMp?.release()
            crashMp?.stop(); crashMp?.release()
        }
    }

    var multiplier by remember { mutableStateOf(1.00f) }
    var gameStatus by remember { mutableStateOf(AviatorGameStatus.WAITING) }
    var history by remember { mutableStateOf(listOf<Float>()) }
    
    // Bet States
    var betAmount1 by remember { mutableStateOf(0.20f) }; var isBetActive1 by remember { mutableStateOf(false) }
    var hasCashedOut1 by remember { mutableStateOf(false) }; var winAmount1 by remember { mutableStateOf(0f) }
    var isAutoBetEnabled1 by remember { mutableStateOf(false) }; var isAutoCashOutEnabled1 by remember { mutableStateOf(false) }; var autoCashOutValue1 by remember { mutableStateOf(2.00f) }

    var betAmount2 by remember { mutableStateOf(0.20f) }; var isBetActive2 by remember { mutableStateOf(false) }
    var hasCashedOut2 by remember { mutableStateOf(false) }; var winAmount2 by remember { mutableStateOf(0f) }
    var isAutoBetEnabled2 by remember { mutableStateOf(false) }; var isAutoCashOutEnabled2 by remember { mutableStateOf(false) }; var autoCashOutValue2 by remember { mutableStateOf(2.00f) }

    var countdown by remember { mutableStateOf(5) }
    var showFullHistory by remember { mutableStateOf(false) }

    fun sendBetToServer(amount: Float, betIdx: Int) {
        socket?.emit("aviatorBet", JSONObject().apply { put("username", AppConfig.currentUsername); put("amount", amount.toDouble()); put("betIdx", betIdx) })
        // Balansdan dərhal çıxılması üçün optimistik yeniləmə
        AppConfig.userBalance = (AppConfig.userBalance - amount.toDouble()).coerceAtLeast(0.0)
    }

    fun sendCashOutToServer(currentMultiplier: Float, betIdx: Int) {
        socket?.emit("aviatorCashOut", JSONObject().apply { put("username", AppConfig.currentUsername); put("multiplier", currentMultiplier.toDouble()); put("betIdx", betIdx) })
    }

    LaunchedEffect(gameStatus) {
        when (gameStatus) {
            AviatorGameStatus.WAITING -> {
                if (flyMp?.isPlaying == true) { flyMp.pause(); flyMp.seekTo(0) }
                socket?.emit("getAviatorPoint")
                delay(500)
                if (isAutoBetEnabled1 && AppConfig.userBalance >= betAmount1 && !isBetActive1) { sendBetToServer(betAmount1, 1); isBetActive1 = true }
                if (isAutoBetEnabled2 && AppConfig.userBalance >= betAmount2 && !isBetActive2) { sendBetToServer(betAmount2, 2); isBetActive2 = true }
                countdown = 5
                while (countdown > 0) { delay(1000); countdown-- }
                gameStatus = AviatorGameStatus.FLYING
            }
            AviatorGameStatus.FLYING -> {
                try { flyMp?.start() } catch (e: Exception) { e.printStackTrace() }
                multiplier = 1.00f
                while (gameStatus == AviatorGameStatus.FLYING) {
                    delay(20); multiplier += 0.0016f * multiplier
                    if (isBetActive1 && !hasCashedOut1 && isAutoCashOutEnabled1 && multiplier >= autoCashOutValue1) {
                        winAmount1 = betAmount1 * autoCashOutValue1; sendCashOutToServer(autoCashOutValue1, 1); hasCashedOut1 = true
                    }
                    if (isBetActive2 && !hasCashedOut2 && isAutoCashOutEnabled2 && multiplier >= autoCashOutValue2) {
                        winAmount2 = betAmount2 * autoCashOutValue2; sendCashOutToServer(autoCashOutValue2, 2); hasCashedOut2 = true
                    }
                    if (multiplier >= serverCrashPoint) {
                        gameStatus = AviatorGameStatus.CRASHED
                        history = (listOf(serverCrashPoint) + history).take(100)
                    }
                }
            }
            AviatorGameStatus.CRASHED -> {
                if (flyMp?.isPlaying == true) flyMp.pause()
                try {
                    crashMp?.seekTo(0)
                    crashMp?.start()
                } catch (e: Exception) { e.printStackTrace() }
                delay(3000)
                isBetActive1 = false; hasCashedOut1 = false; winAmount1 = 0f
                isBetActive2 = false; hasCashedOut2 = false; winAmount2 = 0f
                gameStatus = AviatorGameStatus.WAITING
            }
        }
    }

    Scaffold(
        topBar = { 
            CenterAlignedTopAppBar(
                title = { Text("AVIATOR", color = Color.Red, fontWeight = FontWeight.Black) }, 
                navigationIcon = { 
                    IconButton(onClick = handleBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) 
                    } 
                }, 
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1A1A1A))
            ) 
        },
        containerColor = Color(0xFF1A1A1A)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Balans: ${"%.2f".format(AppConfig.userBalance)} AZN", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (history.size >= 5) {
                    val analysis = calculateAdvancedAnalysis(history)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalysisBadge("PROQNOZ", "%.2fx".format(analysis.prediction), Color.Cyan)
                        AnalysisBadge("GÜVƏN", "%${analysis.confidence}", if(analysis.confidence > 80) Color.Green else Color.Yellow)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val displayHistory = history.take(10)
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                    items(displayHistory.size) { index -> HistoryItem(displayHistory[index], history.size - index) }
                }
                IconButton(onClick = { showFullHistory = true }) { Text("▼", color = Color.Gray, fontSize = 12.sp) }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF000000), RoundedCornerShape(16.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                if (gameStatus == AviatorGameStatus.FLYING || gameStatus == AviatorGameStatus.CRASHED) {
                    GameAnimation(multiplier, gameStatus)
                    Text(text = "${"%.2f".format(multiplier)}x", color = if (gameStatus == AviatorGameStatus.CRASHED) Color.Red else if (multiplier < 2f) Color(0xFF3498DB) else if (multiplier < 10f) Color(0xFF9B59B6) else Color(0xFFF06292), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NÖVBƏTİ RAUND GÖZLƏNİLİR", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("$countdown", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BetControl(
                    modifier = Modifier.weight(1f), amount = betAmount1, onAmountChange = { betAmount1 = it },
                    isBetActive = isBetActive1, hasCashedOut = hasCashedOut1, winAmount = winAmount1,
                    isAutoBetEnabled = isAutoBetEnabled1, onAutoBetToggle = { isAutoBetEnabled1 = it },
                    isAutoCashOutEnabled = isAutoCashOutEnabled1, onAutoCashOutToggle = { isAutoCashOutEnabled1 = it },
                    autoCashOutValue = autoCashOutValue1, onAutoCashOutValueChange = { autoCashOutValue1 = it },
                    gameStatus = gameStatus,
                    onBet = { if (AppConfig.userBalance >= betAmount1 && !isBetActive1) { sendBetToServer(betAmount1, 1); isBetActive1 = true } },
                    onCashOut = { winAmount1 = betAmount1 * multiplier; sendCashOutToServer(multiplier, 1); hasCashedOut1 = true },
                    multiplier = multiplier
                )
                BetControl(
                    modifier = Modifier.weight(1f), amount = betAmount2, onAmountChange = { betAmount2 = it },
                    isBetActive = isBetActive2, hasCashedOut = hasCashedOut2, winAmount = winAmount2,
                    isAutoBetEnabled = isAutoBetEnabled2, onAutoBetToggle = { isAutoBetEnabled2 = it },
                    isAutoCashOutEnabled = isAutoCashOutEnabled2, onAutoCashOutToggle = { isAutoCashOutEnabled2 = it },
                    autoCashOutValue = autoCashOutValue2, onAutoCashOutValueChange = { autoCashOutValue2 = it },
                    gameStatus = gameStatus,
                    onBet = { if (AppConfig.userBalance >= betAmount2 && !isBetActive2) { sendBetToServer(betAmount2, 2); isBetActive2 = true } },
                    onCashOut = { winAmount2 = betAmount2 * multiplier; sendCashOutToServer(multiplier, 2); hasCashedOut2 = true },
                    multiplier = multiplier
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    if (showFullHistory) {
        AlertDialog(
            onDismissRequest = { showFullHistory = false },
            confirmButton = { TextButton(onClick = { showFullHistory = false }) { Text("BAĞLA", color = Color.Red) } },
            title = { Text("RAUND TARİXÇƏSİ", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF1A1A1A),
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history.size) { index -> HistoryItem(history[index], history.size - index) }
                    }
                }
            }
        )
    }
}

@Composable
fun HistoryItem(multiplier: Float, index: Int? = null) {
    Surface(modifier = Modifier.padding(end = 4.dp), color = Color(0xFF2D2D2D), shape = RoundedCornerShape(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (index != null) Text(text = "#$index", color = Color.Gray, fontSize = 7.sp)
            Text(text = "${"%.2f".format(multiplier)}x", color = if (multiplier < 2f) Color(0xFF3498DB) else if (multiplier < 10f) Color(0xFF9B59B6) else Color(0xFFF06292), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BetControl(
    modifier: Modifier, amount: Float, onAmountChange: (Float) -> Unit,
    isBetActive: Boolean, hasCashedOut: Boolean, winAmount: Float,
    isAutoBetEnabled: Boolean, onAutoBetToggle: (Boolean) -> Unit,
    isAutoCashOutEnabled: Boolean, onAutoCashOutToggle: (Boolean) -> Unit,
    autoCashOutValue: Float, onAutoCashOutValueChange: (Float) -> Unit,
    gameStatus: AviatorGameStatus, onBet: () -> Unit, onCashOut: () -> Unit,
    multiplier: Float
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasCashedOut && winAmount > 0) {
                Text(text = "+${"%.2f".format(winAmount)}", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { if (amount > 10) onAmountChange(amount - 10) else if (amount > 1) onAmountChange(amount - 1) else if (amount > 0.30) onAmountChange(amount - 0.10f) else onAmountChange(0.20f) }, enabled = !isBetActive || gameStatus == AviatorGameStatus.WAITING) { Text("-", color = Color.White, fontSize = 20.sp) }
                var amountText by remember(amount) { mutableStateOf(if (amount % 1f == 0f) amount.toInt().toString() else "%.2f".format(amount).replace(",", ".")) }
                BasicTextField(value = amountText, onValueChange = { nv -> val s = nv.replace(",", "."); if (s.isEmpty()) amountText = "" else if (s.all { it.isDigit() || it == '.' }) { amountText = s; s.toFloatOrNull()?.let { onAmountChange(it) } } }, enabled = !isBetActive || gameStatus == AviatorGameStatus.WAITING, textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = Modifier.width(60.dp).background(Color(0xFF3D3D3D), RoundedCornerShape(4.dp)).padding(4.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                IconButton(onClick = { if (amount >= 10) onAmountChange(amount + 10) else if (amount >= 1) onAmountChange(amount + 1) else onAmountChange(amount + 0.10f) }, enabled = !isBetActive || gameStatus == AviatorGameStatus.WAITING) { Text("+", color = Color.White, fontSize = 20.sp) }
            }
            Surface(onClick = { onAmountChange(0.20f) }, modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 2.dp), enabled = !isBetActive || gameStatus == AviatorGameStatus.WAITING, shape = RoundedCornerShape(4.dp), color = Color(0xFFC62828)) { Box(contentAlignment = Alignment.Center) { Text("0.20 Qəpik", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) } }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1f, 2f, 5f).forEach { vm ->
                    Surface(onClick = { onAmountChange(vm) }, modifier = Modifier.weight(1f).height(24.dp), enabled = !isBetActive || gameStatus == AviatorGameStatus.WAITING, shape = RoundedCornerShape(4.dp), color = Color(0xFF3D3D3D)) { Box(contentAlignment = Alignment.Center) { Text("${vm.toInt()} Manat", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) } }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isAutoBetEnabled, onCheckedChange = onAutoBetToggle, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50)), modifier = Modifier.size(24.dp)); Text("Avto", color = Color.White, fontSize = 9.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isAutoCashOutEnabled, onCheckedChange = onAutoCashOutToggle, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFC107)), modifier = Modifier.size(24.dp)); Text("Avto Nağd", color = Color.White, fontSize = 9.sp) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!isBetActive) {
                Button(onClick = onBet, enabled = gameStatus != AviatorGameStatus.FLYING, modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), disabledContainerColor = Color(0xFF1B351C)), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { Text("MƏRC", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            } else if (gameStatus == AviatorGameStatus.WAITING) {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E20)), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { Text("QOYULDU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            } else if (gameStatus == AviatorGameStatus.FLYING && !hasCashedOut) {
                Button(onClick = onCashOut, modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { val cw = amount * multiplier; Text("%.2f".format(cw), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.Black) }
            } else {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { val txt = if (hasCashedOut) "GÖZLƏNİLİR" else "UÇUR"; Text(txt, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun GameAnimation(multiplier: Float, status: AviatorGameStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "Floating")
    val bobbingOffset by infiniteTransition.animateFloat(initialValue = -12f, targetValue = 12f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "BobbingAnimation")
    val swayOffset by infiniteTransition.animateFloat(initialValue = -8f, targetValue = 8f, animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "SwayAnimation")
    val propAngle by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(100, easing = LinearEasing)), label = "PropellerSpin")
    val fallOffset by animateFloatAsState(targetValue = if (status == AviatorGameStatus.CRASHED) 1000f else 0f, animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing), label = "FallAnimation")
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width; val height = size.height; val strokeWidth = 5.dp.toPx(); val p30 = 30.dp.toPx(); val p8 = 8.dp.toPx(); val p60 = 60.dp.toPx(); val p16 = 16.dp.toPx(); val p24 = 24.dp.toPx(); val p20 = 20.dp.toPx(); val p4 = 4.dp.toPx(); val p60_span = 60.dp.toPx(); val p32 = 32.dp.toPx(); val p36 = 36.dp.toPx(); val p28 = 28.dp.toPx()
        val progress = (1f - (1f / (multiplier * 0.8f + 0.2f))).coerceIn(0f, 0.95f)
        val endX = (width * progress) + if (status == AviatorGameStatus.FLYING) swayOffset else 0f
        val startY = height * 0.85f; val baseEndY = startY - (height * 0.75f * progress)
        val fillPath = Path().apply { moveTo(0f, startY); quadraticBezierTo(width * 0.3f, startY, endX, baseEndY); lineTo(endX, startY); close() }
        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(Color.Red.copy(alpha = 0.5f), Color.Transparent), startY = baseEndY, endY = startY))
        val path = Path().apply { moveTo(0f, startY); quadraticBezierTo(width * 0.3f, startY, endX, baseEndY) }
        drawPath(path = path, color = Color.Red, style = Stroke(width = strokeWidth))
        val planeX = endX; val planeY = if (status == AviatorGameStatus.FLYING) baseEndY + bobbingOffset else baseEndY + fallOffset
        withTransform({ val rotation = if (status == AviatorGameStatus.CRASHED) 45f else -15f + (bobbingOffset / 2f) + (swayOffset / 2f); rotate(degrees = rotation, pivot = androidx.compose.ui.geometry.Offset(planeX, planeY)) }) {
            drawRoundRect(color = Color.Red, topLeft = androidx.compose.ui.geometry.Offset(planeX - p30, planeY - p8), size = androidx.compose.ui.geometry.Size(p60, p16), cornerRadius = androidx.compose.ui.geometry.CornerRadius(p8))
            drawArc(color = Color(0xFF87CEEB), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = androidx.compose.ui.geometry.Offset(planeX - p8, planeY - p16), size = androidx.compose.ui.geometry.Size(p24, p20))
            drawRect(color = Color.Red, topLeft = androidx.compose.ui.geometry.Offset(planeX - p4, planeY - p28), size = androidx.compose.ui.geometry.Size(p8, p60_span))
            drawRoundRect(color = Color.Red, topLeft = androidx.compose.ui.geometry.Offset(planeX - p16, planeY - p32), size = androidx.compose.ui.geometry.Size(p32, p8), cornerRadius = androidx.compose.ui.geometry.CornerRadius(p4))
            drawRect(color = Color.Red, topLeft = androidx.compose.ui.geometry.Offset(planeX - p36, planeY - p16), size = androidx.compose.ui.geometry.Size(p8, p32))
            withTransform({ rotate(propAngle, androidx.compose.ui.geometry.Offset(planeX + p30, planeY)) }) { drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(planeX + p30, planeY - p16), end = androidx.compose.ui.geometry.Offset(planeX + p30, planeY + p16), strokeWidth = p4) }
        }
    }
}

@Composable
fun AnalysisBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 8.sp)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun calculateAdvancedAnalysis(history: List<Float>): ProAnalysis {
    if (history.size < 5) return ProAnalysis(1.50f, 60, "STABIL", "YENI")
    val last30 = history.take(30); var ws = 0f; var wt = 0
    last30.forEachIndexed { i, v -> val w = (last30.size - i); ws += v * w; wt += w }
    val wma = ws / wt; val mean = last30.average().toFloat()
    val variance = last30.map { (it - mean) * (it - mean) }.average().toFloat()
    val sd = Math.sqrt(variance.toDouble()).toFloat()
    val vs = if (sd > 4f) "YUKSEK" else if (sd > 1.5f) "ORTA" else "ASAGI"
    val recentAvg = last30.take(5).average(); val olderAvg = last30.takeLast(10).average()
    val ts = if (recentAvg > olderAvg) "YUKSELEN" else "ENEN"
    var pred = wma * 0.85f; if (last30.take(3).all { it < 1.5f }) pred = 2.10f 
    if (last30.first() > 10f) pred = 1.15f; pred = pred.coerceIn(1.10f, 5.00f)
    val conf = (85 - (sd * 5).toInt() + (history.size / 20)).coerceIn(65, 96)
    return ProAnalysis(pred, conf, vs, ts)
}

data class ProAnalysis(val prediction: Float, val confidence: Int, val volatility: String, val trend: String)
