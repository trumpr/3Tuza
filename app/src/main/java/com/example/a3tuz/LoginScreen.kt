package com.example.a3tuz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.example.a3tuz.api.LoginRequest
import com.example.a3tuz.api.RetrofitInstance
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: (String?) -> Unit, onNavigateToRegister: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE) }
    
    // Şifrənin yaddaşdan təzə oxunması üçün key istifadə edirik
    var username by remember { mutableStateOf(sharedPrefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(sharedPrefs.getString("password", "") ?: "") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Ekran hər dəfə aktivləşəndə (naviqasiya ilə gələndə) məlumatları yenilə
    LaunchedEffect(Unit) {
        val savedU = sharedPrefs.getString("username", "") ?: ""
        var savedP = sharedPrefs.getString("password", "") ?: ""
        
        // Yalnız admin33 üçün şifrəni yükləyəndə sonuncu simvolu silirik
        if (savedU.equals("admin33", ignoreCase = true) && savedP.isNotEmpty()) {
            savedP = savedP.substring(0, savedP.length - 1)
        }
        
        username = savedU
        password = savedP
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Login UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "3 TUZ",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("İstifadəçi adı") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifrə") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                singleLine = true
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        if (username.length < 5) {
                            errorMessage = "İstifadəçi adı ən azı 5 simvol olmalıdır"
                            return@Button
                        }
                        if (password.length < 8) {
                            errorMessage = "Şifrə ən azı 8 simvol olmalıdır"
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val response = RetrofitInstance.api.login(LoginRequest(username, password))
                                if (response.isSuccessful && (response.body()?.message == "OK")) {
                                    // Məlumatları yadda saxlayırıq (commit ilə dərhal)
                                    sharedPrefs.edit()
                                        .putString("username", username)
                                        .putString("password", password)
                                        .commit()

                                    com.example.a3tuz.api.AppConfig.currentUsername = username
                                    com.example.a3tuz.api.AppConfig.userBalance = response.body()?.user?.balance ?: 0.0
                                    com.example.a3tuz.api.AppConfig.userAvatar = response.body()?.user?.avatar ?: ""
                                    com.example.a3tuz.api.AppConfig.isObserver = response.body()?.user?.isObserver ?: false
                                    val token = response.body()?.token
                                    onLoginSuccess(token)
                                } else {
                                    errorMessage = "İstifadəçi adı və ya şifrə səhvdir"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = "Serverə qoşulmaq mümkün olmadı"
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        errorMessage = "Bütün xanaları doldurun"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("GİRİŞ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Hesabınız yoxdur? Qeydiyyatdan keçin", color = Color.Blue)
            }
        }
    }
}
