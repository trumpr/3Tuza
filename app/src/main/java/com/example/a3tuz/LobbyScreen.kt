package com.example.a3tuz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(onBack: () -> Unit, onJoinRoom: (String, Double, String?) -> Unit) {
    var roomData by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    var onlineCount by remember { mutableStateOf(0) }
    var currentBalance = AppConfig.userBalance
    var userAvatar by remember { mutableStateOf(AppConfig.userAvatar) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customRoomName by remember { mutableStateOf("") }
    var customBet by remember { mutableStateOf("") }
    var customPassword by remember { mutableStateOf("") }
    
    // Parol tələb edən otaqlar üçün
    var showPasswordPrompt by remember { mutableStateOf<RoomInfo?>(null) }
    var enteredPassword by remember { mutableStateOf("") }
    
    val initialStatus = if (SocketManager.getSocket()?.connected() == true) "Online" else "Bağlanır..."
    var connectionStatus by remember { mutableStateOf(initialStatus) }
    
    val scope = rememberCoroutineScope()
    val lobbyContext = LocalContext.current

    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        if (base64Str.isEmpty()) return null
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = lobbyContext.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                    
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                    
                    val resp = RetrofitInstance.api.updateAvatar(mapOf("username" to AppConfig.currentUsername, "avatar" to base64))
                    if (resp.isSuccessful) {
                        AppConfig.userAvatar = base64
                        userAvatar = base64
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    DisposableEffect(Unit) {
        val s = SocketManager.getSocket()
        SocketManager.connect()
        
        val onConnect = Emitter.Listener {
            scope.launch(Dispatchers.Main) { connectionStatus = "Online" }
            s?.emit("getRoomCounts")
        }
        
        val onDisconnect = Emitter.Listener {
            scope.launch(Dispatchers.Main) { connectionStatus = "Offline" }
        }

        val onRoomCounts = Emitter.Listener { args ->
            try {
                val data = args[0] as JSONObject
                val updatedData = mutableMapOf<String, JSONObject>()
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val roomObj = data.optJSONObject(key)
                    if (roomObj != null) {
                        updatedData[key] = roomObj
                    } else {
                        val dummy = JSONObject()
                        dummy.put("count", data.optInt(key, 0))
                        updatedData[key] = dummy
                    }
                }
                scope.launch(Dispatchers.Main) { 
                    roomData = updatedData
                }
            } catch (e: Exception) {
                android.util.Log.e("Lobby", "Data xətası: ${e.message}")
            }
        }

        val onOnlineCount = Emitter.Listener { args ->
            val count = when (val data = args[0]) {
                is Int -> data
                is String -> data.toIntOrNull() ?: 0
                else -> 0
            }
            scope.launch(Dispatchers.Main) { onlineCount = count }
        }

        val onBalanceUpdate = Emitter.Listener { args ->
            val newBalance = (args[0] as? Number)?.toDouble() ?: AppConfig.userBalance
            scope.launch(Dispatchers.Main) { 
                AppConfig.userBalance = newBalance 
            }
        }

        s?.on(Socket.EVENT_CONNECT, onConnect)
        s?.on(Socket.EVENT_DISCONNECT, onDisconnect)
        s?.on("roomCounts", onRoomCounts)
        s?.on("onlineCount", onOnlineCount)
        s?.on("balance", onBalanceUpdate)
        
        if (s?.connected() == true) {
            s.emit("getRoomCounts")
        }

        onDispose {
            s?.off(Socket.EVENT_CONNECT, onConnect)
            s?.off(Socket.EVENT_DISCONNECT, onDisconnect)
            s?.off("roomCounts", onRoomCounts)
            s?.off("onlineCount", onOnlineCount)
            s?.off("balance", onBalanceUpdate)
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val s = SocketManager.getSocket()
            if (s?.connected() == true) {
                s.emit("getRoomCounts")
            }
            delay(2000)
        }
    }

    val displayOnlineCount = if (onlineCount > 0) onlineCount else roomData.values.sumOf { it.optInt("count", 0) }
    
    val defaultRoomIds = listOf("Otaq 1", "Otaq 2", "Otaq 3", "VİP Otaq")
    val allRooms = (defaultRoomIds + roomData.keys.filter { it !in defaultRoomIds && it != "Lobbi" && it != "lobby" }).distinct().map { id ->
        val stat = roomData[id]
        val serverBet = stat?.optDouble("bet", 0.0) ?: 0.0
        val hasPass = stat?.optBoolean("hasPassword", false) ?: false
        
        RoomInfo(
            id = id,
            bet = if (serverBet > 0) serverBet else {
                when(id) {
                    "Otaq 1" -> 0.2
                    "Otaq 2" -> 0.5
                    "Otaq 3" -> 1.0
                    "VİP Otaq" -> 5.0
                    else -> 0.2
                }
            },
            playerCount = stat?.optInt("count", 0) ?: 0,
            hasPassword = hasPass
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Profil Şəkli (Dəyişmək üçün kliklənən)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                                .clickable { launcher.launch("image/*") }
                        ) {
                            val bitmap = decodeBase64ToBitmap(userAvatar)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(AppConfig.currentUsername.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text("3 Tuz Lobbisi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val statusText = if (connectionStatus == "Online") "Online ($displayOnlineCount)" else connectionStatus
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = if (connectionStatus == "Online") Color(0xFF4CAF50) else Color.Red
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${String.format(java.util.Locale.getDefault(), "%.2f", currentBalance)} ₼",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("OTAQ YARAT") }
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Yeni Otaq Yarat", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(value = customRoomName, onValueChange = { customRoomName = it }, label = { Text("Otaq Adı") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = customBet, onValueChange = { customBet = it }, label = { Text("Giriş Məbləği (AZN)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customPassword,
                            onValueChange = { customPassword = it },
                            label = { Text("Parol (İstəyə bağlı)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val bet = customBet.toDoubleOrNull() ?: 0.2
                            if (customRoomName.isNotEmpty()) {
                                onJoinRoom(customRoomName, bet, if(customPassword.isEmpty()) null else customPassword)
                                showCreateDialog = false
                                customPassword = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Yarat")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Ləğv et")
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(allRooms) { room ->
                RoomCard(
                    room = room,
                    onClick = {
                        if (room.hasPassword) {
                            showPasswordPrompt = room
                        } else {
                            onJoinRoom(room.id, room.bet, null)
                        }
                    }
                )
            }
        }

        // Parol daxil etmə pəncərəsi
        showPasswordPrompt?.let { room ->
            AlertDialog(
                onDismissRequest = { showPasswordPrompt = null; enteredPassword = "" },
                title = { Text("Otaq Parolludur", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("${room.id} otağına girmək üçün parolu daxil edin:")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = enteredPassword,
                            onValueChange = { enteredPassword = it },
                            label = { Text("Parol") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onJoinRoom(room.id, room.bet, enteredPassword)
                        showPasswordPrompt = null
                        enteredPassword = ""
                    }) { Text("DAXİL OL") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordPrompt = null; enteredPassword = "" }) { Text("LƏĞV ET") }
                }
            )
        }
    }
}

@Composable
fun RoomCard(room: RoomInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${room.bet} ₼",
                        color = Color(0xFF2E7D32),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room.id,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        if (room.hasPassword) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = "Giriş Məbləği",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.playerCount}/6",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (room.playerCount >= 6) Color.Red else Color.Black
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (room.playerCount >= 6) Color.Gray else Color(0xFF2196F3)
                    ),
                    enabled = room.playerCount < 6
                ) {
                    Text(
                        text = if (room.playerCount >= 6) "Dolu" else "GİR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class RoomInfo(
    val id: String,
    val bet: Double,
    val playerCount: Int,
    val hasPassword: Boolean = false
)
