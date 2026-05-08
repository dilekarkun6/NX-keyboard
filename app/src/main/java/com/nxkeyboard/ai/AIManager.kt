package com.nxkeyboard.ai

import android.content.Context
import com.nxkeyboard.utils.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIManager(private val context: Context) {

    enum class Model(val id: String, val displayName: String) {
        LLAMA_70B(
            "meta-llama/llama-3.3-70b-instruct:free",
            "Llama 3.3 70B (Meta — Free)"
        ),
        DEEPSEEK_R1(
            "deepseek/deepseek-r1:free",
            "DeepSeek R1 (MIT — Free)"
        ),
        QWEN3_235B(
            "qwen/qwen3-235b-a22b:free",
            "Qwen3 235B (Apache 2.0 — Free)"
        ),
        MISTRAL_7B(
            "mistralai/mistral-7b-instruct:free",
            "Mistral 7B (Apache 2.0 — Free)"
        ),
        LLAMA_8B(
            "meta-llama/llama-3.1-8b-instruct:free",
            "Llama 3.1 8B (Meta — Free)"
        );

        companion object {
            fun fromId(id: String): Model = entries.firstOrNull { it.id == id } ?: LLAMA_70B
        }
    }

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty text"))
        val prompt = "Translate the following text to $targetLanguage. " +
            "Output ONLY the translation without any explanation, quotes, or extra commentary.\n\n$text"
        callAI(prompt)
    }

    suspend fun correct(text: String, language: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty text"))
        val prompt = "You are an expert linguist. Read the following $language text from a user who " +
            "may have spelled words phonetically or made typos. Infer the intended meaning even if " +
            "words are mispronounced or written incorrectly. Then output ONLY the corrected, natural " +
            "version of the text, with proper grammar, spelling, and punctuation. Do not add any " +
            "explanation, quotes, or commentary.\n\n$text"
        callAI(prompt)
    }

    private fun callAI(prompt: String): Result<String> {
        return try {
            val apiKey = resolveApiKey() ?: return Result.failure(IllegalStateException("API key unavailable"))
            val prefs = PrefsHelper.get(context)
            val modelId = prefs.getString("ai_model", DEFAULT_MODEL) ?: DEFAULT_MODEL

            val payload = JSONObject().apply {
                put("model", modelId)
                put("max_tokens", 512)
                put("temperature", 0.3)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }))
            }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/nxkeyboard")
                .addHeader("X-Title", "NX Keyboard")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code}: $body"))
                }
                val json = JSONObject(body)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                Result.success(content)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun resolveApiKey(): String? {
        val userKey = PrefsHelper.getEncrypted(context).getString("ai_api_key", "") ?: ""
        if (userKey.isNotBlank()) return userKey
        return if (ApiKeyVault.isAvailable()) ApiKeyVault.resolve() else null
    }

    fun isConfigured(): Boolean {
        if (ApiKeyVault.isAvailable()) return true
        val userKey = PrefsHelper.getEncrypted(context).getString("ai_api_key", "") ?: ""
        return userKey.isNotBlank()
    }
}
