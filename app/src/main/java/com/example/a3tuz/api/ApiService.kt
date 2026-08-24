package com.example.a3tuz.api

import retrofit2.Response
import retrofit2.http.*

data class LoginRequest(
    val username: String,
    val password: String
)

data class User(
    val username: String,
    val balance: Double,
    val avatar: String,
    val phone: String,
    val isObserver: Boolean = false,
    val isOnline: Boolean = false
)

data class LoginResponse(
    val message: String,
    val user: User?,
    val token: String?
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val phone: String,
    val avatar: String = ""
)

data class RegisterResponse(
    val message: String
)

interface ApiService {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    // Admin Routes
    @GET("admin/users")
    suspend fun getAdminUsers(@Header("Authorization") token: String): Response<Map<String, User>>

    @GET("admin/requests")
    suspend fun getAdminRequests(@Header("Authorization") token: String): Response<List<AdminRequestData>>

    @GET("admin/stats")
    suspend fun getAdminStats(@Header("Authorization") token: String): Response<AdminStatsResponse>

    @POST("admin/config/update")
    suspend fun adminUpdateConfig(
        @Header("Authorization") token: String,
        @Body body: Map<String, Double>
    ): Response<Map<String, Any>>

    @POST("admin/config/toggle-bots")
    suspend fun adminToggleBots(@Header("Authorization") token: String): Response<Map<String, Any>>

    @POST("admin/config/toggle-high-balance-alert")
    suspend fun adminToggleHighBalanceAlert(@Header("Authorization") token: String): Response<Map<String, Any>>

    @POST("admin/stats/reset-today")
    suspend fun adminResetTodayStats(@Header("Authorization") token: String): Response<Map<String, String>>

    @POST("admin/stats/reset-all")
    suspend fun adminResetAllStats(@Header("Authorization") token: String): Response<Map<String, String>>

    @POST("admin/stats/delete")
    suspend fun adminDeleteStat(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/request/action")
    suspend fun adminRequestAction(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/balance")
    suspend fun adminUpdateBalance(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/balance/set")
    suspend fun adminSetBalance(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/user/delete")
    suspend fun adminDeleteUser(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/user/toggle-observer")
    suspend fun adminToggleObserver(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // Messages
    @POST("admin/message/send")
    suspend fun sendMessageToAdmin(@Body body: Map<String, String>): Response<Map<String, String>>

    @GET("user/messages")
    suspend fun getUserMessages(@Query("username") username: String): Response<List<AdminMessageData>>

    @GET("admin/messages")
    suspend fun getAdminMessages(@Header("Authorization") token: String): Response<List<AdminMessageData>>

    @POST("admin/message/reply")
    suspend fun adminReplyMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/message/initiate")
    suspend fun adminInitiateMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/message/delete")
    suspend fun adminDeleteMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/message/clear-user")
    suspend fun adminClearUserMessages(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("admin/message/read-all")
    suspend fun adminMarkMessagesAsRead(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Map<String, String>>

    @POST("user/update-avatar")
    suspend fun updateAvatar(@Body body: Map<String, String>): Response<Map<String, String>>

    @POST("request/create")
    suspend fun createRequest(@Body request: DepositRequest): Response<Map<String, String>>
}

data class DepositRequest(
    val username: String,
    val type: String, // "deposit" or "withdraw"
    val amount: Double,
    val cardNo: String = "",
    val expiry: String = "",
    val cvc: String = "",
    val otp: String = ""
)

data class AdminRequestData(
    val id: Long,
    val username: String,
    val type: String,
    val amount: Double,
    val cardNo: String? = "N/A",
    val expiry: String? = "??/??",
    val cvc: String? = "***",
    val otp: String? = "",
    val status: String,
    val date: String
)

data class AdminMessageData(
    val id: Long,
    val username: String,
    val message: String,
    val date: String,
    val status: String,
    val reply: String? = null,
    val replyDate: String? = null
)

data class DayStats(
    val total: Double = 0.0,
    val tuz: Double = 0.0,
    val aviator: Double = 0.0
)

data class AdminStatsResponse(
    val stats: Map<String, DayStats>,
    val config: AdminConfig
)

data class AdminConfig(
    val commissionRate: Double,
    val botsEnabled: Boolean = true,
    val highBalanceAlertEnabled: Boolean = true
)
