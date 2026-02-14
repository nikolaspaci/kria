package com.nikolaspaci.app.llamallmlocal.data.huggingface

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val BASE_API_URL = "https://huggingface.co/api/models"
        private const val BASE_URL = "https://huggingface.co"
    }

    suspend fun searchModels(query: String): Result<List<HfModel>> = withContext(Dispatchers.IO) {
        try {
            val searchQuery = if ("gguf" !in query.lowercase()) "$query gguf" else query
            val url = "$BASE_API_URL?search=${searchQuery.encodeUrl()}&sort=downloads&limit=20"
            val request = Request.Builder().url(url).build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Search failed: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val type = object : TypeToken<List<HfModel>>() {}.type
            val models: List<HfModel> = gson.fromJson(body, type)

            val filtered = models.filter { model ->
                model.tags?.any { it.equals("gguf", ignoreCase = true) } == true
            }

            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelDetail(repoId: String): Result<HfModelDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_API_URL/$repoId"
            val request = Request.Builder().url(url).build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to load model details: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val detail: HfModelDetail = gson.fromJson(body, HfModelDetail::class.java)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDownloadUrl(repoId: String, filename: String): String {
        return "$BASE_URL/$repoId/resolve/main/$filename"
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
