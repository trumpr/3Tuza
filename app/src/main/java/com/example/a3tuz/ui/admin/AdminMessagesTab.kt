package com.example.a3tuz.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3tuz.api.AdminMessageData
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MessagesList(token: String, onRefreshUnread: () -> Unit) {
    var messages by remember { mutableStateOf<List<AdminMessageData>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchMessages() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminMessages(token)
                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    scope.launch(Dispatchers.Main) { 
                        messages = body 
                        onRefreshUnread()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        fetchMessages()
        val socket = SocketManager.getSocket()
        socket?.on("adminUpdate") { fetchMessages() }
    }

    if (selectedUser == null) {
        val groupedMessages = messages.groupBy { it.username }
        
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Yazışmalar", 
                fontWeight = FontWeight.Bold, 
                fontSize = 20.sp, 
                modifier = Modifier.padding(16.dp)
            )
            
            LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 8.dp)) {
                if (groupedMessages.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Hələ mesaj yoxdur", color = Color.Gray)
                        }
                    }
                }

                items(groupedMessages.entries.toList()) { entry ->
                    val username = entry.key
                    val userMsgs = entry.value
                    val lastMsg = userMsgs.last()
                    val hasNew = userMsgs.any { it.status == "new" }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                selectedUser = username
                                scope.launch {
                                    try {
                                        RetrofitInstance.api.adminMarkMessagesAsRead(token, mapOf("username" to username))
                                        fetchMessages()
                                        onRefreshUnread()
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFE3F2FD)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(username.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                                    }
                                }
                                if (hasNew) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.Red, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(lastMsg.date.split(" ").lastOrNull() ?: "", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(
                                    text = if(lastMsg.reply != null) "Siz: ${lastMsg.reply}" else lastMsg.message,
                                    fontSize = 13.sp,
                                    color = if(hasNew && lastMsg.reply == null) Color.Black else Color.Gray,
                                    maxLines = 1,
                                    fontWeight = if(hasNew && lastMsg.reply == null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        val userChatMsgs = messages.filter { it.username == selectedUser }
        val listState = rememberLazyListState()

        LaunchedEffect(userChatMsgs.size) {
            if (userChatMsgs.isNotEmpty()) {
                listState.animateScrollToItem(userChatMsgs.size - 1)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .navigationBarsPadding()
                .imePadding()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedUser = null }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                    Text(selectedUser!!, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, "Təmizlə", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    title = { Text("Yazışmanı Təmizlə") },
                    text = { Text("$selectedUser ilə olan bütün yazışmaları silmək istədiyinizdən əminsiniz?") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                RetrofitInstance.api.adminClearUserMessages(token, mapOf("username" to selectedUser!!))
                                showClearConfirm = false
                                selectedUser = null 
                                fetchMessages()
                            }
                        }) { Text("TƏMİZLƏ", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text("LƏĞV ET") }
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                items(userChatMsgs) { msg ->
                    ChatBubble(text = msg.message, date = msg.date, isMe = false)
                    if (msg.reply != null) {
                        ChatBubble(text = msg.reply, date = msg.replyDate ?: "", isMe = true)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Mesaj yazın...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5)
                        )
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    val lastUnreplied = userChatMsgs.lastOrNull { it.reply == null }
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                scope.launch {
                                    if (lastUnreplied != null) {
                                        RetrofitInstance.api.adminReplyMessage(token, mapOf("id" to lastUnreplied.id.toString(), "reply" to replyText))
                                    } else {
                                        RetrofitInstance.api.adminInitiateMessage(token, mapOf("username" to selectedUser!!, "message" to replyText))
                                    }
                                    replyText = ""
                                    fetchMessages()
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp).background(Color(0xFF2196F3), CircleShape),
                        enabled = replyText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, date: String, isMe: Boolean) {
    if (text == "(Admin tərəfindən başladıldı)") return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) Color(0xFFDCF8C6) else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 16.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = text, fontSize = 15.sp, color = Color.Black)
                Text(
                    text = date.split(" ").lastOrNull() ?: "",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}
