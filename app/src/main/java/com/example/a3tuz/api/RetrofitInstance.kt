package com.example.a3tuz.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private var retrofit: Retrofit? = null

    val api: ApiService
        get() {
            if (retrofit == null || retrofit?.baseUrl().toString() != AppConfig.baseUrl) {
                retrofit = Retrofit.Builder()
                    .baseUrl(AppConfig.baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!.create(ApiService::class.java)
        }
}
