package com.example.a3tuz.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.api.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UsersList(token: String) {
    var users by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var userToDelete by remember { mutableStateOf<String?>(null) }
    
    // Tab seçimi: 0 -> Onlayn, 1 -> Oflayn
    var selectedUserTab by remember { mutableIntStateOf(0) }
    
    // Balans idarəetməsi üçün state-lər
    var userForBalance by remember { mutableStateOf<User?>(null) }
    var balanceActionType by remember { mutableStateOf("") } // "add", "sub", "set"
    var balanceAmountText by remember { mutableStateOf("") }
    
    // Adminin yeni mesaj başlatması üçün state-lər
    var userForMessage by remember { mutableStateOf<User?>(null) }
    var initiateMessageText by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val adminContext = LocalContext.current

    fun fetchUsers() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminUsers(token)
                if (response.isSuccessful) {
                    val body = response.body() ?: emptyMap()
                    scope.launch(Dispatchers.Main) { users = body }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        fetchUsers()
        val socket = SocketManager.getSocket()
        val updateListener = io.socket.emitter.Emitter.Listener { fetchUsers() }
        socket?.on("adminUpdate", updateListener)
    }

    DisposableEffect(Unit) {
        onDispose { SocketManager.getSocket()?.off("adminUpdate") }
    }

    // Balans Dəyişmə Dialoqu
    if (userForBalance != null) {
        AlertDialog(
            onDismissRequest = { userForBalance = null },
            title = { 
                Text(when(balanceActionType) {
                    "add" -> "Balans Artır"
                    "sub" -> "Balans Azalt"
                    else -> "Balans Təyin Et"
                } + " (${userForBalance?.username})")
            },
            text = {
                Column {
                    Text("Cari balans: ${userForBalance?.balance} AZN", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = balanceAmountText,
                        onValueChange = { if(it.isEmpty() || it.replace(",", ".").toDoubleOrNull() != null) balanceAmountText = it },
                        label = { Text("Məbləğ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = balanceAmountText.replace(",", ".").toDoubleOrNull()
                    if (amt != null) {
                        scope.launch {
                            val finalAmount = if (balanceActionType == "sub") -amt else amt
                            try {
                                if (balanceActionType == "set") {
                                    RetrofitInstance.api.adminSetBalance(
                                        token, 
                                        mapOf("username" to userForBalance!!.username, "amount" to finalAmount.toString())
                                    )
                                } else {
                                    RetrofitInstance.api.adminUpdateBalance(
                                        token, 
                                        mapOf("username" to userForBalance!!.username, "amount" to finalAmount.toString())
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            userForBalance = null
                            balanceAmountText = ""
                            fetchUsers()
                        }
                    }
                }) { Text("TƏSDİQLƏ") }
            },
            dismissButton = {
                TextButton(onClick = { userForBalance = null; balanceAmountText = "" }) { Text("LƏĞV ET") }
            }
        )
    }

    // Admin Mesaj Başlatma Dialoqu
    if (userForMessage != null) {
        AlertDialog(
            onDismissRequest = { userForMessage = null },
            title = { Text("Mesaj Yaz: ${userForMessage?.username}") },
            text = {
                OutlinedTextField(
                    value = initiateMessageText,
                    onValueChange = { initiateMessageText = it },
                    label = { Text("Mesajınız") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (initiateMessageText.isNotBlank()) {
                        scope.launch {
                            try {
                                RetrofitInstance.api.adminInitiateMessage(
                                    token,
                                    mapOf("username" to userForMessage!!.username, "message" to initiateMessageText)
                                )
                                userForMessage = null
                                initiateMessageText = ""
                                android.widget.Toast.makeText(adminContext, "Mesaj göndərildi!", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }) { Text("GÖNDƏR") }
            },
            dismissButton = {
                TextButton(onClick = { userForMessage = null; initiateMessageText = "" }) { Text("LƏĞV ET") }
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("İstifadəçini Sil") },
            text = { Text("${userToDelete} adlı istifadəçini və bütün məlumatlarını silmək istədiyinizdən əminsiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    val targetUser = userToDelete!!
                    scope.launch {
                        try {
                            val response = RetrofitInstance.api.adminDeleteUser(token, mapOf("username" to targetUser))
                            if (response.isSuccessful) {
                                android.widget.Toast.makeText(adminContext, "İstifadəçi silindi: $targetUser", android.widget.Toast.LENGTH_SHORT).show()
                                userToDelete = null
                                fetchUsers()
                            } else {
                                val errorMsg = response.errorBody()?.string() ?: "Naməlum xəta"
                                android.widget.Toast.makeText(adminContext, "Xəta: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(adminContext, "Bağlantı xətası: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("SİL", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("LƏĞV ET") }
            }
        )
    }

    val onlineUsers = users.values.filter { it.isOnline }.sortedBy { it.username }
    val offlineUsers = users.values.filter { !it.isOnline }.sortedBy { it.username }
    val currentDisplayList = if (selectedUserTab == 0) onlineUsers else offlineUsers

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedUserTab,
            containerColor = Color.White,
            contentColor = Color(0xFF2196F3)
        ) {
            Tab(
                selected = selectedUserTab == 0,
                onClick = { selectedUserTab = 0 },
                text = { Text("ONLAYN (${onlineUsers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Circle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp)) }
            )
            Tab(
                selected = selectedUserTab == 1,
                onClick = { selectedUserTab = 1 },
                text = { Text("OFLAYN (${offlineUsers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Circle, null, tint = Color.LightGray, modifier = Modifier.size(12.dp)) }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(8.dp)) {
            item {
                val totalBalance = users.values.sumOf { it.balance }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val label = if (selectedUserTab == 0) "ONLAYN" else "OFLAYN"
                            Text(label, fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("${currentDisplayList.size} nəfər", fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ÜMUMİ BALANS", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("${String.format("%.2f", totalBalance)} AZN", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                        }
                    }
                }
            }

            items(currentDisplayList) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (user.isOnline) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                                    }
                                }
                                Text("Balans: ${String.format("%.2f", user.balance)} AZN", color = Color(0xFF2E7D32), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Tel: ${user.phone}", fontSize = 12.sp, color = Color.Gray)
                            }
                            
                            // Göz İkonu (Müşahidəçi rejimi)
                            IconButton(onClick = {
                                scope.launch {
                                    RetrofitInstance.api.adminToggleObserver(token, mapOf("username" to user.username))
                                    fetchUsers()
                                }
                            }) {
                                Icon(
                                    imageVector = if (user.isObserver) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Müşahidəçi",
                                    tint = if (user.isObserver) Color(0xFF2196F3) else Color.LightGray
                                )
                            }

                            IconButton(onClick = { userToDelete = user.username }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                        
                        // Balans Düymələri
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Yeni Mesaj Yaz Düyməsi
                            IconButton(
                                onClick = { userForMessage = user; initiateMessageText = "" },
                                modifier = Modifier.size(36.dp).background(Color(0xFFFFF9C4), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Message, null, tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                            }

                            Button(
                                onClick = { userForBalance = user; balanceActionType = "add"; balanceAmountText = "" },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Text("ARTIR", fontSize = 11.sp)
                            }
                            
                            Button(
                                onClick = { userForBalance = user; balanceActionType = "sub"; balanceAmountText = "" },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                Text("AZALT", fontSize = 11.sp)
                            }
                            
                            Button(
                                onClick = { userForBalance = user; balanceActionType = "set"; balanceAmountText = "" },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Text("TƏYİN ET", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
