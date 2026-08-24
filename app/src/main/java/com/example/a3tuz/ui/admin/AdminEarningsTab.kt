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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3tuz.api.RetrofitInstance
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.api.DayStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EarningsList(token: String) {
    val adminContext = LocalContext.current
    var stats by remember { mutableStateOf<Map<String, DayStats>>(emptyMap()) }
    var commissionRate by remember { mutableStateOf(5.0) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var newRateText by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()

    fun fetchStats() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getAdminStats(token)
                if (response.isSuccessful) {
                    val body = response.body()
                    scope.launch(Dispatchers.Main) { 
                        stats = body?.stats ?: emptyMap()
                        commissionRate = body?.config?.commissionRate ?: 5.0
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        fetchStats()
        val socket = SocketManager.getSocket()
        socket?.on("adminUpdate") { fetchStats() }
    }

    val todayDate = java.time.LocalDate.now().toString()
    val currentMonthPrefix = todayDate.substring(0, 7) // YYYY-MM
    
    val todayStats = stats[todayDate] ?: DayStats()
    val monthlyTotal = stats.filter { it.key.startsWith(currentMonthPrefix) }.values.sumOf { it.total }
    val totalProfit = stats.values.sumOf { it.total }

    if (showRateDialog) {
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("Komissiya Faizını Dəyiş") },
            text = {
                OutlinedTextField(
                    value = newRateText,
                    onValueChange = { if(it.isEmpty() || it.toDoubleOrNull() != null) newRateText = it },
                    label = { Text("Yeni Faiz (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val rate = newRateText.toDoubleOrNull()
                    if (rate != null) {
                        scope.launch {
                            RetrofitInstance.api.adminUpdateConfig(token, mapOf("commissionRate" to rate))
                            showRateDialog = false
                            fetchStats()
                        }
                    }
                }) { Text("Yadda Saxla") }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) { Text("Ləğv Et") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        // Faiz Tənzimləmə
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { 
                newRateText = commissionRate.toString()
                showRateDialog = true 
            },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFFE65100))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Komissiya", fontSize = 11.sp, color = Color.Gray)
                    Text("$commissionRate%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("FAİZİ DƏYİŞ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
            }
        }

        // Qazanclar Paneli (3-lü görünüş)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Aylıq
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("BU AY", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", monthlyTotal)} ₼", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
            // Ümumi
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ÜMUMİ", fontSize = 10.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", totalProfit)} ₼", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Oyunlar üzrə bölgü
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("3 TUZ (Bugün)", fontSize = 9.sp, color = Color(0xFF0277BD), fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", todayStats.tuz)} ₼", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AVIATOR (Bugün)", fontSize = 9.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", todayStats.aviator)} ₼", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bugünkü Qazanc və Sıfırlama
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BUGÜNKÜ QAZANC (CƏMİ)", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", todayStats.total)} ₼", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF0D47A1))
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SIFIRLA", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { scope.launch { RetrofitInstance.api.adminResetTodayStats(token); fetchStats() } }) {
                            Icon(Icons.Default.Refresh, "Bugünü", tint = Color(0xFF1976D2))
                        }
                        IconButton(onClick = { scope.launch { RetrofitInstance.api.adminResetAllStats(token); fetchStats() } }) {
                            Icon(Icons.Default.DeleteForever, "Hamısını", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarixçə Qovluğu
        Card(
            modifier = Modifier.fillMaxWidth().clickable { isHistoryExpanded = !isHistoryExpanded },
            colors = CardDefaults.cardColors(containerColor = if(isHistoryExpanded) Color(0xFFF5F5F5) else Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isHistoryExpanded) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = Color(0xFFFFB300))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Gündəlik Tarixçə", fontWeight = FontWeight.Bold)
                }
                Icon(if (isHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
            }
        }

        if (isHistoryExpanded) {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(stats.entries.sortedByDescending { it.key }.toList()) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.key, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${String.format("%.2f", entry.value.total)} ₼", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(onClick = { 
                                    scope.launch { 
                                        try {
                                            val resp = RetrofitInstance.api.adminDeleteStat(token, mapOf("date" to entry.key))
                                            if (resp.isSuccessful) {
                                                fetchStats()
                                            } else {
                                                android.widget.Toast.makeText(adminContext, "Silinmədi: ${resp.code()}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(adminContext, "Xəta: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } 
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
