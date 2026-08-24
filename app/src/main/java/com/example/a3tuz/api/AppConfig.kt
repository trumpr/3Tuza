package com.example.a3tuz.api

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object AppConfig {
    // GitHub linkini Base64 ilə gizlətdik ki, APK daxilində birbaşa oxunmasın
    // Orijinal: https://raw.githubusercontent.com/trumpr/metn/main/url.txt
    private const val ENCODED_LINK = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL3RydW1wci9tZXRuL21haW4vdXJsLnR4dA=="
    
    var baseUrl: String = "" 
    var isMaintenanceMode by mutableStateOf(false)
    var currentUsername by mutableStateOf("")
    var userBalance by mutableStateOf(0.0)
    var userAvatar: String = "" // Base64 string
    var isObserver by mutableStateOf(false)

    private fun getDecodedUrl(): String {
        return try {
            val data = Base64.decode(ENCODED_LINK, Base64.DEFAULT)
            String(data)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun fetchBaseUrl() {
        withContext(Dispatchers.IO) {
            try {
                val urlToFetch = getDecodedUrl()
                if (urlToFetch.isEmpty()) return@withContext

                val client = OkHttpClient()
                val request = Request.Builder().url(urlToFetch).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val rawUrl = response.body?.string()?.trim()
                    if (rawUrl == "TEMIR" || rawUrl == "MAINTENANCE") {
                        isMaintenanceMode = true
                        return@withContext
                    }
                    
                    var url = rawUrl
                    if (!url.isNullOrEmpty()) {
                        if (!url.startsWith("http")) {
                            url = "https://$url"
                        }
                        baseUrl = if (url.endsWith("/")) url else "$url/"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
