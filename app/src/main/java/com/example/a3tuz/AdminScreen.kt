package com.example.a3tuz

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.ui.admin.EarningsList
import com.example.a3tuz.ui.admin.MessagesList
import com.example.a3tuz.ui.admin.RequestsList
import com.example.a3tuz.ui.admin.UsersList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(adminToken: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("İstifadəçilər", "Gözləyənlər", "Mesajlar", "Tarixçə", "Qazanclar")
    var showMenu by remember { mutableStateOf(false) }
    var hasUnreadMessages by remember { mutableStateOf(false) }
    var hasPendingRequests by remember { mutableStateOf(false) }
    var botsEnabled by remember { mutableStateOf(true) }
    var highBalanceAlertEnabled by remember { mutableStateOf(true) }
    
    // Son görülən sorğuları izləmək üçün
    var lastSeenRequestId by remember { mutableLongStateOf(0L) }
    var currentMaxRequestId by remember { mutableLongStateOf(0L) }
    
    // Arxa plan və səs sistemi üçün
    val adminScreenContext = LocalContext.current
    var isRinging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun checkUnreadMessages() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminMessages(adminToken)
                if (response.isSuccessful) {
                    val messages = response.body() ?: emptyList()
                    val hasUnread = messages.any { it.status == "new" }
                    scope.launch(Dispatchers.Main) { hasUnreadMessages = hasUnread }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun checkPendingRequests() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminRequests(adminToken)
                if (response.isSuccessful) {
                    val requests = response.body() ?: emptyList()
                    val pending = requests.filter { it.status == "pending" }
                    val maxId = pending.maxOfOrNull { it.id } ?: 0L
                    
                    scope.launch(Dispatchers.Main) { 
                        currentMaxRequestId = maxId
                        // Əgər yeni bir sorğu varsa (ID daha böyükdürsə) nöqtəni göstər
                        hasPendingRequests = maxId > lastSeenRequestId
                    }
                }
                
                // Bot statusunu da yoxlayaq
                val statsResponse = RetrofitInstance.api.getAdminStats(adminToken)
                if (statsResponse.isSuccessful) {
                    val config = statsResponse.body()?.config
                    scope.launch(Dispatchers.Main) {
                        botsEnabled = config?.botsEnabled ?: true
                        highBalanceAlertEnabled = config?.highBalanceAlertEnabled ?: true
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        checkUnreadMessages()
        checkPendingRequests()
        val socket = SocketManager.getSocket()
        socket?.on("notification") { args ->
            val data = args.getOrNull(0) as? org.json.JSONObject
            val type = data?.optString("type") ?: ""
            val title = data?.optString("title") ?: ""
            
            // Yalnız vacib sorğularda "Səsi Kəs" düyməsini göstər
            if (type != "new_user" && type != "high_balance" && title.contains("Sorğu", ignoreCase = true)) {
                isRinging = true
            }

            checkUnreadMessages()
            checkPendingRequests()
        }
        socket?.on("adminUpdate") { 
            checkUnreadMessages() 
            checkPendingRequests()
        }
        
        // Arxa plan xidmətini başlat
        val intent = Intent(adminScreenContext, AdminNotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            adminScreenContext.startForegroundService(intent)
        } else {
            adminScreenContext.startService(intent)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SocketManager.getSocket()?.off("notification")
            SocketManager.getSocket()?.off("adminUpdate")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tabs[selectedTab], fontWeight = FontWeight.Bold)
                        Text(
                            text = "Server: ${AppConfig.baseUrl}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    if (isRinging) {
                        Button(
                            onClick = { 
                                isRinging = false
                                // Xidmətdəki səsi də kəsək
                                val stopIntent = Intent(adminScreenContext, AdminNotificationService::class.java).apply {
                                    action = "STOP_SOUND"
                                }
                                adminScreenContext.startService(stopIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeOff, null)
                            Spacer(Modifier.width(4.dp))
                            Text("SƏSİ KƏS")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showMenu = true }) {
                        Box {
                            Icon(Icons.Default.Menu, contentDescription = "Menyu")
                            if (hasUnreadMessages || hasPendingRequests) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title)
                                        if ((index == 1 && hasPendingRequests) || (index == 2 && hasUnreadMessages)) {
                                            Spacer(Modifier.width(8.dp))
                                            Box(modifier = Modifier.size(6.dp).background(Color.Red, CircleShape))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedTab = index
                                    showMenu = false
                                    // Əgər Gözləyənlər tabı seçilirsə, bildirişi təmizlə
                                    if (index == 1) {
                                        lastSeenRequestId = currentMaxRequestId
                                        hasPendingRequests = false
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when(index) {
                                            0 -> Icons.Default.People
                                            1 -> Icons.Default.HourglassEmpty
                                            2 -> Icons.Default.Message
                                            3 -> Icons.Default.History
                                            else -> Icons.Default.MonetizationOn
                                        },
                                        contentDescription = null,
                                        tint = if ((index == 1 && hasPendingRequests) || (index == 2 && hasUnreadMessages)) Color.Red else LocalContentColor.current
                                    )
                                }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    if (botsEnabled) "Botları Dayandır" else "Botları Başlat",
                                    color = if (botsEnabled) Color.Red else Color(0xFF4CAF50)
                                ) 
                            },
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val resp = RetrofitInstance.api.adminToggleBots(adminToken)
                                        if (resp.isSuccessful) {
                                            val newState = resp.body()?.get("botsEnabled") as? Boolean ?: !botsEnabled
                                            scope.launch(Dispatchers.Main) { botsEnabled = newState }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                                showMenu = false
                            },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = if (botsEnabled) Color.Red else Color(0xFF4CAF50)
                                ) 
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    if (highBalanceAlertEnabled) "15₼ Xəbərdarlığı Söndür" else "15₼ Xəbərdarlığı Aktiv Et",
                                    color = if (highBalanceAlertEnabled) Color.Red else Color(0xFF4CAF50)
                                ) 
                            },
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val resp = RetrofitInstance.api.adminToggleHighBalanceAlert(adminToken)
                                        if (resp.isSuccessful) {
                                            val newState = resp.body()?.get("highBalanceAlertEnabled") as? Boolean ?: !highBalanceAlertEnabled
                                            scope.launch(Dispatchers.Main) { highBalanceAlertEnabled = newState }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                                showMenu = false
                            },
                            leadingIcon = { 
                                Icon(
                                    imageVector = if (highBalanceAlertEnabled) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (highBalanceAlertEnabled) Color.Red else Color(0xFF4CAF50)
                                ) 
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Çıxış", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onLogout()
                            },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = Color.Red) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
            when (selectedTab) {
                0 -> UsersList(adminToken)
                1 -> RequestsList(adminToken, onlyPending = true)
                2 -> MessagesList(adminToken, onRefreshUnread = { checkUnreadMessages() })
                3 -> RequestsList(adminToken, onlyPending = false)
                4 -> EarningsList(adminToken)
            }
        }
    }
}
