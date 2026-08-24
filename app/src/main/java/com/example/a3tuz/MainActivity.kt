package com.example.a3tuz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Build
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.a3tuz.api.AppConfig
import com.example.a3tuz.api.SocketManager
import com.example.a3tuz.ui.theme._3TuzTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isUrlFetched = mutableStateOf(false)
        var adminToken by mutableStateOf<String?>(null)

        lifecycleScope.launch {
            AppConfig.fetchBaseUrl()
            isUrlFetched.value = true
        }

        enableEdgeToEdge()
        setContent {
            _3TuzTheme {
                if (AppConfig.isMaintenanceMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Build,
                                contentDescription = null,
                                tint = Color.Yellow,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Serverdə təmir işləri gedir",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Narahatçılığa görə üzr istəyirik. 5 dəqiqədən sonra bir də cəhd edin.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (isUrlFetched.value) {
                    val navController = rememberNavController()
                    
                    // Qlobal socket dinləyiciləri
                    LaunchedEffect(AppConfig.currentUsername) {
                        val username = AppConfig.currentUsername
                        if (username.isNotEmpty()) {
                            val socket = SocketManager.getSocket()
                            
                            val balanceListener = io.socket.emitter.Emitter.Listener { args ->
                                val data = args.getOrNull(0)
                                val newBalance = when (data) {
                                    is Double -> data
                                    is Number -> data.toDouble()
                                    is String -> data.toDoubleOrNull() ?: AppConfig.userBalance
                                    else -> AppConfig.userBalance
                                }
                                (this@MainActivity).runOnUiThread {
                                    AppConfig.userBalance = newBalance
                                }
                            }
                            
                            val observerListener = io.socket.emitter.Emitter.Listener { args ->
                                val status = args.getOrNull(0) as? Boolean ?: false
                                (this@MainActivity).runOnUiThread {
                                    AppConfig.isObserver = status
                                }
                            }
                            
                            socket?.off("balance")
                            socket?.off("observerStatus")
                            socket?.on("balance", balanceListener)
                            socket?.on("observerStatus", observerListener)
                            
                            socket?.emit("identify", username)
                            
                            socket?.on(io.socket.client.Socket.EVENT_CONNECT) {
                                socket.emit("identify", username)
                            }
                        }
                    }

                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = { token: String? ->
                                    if (token != null) {
                                        adminToken = token
                                        navController.navigate("admin") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("main") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                },
                                onNavigateToRegister = {
                                    navController.navigate("register")
                                }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                onRegisterSuccess = {
                                    navController.navigate("login") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                },
                                onBackToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("main?topup={topup}") { backStackEntry ->
                            val showTopUp = backStackEntry.arguments?.getString("topup")?.toBoolean() ?: false
                            MainScreen(
                                showTopUpImmediately = showTopUp,
                                onGameSelect = { game ->
                                    when (game) {
                                        "3tuz" -> navController.navigate("lobby")
                                        "aviator" -> navController.navigate("aviator")
                                    }
                                },
                                onBackToGame = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("lobby") {
                            LobbyScreen(
                                onBack = { navController.popBackStack() },
                                onJoinRoom = { roomId, bet, password ->
                                    val route = if (password != null) "game/$roomId/$bet?password=$password" else "game/$roomId/$bet"
                                    navController.navigate(route)
                                }
                            )
                        }
                        composable("aviator") {
                            AviatorScreen(onBack = { navController.popBackStack() })
                        }
                        composable("game/{roomId}/{bet}?password={password}") { backStackEntry ->
                            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                            val bet = backStackEntry.arguments?.getString("bet")?.toDoubleOrNull() ?: 0.2
                            val password = backStackEntry.arguments?.getString("password")
                            GameScreen(
                                roomId = roomId,
                                bet = bet,
                                password = password,
                                onLeave = { navController.popBackStack() },
                                onNavigateToTopUp = {
                                    navController.navigate("main?topup=true")
                                }
                            )
                        }
                        composable("admin") {
                            adminToken?.let { token ->
                                AdminScreen(
                                    adminToken = token,
                                    onLogout = {
                                        // Admin statusunu sıfırla
                                        adminToken = null
                                        com.example.a3tuz.api.AppConfig.currentUsername = ""
                                        
                                        // Login ekranına naviqasiya et və stack-i təmizlə
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
