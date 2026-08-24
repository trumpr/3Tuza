package com.example.a3tuz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.*
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.api.calculateGameScore
import com.example.a3tuz.ui.components.*
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(roomId: String, bet: Double, password: String? = null, onLeave: () -> Unit, onNavigateToTopUp: () -> Unit) {
    var myCards by remember { mutableStateOf<List<String>>(emptyList()) }
    var players by remember { mutableStateOf<List<JSONObject?>>(List(6) { null }) }
    var gameMessage by remember { mutableStateOf("Oyunçular gözlənilir...") }
    var currentTurn by remember { mutableStateOf<String?>(null) }
    var turnTimer by remember { mutableIntStateOf(0) }
    var isOvertime by remember { mutableStateOf(false) }
    var potAmount by remember { mutableDoubleStateOf(0.0) }
    var currentMinBet by remember { mutableStateOf(bet) }
    var acUnlocked by remember { mutableStateOf(false) }
    var gameResult by remember { mutableStateOf<String?>(null) }
    var allPlayersHands by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var revealProgress by remember { mutableFloatStateOf(0f) }
    
    var showBetDialog by remember { mutableStateOf(false) }
    var customBetAmountText by remember { mutableStateOf("") }
    var betAnimations by remember { mutableStateOf<List<BetAnim>>(emptyList()) }
    var showBalanceErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("Zəhmət olmasa balansı artırın.") }
    var topupData by remember { mutableStateOf<JSONObject?>(null) }
    var topupTimeLeft by remember { mutableStateOf(0) }
    
    var sekaOfferData by remember { mutableStateOf<JSONObject?>(null) }
    var hasJoinedSeka by remember { mutableStateOf(false) }
    var splitOfferFrom by remember { mutableStateOf<String?>(null) }
    var isSplitWaiting by remember { mutableStateOf(false) }
    var isSplitDisabledForRound by remember { mutableStateOf(false) }
    var manualSekaOfferFrom by remember { mutableStateOf<String?>(null) }
    var isSekaWaiting by remember { mutableStateOf(false) }
    var isSekaDisabledForRound by remember { mutableStateOf(false) }
    var isNavigatingToTopUp by remember { mutableStateOf(false) }
    var cheatTarget by remember { mutableStateOf<String?>(null) }
    var cheatLevel by remember { mutableIntStateOf(0) }
    
    var isTyomnuActive by remember { mutableStateOf(false) }
    var isTyomnuChainInProgress by remember { mutableStateOf(false) }
    var showTyomnuDialog by remember { mutableStateOf(false) }
    var tyomnuAmount by remember { mutableDoubleStateOf(0.0) }
    var tyomnuTimeLeft by remember { mutableIntStateOf(5) }
    
    val scope = rememberCoroutineScope()
    val gameContext = LocalContext.current
    val view = LocalView.current
    val socket = SocketManager.getSocket()

    DisposableEffect(Unit) {
        val window = (gameContext as android.app.Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
    }

    fun playSound(resId: Int) {
        try { val mp = MediaPlayer.create(gameContext, resId); mp.setOnCompletionListener { it.release() }; mp.start() } catch (e: Exception) { e.printStackTrace() }
    }

    fun getCardDrawable(card: String): Int {
        try {
            val vS = card.substring(0, card.length - 1); val sC = card.last()
            val v = when(vS) { "A" -> "14"; "K" -> "13"; "Q" -> "12"; "J" -> "11"; else -> vS }
            val s = when(sC) { '♣' -> "c"; '♦' -> "d"; '♥' -> "h"; '♠' -> "s"; else -> "back" }
            val rId = gameContext.resources.getIdentifier("card_${v}_$s", "drawable", gameContext.packageName)
            return if (rId != 0) rId else R.drawable.card_back
        } catch (e: Exception) { return R.drawable.card_back }
    }

    fun sendAction(action: String, amount: Double? = null) {
        val data = JSONObject().apply { put("roomId", roomId); put("action", action); if (amount != null) put("amount", amount) }
        socket?.emit("action", data)
    }

    DisposableEffect(Unit) {
        val onCards = Emitter.Listener { args ->
            val data = args[0] as? JSONArray
            if (data != null) {
                val cards = mutableListOf<String>()
                for (i in 0 until data.length()) cards.add(data.getString(i))
                scope.launch { myCards = cards; revealProgress = 0f; gameMessage = ""; gameResult = null; allPlayersHands = emptyMap(); playSound(R.raw.card_sound) }
            }
        }
        val onPlayers = Emitter.Listener { args ->
            val data = args[0] as? JSONArray
            if (data != null) {
                val list = mutableListOf<JSONObject?>()
                for (i in 0 until 6) { if (data.isNull(i)) list.add(null) else list.add(data.getJSONObject(i)) }
                scope.launch { players = list }
            }
        }
        val onTimer = Emitter.Listener { args -> scope.launch { turnTimer = args[0] as? Int ?: 0 } }
        val onTimerOvertime = Emitter.Listener { args -> scope.launch { isOvertime = args[0] as? Boolean ?: false } }
        val onGameCountdown = Emitter.Listener { args -> val timeLeft = args[0] as? Int ?: 0; scope.launch { if (timeLeft > 0) gameMessage = "$timeLeft" } }
        val onPot = Emitter.Listener { args -> val p = (args[0] as? Number)?.toDouble() ?: 0.0; scope.launch { potAmount = p } }
        val onMinBet = Emitter.Listener { args -> val mb = (args[0] as? Number)?.toDouble() ?: bet; scope.launch { currentMinBet = mb } }
        val onTurn = Emitter.Listener { args -> scope.launch { currentTurn = args[0] as? String; isOvertime = false } }
        val onAcUnlocked = Emitter.Listener { args -> scope.launch { acUnlocked = args[0] as? Boolean ?: false } }
        val onResult = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                val winner = data.optString("winner"); val pot = data.optDouble("pot")
                val handsJson = data.optJSONObject("allHands"); val handsMap = mutableMapOf<String, List<String>>()
                if (handsJson != null) {
                    val keys = handsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next(); val cardsArr = handsJson.getJSONArray(key); val cardsList = mutableListOf<String>()
                        for (i in 0 until cardsArr.length()) cardsList.add(cardsArr.getString(i))
                        handsMap[key] = cardsList
                    }
                }
                scope.launch {
                    delay(1500)
                    allPlayersHands = handsMap; val isSeka = data.optBoolean("isSeka", false)
                    gameResult = if (isSeka) "$winner" else "Qalib: $winner (${String.format(Locale.getDefault(), "%.2f", pot)} ₼)"
                    currentTurn = null
                    
                    if (!isSeka && pot > 0) {
                        val mIdx = players.indexOfFirst { it?.optString("username") == AppConfig.currentUsername }.takeIf { it != -1 } ?: 0
                        val wIdx = players.indexOfFirst { it?.optString("username") == winner }
                        val rPos = if(winner == AppConfig.currentUsername) 0 else if(wIdx != -1) (wIdx - mIdx + 6) % 6 else -1
                        if (rPos != -1) {
                            val winAnim = BetAnim(System.currentTimeMillis(), winner, pot, AnimType.WIN, rPos)
                            betAnimations = betAnimations + winAnim; delay(4200); betAnimations = betAnimations.filter { it.id != winAnim.id }
                        }
                    }
                    if (isSeka) { playSound(R.raw.seka) } 
                    else if (winner == "BÖLÜNDÜ") { playSound(R.raw.pulbolundu) }
                    else if (winner == AppConfig.currentUsername) { playSound(R.raw.win) } 
                    else { playSound(R.raw.meglub) }
                }
            }
        }
        val onClear = Emitter.Listener { scope.launch { myCards = emptyList(); revealProgress = 0f; allPlayersHands = emptyMap(); gameResult = null; gameMessage = "Yeni raund gözlənilir..."; acUnlocked = false; sekaOfferData = null; hasJoinedSeka = false; isSplitWaiting = false; isSplitDisabledForRound = false; isSekaWaiting = false; isSekaDisabledForRound = false; isOvertime = false; isTyomnuActive = false; showTyomnuDialog = false } }
        val onActionSound = Emitter.Listener { args -> val sT = args[0] as? String; scope.launch { when(sT) { "pas" -> playSound(R.raw.pas); "money" -> playSound(R.raw.money); "bank" -> playSound(R.raw.bank); "open" -> playSound(R.raw.open); "tyomnu" -> playSound(R.raw.tyomnu); "patyomnu" -> playSound(R.raw.patyomnu) } } }
        val onPlayerBet = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                val u = data.optString("username"); val amt = data.optDouble("amount")
                val mIdx = players.indexOfFirst { it?.optString("username") == AppConfig.currentUsername }.takeIf { it != -1 } ?: 0
                val sIdx = players.indexOfFirst { it?.optString("username") == u }
                val rPos = if(u == AppConfig.currentUsername) 0 else if(sIdx != -1) (sIdx - mIdx + 6) % 6 else -1
                if (rPos != -1) { val nAnim = BetAnim(System.currentTimeMillis(), u, amt, AnimType.BET, rPos); scope.launch { betAnimations = betAnimations + nAnim; delay(2200); betAnimations = betAnimations.filter { it.id != nAnim.id } } }
            }
        }
        val onSekaOffer = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                scope.launch {
                    val participants = data.optJSONArray("participants"); var isP = false
                    if (participants != null) { for (i in 0 until participants.length()) { if (participants.getString(i) == AppConfig.currentUsername) isP = true } }
                    if (isP) hasJoinedSeka = true else sekaOfferData = data
                }
            }
        }
        val onSplitOffer = Emitter.Listener { args -> scope.launch { splitOfferFrom = (args[0] as? JSONObject)?.optString("from") } }
        val onSplitRejected = Emitter.Listener { scope.launch { isSplitWaiting = false; isSplitDisabledForRound = true } }
        val onManualSekaOffer = Emitter.Listener { args -> scope.launch { manualSekaOfferFrom = (args[0] as? JSONObject)?.optString("from") } }
        val onSekaRejected = Emitter.Listener { scope.launch { isSekaWaiting = false; isSekaDisabledForRound = true } }
        val onResetSplitStatus = Emitter.Listener { scope.launch { isSplitDisabledForRound = false; isSekaDisabledForRound = false } }
        val onObserverHands = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                val handsMap = mutableMapOf<String, List<String>>(); val keys = data.keys()
                while (keys.hasNext()) { val key = keys.next(); val arr = data.getJSONArray(key); val list = mutableListOf<String>(); for (i in 0 until arr.length()) list.add(arr.getString(i)); handsMap[key] = list }
                scope.launch { allPlayersHands = handsMap }
            }
        }
        val onTyomnuOffer = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                scope.launch {
                    tyomnuAmount = data.optDouble("amount")
                    tyomnuTimeLeft = 5
                    showTyomnuDialog = true
                }
            }
        }
        val onTyomnuStateUpdate = Emitter.Listener { args ->
            val data = args[0] as? JSONObject
            if (data != null) {
                scope.launch {
                    isTyomnuActive = data.optBoolean("active")
                    isTyomnuChainInProgress = data.optBoolean("chainInProgress")
                }
            }
        }

        socket?.on("cards", onCards); socket?.on("players", onPlayers); socket?.on("timer", onTimer)
        socket?.on("timerOvertime", onTimerOvertime); socket?.on("gameCountdown", onGameCountdown)
        socket?.on("potUpdate", onPot); socket?.on("minBetUpdate", onMinBet); socket?.on("turn", onTurn)
        socket?.on("acUnlocked", onAcUnlocked); socket?.on("gameResult", onResult)
        socket?.on("clearResult", onClear); socket?.on("actionSound", onActionSound); socket?.on("playerBet", onPlayerBet)
        socket?.on("sekaOffer", onSekaOffer); socket?.on("splitOffer", onSplitOffer); socket?.on("splitRejected", onSplitRejected)
        socket?.on("manualSekaOffer", onManualSekaOffer); socket?.on("sekaRejected", onSekaRejected)
        socket?.on("resetSplitStatus", onResetSplitStatus); socket?.on("observerHands", onObserverHands)
        socket?.on("tyomnuOffer", onTyomnuOffer); socket?.on("tyomnuStateUpdate", onTyomnuStateUpdate)
        socket?.on("cheatQueued") { args ->
            val data = args[0] as? JSONObject
            scope.launch {
                cheatTarget = data?.optString("target")
                cheatLevel = data?.optInt("level", 0) ?: 0
            }
        }
        socket?.on("cheatReset") { scope.launch { cheatTarget = null; cheatLevel = 0 } }
        socket?.on("topupStarted") { args -> scope.launch { topupData = args[0] as? JSONObject } }
        socket?.on("topupTimer") { args -> scope.launch { topupTimeLeft = args[0] as? Int ?: 0 } }
        socket?.on("topupEnded") { scope.launch { topupData = null } }
        socket?.on("error") { args ->
            val msg = args[0] as? String ?: "Xəta baş verdi"
            scope.launch {
                errorDialogMessage = msg
                showBalanceErrorDialog = true
            }
        }

        onDispose {
            socket?.off("cards"); socket?.off("players"); socket?.off("timer"); socket?.off("gameCountdown")
            socket?.off("potUpdate"); socket?.off("turn"); socket?.off("minBetUpdate"); socket?.off("acUnlocked")
            socket?.off("gameResult"); socket?.off("clearResult"); socket?.off("actionSound")
            socket?.off("playerBet"); socket?.off("sekaOffer"); socket?.off("splitOffer"); socket?.off("splitRejected")
            socket?.off("manualSekaOffer"); socket?.off("sekaRejected"); socket?.off("resetSplitStatus"); socket?.off("observerHands")
            socket?.off("tyomnuOffer"); socket?.off("tyomnuStateUpdate")
            if (!isNavigatingToTopUp) socket?.emit("leaveRoom")
        }
    }

    LaunchedEffect(Unit) {
        if (socket?.connected() == true) {
            socket.emit("joinRoom", JSONObject().apply {
                put("roomId", roomId)
                put("username", AppConfig.currentUsername)
                put("initialBet", bet)
                if (password != null) put("password", password)
            })
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sW = maxWidth; val sH = maxHeight
        Image(painter = painterResource(id = R.drawable.table), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        // Üst Bar
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = roomId, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Giriş: ${String.format(Locale.getDefault(), "%.2f", bet)} ₼", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(text = " Balans: ${String.format(Locale.getDefault(), "%.2f", AppConfig.userBalance)} ₼ ", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))) { Text("Çıxış", fontSize = 12.sp) }
        }

        // ORTA
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-30).dp)) {
                if (potAmount > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(bottom = 12.dp).size(width = 80.dp, height = 50.dp)
                    ) {
                        val rotations = listOf(-15f, 5f, 20f, -10f, 15f)
                        val xOffsets = listOf(-5f, 8f, -2f, 4f, 0f)
                        val yOffsets = listOf(-2f, 3f, 6f, -4f, 0f)
                        
                        repeat(5) { i ->
                            Image(
                                painter = painterResource(id = R.drawable.monet),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(28.dp)
                                    .graphicsLayer {
                                        rotationZ = rotations[i % rotations.size]
                                        translationX = xOffsets[i % xOffsets.size].dp.toPx()
                                        translationY = yOffsets[i % yOffsets.size].dp.toPx()
                                    },
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                    Text("${String.format(Locale.getDefault(), "%.2f", potAmount)} ₼", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                val msg = gameResult ?: if(myCards.isEmpty()) gameMessage else null
                if (!msg.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp)) { Text(text = msg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }

        // Oyunçular (Masanın ətrafında professional düzülüş)
        val myIdx = players.indexOfFirst { it?.optString("username") == AppConfig.currentUsername }.takeIf { it != -1 } ?: 0
        val radiusX = sW * 0.38f
        val radiusY = sH * 0.22f

        for (i in 0 until 6) {
            val sIdx = (myIdx + i) % 6
            val p = players[sIdx]
            val u = p?.optString("username") ?: ""
            if (u.isNotEmpty()) {
                val isMe = u == AppConfig.currentUsername
                val angle = 90.0 + (i * 60.0)
                val rad = Math.toRadians(angle)
                
                var posX = (radiusX.value * Math.cos(rad)).toFloat()
                var posY = (radiusY.value * Math.sin(rad)).toFloat()

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlayerCircleItem(
                        username = u,
                        avatarStr = p?.optString("avatar", "") ?: (if(isMe) AppConfig.userAvatar else ""),
                        isCurrentTurn = currentTurn == u,
                        turnTimer = turnTimer,
                        isOvertime = isOvertime,
                        cards = allPlayersHands[u],
                        isPlaying = p?.optBoolean("isPlaying", false) ?: (isMe && myCards.isNotEmpty()),
                        isFolded = p?.optBoolean("folded", false) ?: false,
                        isMe = isMe,
                        modifier = Modifier.offset(x = posX.dp, y = posY.dp),
                        isCheatVisible = AppConfig.isObserver,
                        cheatLevel = if (cheatTarget == u) cheatLevel else 0,
                        onCheatClick = {
                            socket?.emit("cheatAction", JSONObject().apply {
                                put("roomId", roomId)
                                put("targetUsername", u)
                            })
                        },
                        balance = if (p?.has("balance") == true && !p.isNull("balance")) p.optDouble("balance") else null,
                        cardAlignment = when(i) {
                            0 -> Alignment.TopCenter
                            1 -> Alignment.TopEnd
                            2 -> Alignment.BottomEnd
                            3 -> Alignment.BottomCenter
                            4 -> Alignment.BottomStart
                            5 -> Alignment.TopStart
                            else -> Alignment.TopCenter
                        }
                    )
                }
            }
        }

        betAnimations.forEach { FlyingCoin(it, sW.value, sH.value) }

        // Mənim Kartlarım
        if (myCards.isNotEmpty()) {
            val sc = calculateGameScore(myCards)
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)) {
                    if (revealProgress > 0.8f) { Surface(color = Color(0xFFFFC107), shape = RoundedCornerShape(20.dp)) { Text("XAL: ${String.format("%.1f", sc)}", color = Color.Black, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) } }
                    else { Text("AÇMAQ ÜÇÜN SÜRÜŞDÜR ➔", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp).pointerInput(isTyomnuChainInProgress) { detectHorizontalDragGestures { change, dragAmount -> if (!isTyomnuChainInProgress) { change.consume(); revealProgress = (revealProgress + (dragAmount / size.width.toFloat())).coerceIn(0f, 1f) } } }, contentAlignment = Alignment.Center) {
                        val spc = (-50).dp
                        Row(horizontalArrangement = Arrangement.spacedBy(spc), verticalAlignment = Alignment.Bottom) { repeat(3) { Image(painter = painterResource(id = R.drawable.card_back), null, modifier = Modifier.size(90.dp, 130.dp)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(spc), verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer { clip = true; shape = object : Shape { override fun createOutline(sz: Size, ld: LayoutDirection, dn: Density) = Outline.Generic(Path().apply { addRect(Rect(0f, 0f, sz.width * revealProgress, sz.height)) }) } }) {
                            myCards.forEach { c -> Image(painter = painterResource(id = getCardDrawable(c)), null, modifier = Modifier.size(90.dp, 130.dp)) }
                        }
                    }
                }
            }
        }

        // Düymələr
        if (myCards.isNotEmpty()) {
            val isMyT = currentTurn == AppConfig.currentUsername
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GameActionButton("PAS" + (if(isMyT) " ($turnTimer)" else ""), if (isOvertime && isMyT) Color.Red else Color.DarkGray, isMyT, Modifier.weight(1f)) { sendAction("pass") }
                        val rA = if(currentMinBet > potAmount && potAmount > 0) potAmount else currentMinBet
                        GameActionButton("ARTIR\n(${String.format(Locale.getDefault(), "%.2f", rA)} ₼)", Color(0xFFFF9800), isMyT && AppConfig.userBalance >= rA, Modifier.weight(1f)) { customBetAmountText = String.format(java.util.Locale.US, "%.2f", rA); showBetDialog = true }
                        GameActionButton("BANK\n(${String.format(Locale.getDefault(), "%.2f", potAmount)} ₼)", Color(0xFF2196F3), isMyT && potAmount > 0 && AppConfig.userBalance >= potAmount, Modifier.weight(1f)) { sendAction("bet", potAmount) }
                        GameActionButton("AÇ", Color(0xFF4CAF50), isMyT && acUnlocked && AppConfig.userBalance >= currentMinBet && !isTyomnuActive, Modifier.weight(1f)) { sendAction("ac") }
                        val actC = players.count { it != null && it.optBoolean("isPlaying", false) && !it.optBoolean("folded", false) }
                        GameActionButton(if (isSplitWaiting) "GÖZLƏ..." else "BÖLƏK", if (isSplitDisabledForRound) Color.Gray else Color(0xFF9C27B0), isMyT && acUnlocked && actC == 2 && !isSplitWaiting && !isSplitDisabledForRound, Modifier.weight(1f)) { isSplitWaiting = true; socket?.emit("offerSplit", JSONObject().apply { put("roomId", roomId) }) }
                        GameActionButton(if (isSekaWaiting) "GÖZLƏ..." else "SEKA", if (isSekaDisabledForRound) Color.Gray else Color(0xFFFF5722), isMyT && acUnlocked && actC == 2 && !isSekaWaiting && !isSekaDisabledForRound, Modifier.weight(1f)) { isSekaWaiting = true; socket?.emit("offerSeka", JSONObject().apply { put("roomId", roomId) }) }
                    }
                }
            }
        }

        if (showBetDialog) { AlertDialog(onDismissRequest = { showBetDialog = false }, title = { Text("Mərc Artır") }, text = { Column { val sug = currentMinBet; val fM = if(sug > potAmount && potAmount > 0) potAmount else sug; Text("Min: ${String.format("%.2f", fM)} | Balans: ${String.format("%.2f", AppConfig.userBalance)}"); OutlinedTextField(value = customBetAmountText, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) { if((it.toDoubleOrNull() ?: 0.0) <= AppConfig.userBalance) customBetAmountText = it } }, label = { Text("Məbləğ") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(onClick = { val amt = customBetAmountText.toDoubleOrNull(); if (amt != null && amt >= (if(currentMinBet > potAmount && potAmount > 0) potAmount else currentMinBet) && amt <= potAmount && amt <= AppConfig.userBalance) { sendAction("bet", amt); showBetDialog = false } }) { Text("Göndər") } }, dismissButton = { TextButton(onClick = { showBetDialog = false }) { Text("Ləğv et") } }) }
        
        sekaOfferData?.let { data -> 
            val cost = data.optDouble("cost", 0.0)
            AlertDialog(onDismissRequest = {}, title = { Text("SEKA!") }, text = { Text("Giriş: ${String.format("%.2f", cost)} ₼") }, 
                confirmButton = { Button(onClick = { if (AppConfig.userBalance >= cost) { socket?.emit("joinSeka", JSONObject().apply { put("roomId", roomId); put("username", AppConfig.currentUsername) }); hasJoinedSeka = true; sekaOfferData = null } }) { Text("QOŞUL") } },
                dismissButton = { TextButton(onClick = { sekaOfferData = null }) { Text("İzləyici qal") } }
            ) 
        }

        splitOfferFrom?.let { f -> AlertDialog(onDismissRequest = {}, title = { Text("Bölək?") }, text = { Text("$f pulu bölməyi təklif edir") }, confirmButton = { Button(onClick = { socket?.emit("respondSplit", JSONObject().apply { put("roomId", roomId); put("accept", true) }); splitOfferFrom = null }) { Text("RAZIYAM") } }, dismissButton = { TextButton(onClick = { socket?.emit("respondSplit", JSONObject().apply { put("roomId", roomId); put("accept", false) }); splitOfferFrom = null }) { Text("RƏDD ET") } }) }
        manualSekaOfferFrom?.let { f -> AlertDialog(onDismissRequest = {}, title = { Text("Seka?") }, text = { Text("$f seka etməyi təklif edir") }, confirmButton = { Button(onClick = { socket?.emit("respondSeka", JSONObject().apply { put("roomId", roomId); put("accept", true) }); manualSekaOfferFrom = null }) { Text("RAZIYAM") } }, dismissButton = { TextButton(onClick = { socket?.emit("respondSeka", JSONObject().apply { put("roomId", roomId); put("accept", false) }); manualSekaOfferFrom = null }) { Text("RƏDD ET") } }) }
        if (showBalanceErrorDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (errorDialogMessage.contains("şifrə")) "Giriş Xətası" else "Balans Bitdi!") },
                text = { Text(errorDialogMessage) },
                confirmButton = {
                    if (!errorDialogMessage.contains("şifrə")) {
                        Button(onClick = { isNavigatingToTopUp = true; sendAction("start_topup"); showBalanceErrorDialog = false; onNavigateToTopUp() }) { Text("BALANSI ARTIR") }
                    } else {
                        Button(onClick = { showBalanceErrorDialog = false; onLeave() }) { Text("GERİ DÖN") }
                    }
                },
                dismissButton = {
                    if (!errorDialogMessage.contains("şifrə")) {
                        TextButton(onClick = { sendAction("pass"); showBalanceErrorDialog = false }) { Text("PAS KEÇ") }
                    }
                }
            )
        }
        topupData?.let { d -> Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color.Yellow); Spacer(Modifier.height(16.dp)); Text("${d.optString("username")} balans artırır...", color = Color.White); Text("${topupTimeLeft}s", color = Color.Yellow, fontSize = 32.sp) } } }
        
        LaunchedEffect(showTyomnuDialog) {
            if (showTyomnuDialog) {
                while (tyomnuTimeLeft > 0) {
                    delay(1000)
                    tyomnuTimeLeft--
                }
                showTyomnuDialog = false
            }
        }

        if (showTyomnuDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Tyomnu? ($tyomnuTimeLeft)") },
                text = { Text("Qaranlıq qurmaq istəyirsən? (${String.format("%.2f", tyomnuAmount)} ₼)") },
                confirmButton = {
                    Button(onClick = {
                        socket?.emit("respondTyomnu", JSONObject().apply { put("roomId", roomId); put("accept", true) })
                        showTyomnuDialog = false
                    }) { Text("QUR") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        socket?.emit("respondTyomnu", JSONObject().apply { put("roomId", roomId); put("accept", false) })
                        showTyomnuDialog = false
                    }) { Text("QURMA") }
                }
            )
        }
    }
}

