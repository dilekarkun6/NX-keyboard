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
        AUTO_FREE(
            "openrouter/free",
            "Auto (Free Router — Recommended)"
        ),
        DEEPSEEK_V3(
            "deepseek/deepseek-chat-v3-0324:free",
            "DeepSeek V3 (Free)"
        ),
        DEEPSEEK_R1(
            "deepseek/deepseek-r1:free",
            "DeepSeek R1 (Free)"
        ),
        QWEN3_CODER(
            "qwen/qwen3-coder:free",
            "Qwen3 Coder 480B (Free)"
        ),
        LLAMA_70B(
            "meta-llama/llama-3.3-70b-instruct:free",
            "Llama 3.3 70B (Free)"
        ),
        GEMINI_FLASH(
            "google/gemini-2.0-flash-exp:free",
            "Gemini 2.0 Flash (Free)"
        ),
        MISTRAL_SMALL(
            "mistralai/mistral-small-3.1-24b-instruct:free",
            "Mistral Small 3.1 24B (Free)"
        );

        companion object {
            fun fromId(id: String): Model = entries.firstOrNull { it.id == id } ?: AUTO_FREE
        }
    }

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "google/gemini-2.0-flash-exp:free"

        private val FALLBACK_CHAIN = listOf(
            "google/gemini-2.0-flash-exp:free",
            "openrouter/free",
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "mistralai/mistral-small-3.1-24b-instruct:free"
        )
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedFreeModels: List<String>? = null

    suspend fun translate(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty text"))
        val prompt = "Translate the following text to $targetLanguage. " +
            "Output ONLY the translation without any explanation, quotes, or extra commentary.\n\n$text"
        callAIWithFallback(prompt)
    }

    suspend fun correct(text: String, language: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty text"))
        val phoneticHints = PrefsHelper.getString(context, "phonetic_dict", "")
        val hintsBlock = if (phoneticHints.isNotBlank()) {
            "\n\nIMPORTANT — USER'S PHONETIC DICTIONARY (apply these substitutions exactly, " +
                "case-insensitive token match; the LEFT side is what the user types, the RIGHT " +
                "side is what they actually mean — always replace left→right):\n$phoneticHints\n" +
                "These substitutions take priority over your own guesses. If you see a left-side " +
                "token in the text, you MUST replace it with the right-side value.\n"
        } else ""
        val prompt = "You are an expert linguist correcting text typed on a phone in $language. " +
            "The writer often spells phonetically, mistypes adjacent keys, omits diacritics, drops " +
            "punctuation, and writes proper nouns by sound. Reconstruct the most likely intended " +
            "sentence and output ONLY the corrected text — no quotes, no explanation, no commentary. " +
            "Preserve the user's tone and intent exactly. DO NOT censor, mask, blur, or replace any " +
            "word — slang, profanity, and adult language must be passed through untouched and " +
            "uncensored. Output the words as written, never with asterisks or substitutes." +
            hintsBlock + "\n\nUser text:\n$text"
        callAIWithFallback(prompt)
    }

    private fun callAIWithFallback(prompt: String): Result<String> {
        val apiKey = resolveApiKey() ?: return Result.failure(IllegalStateException("API key unavailable"))
        val prefs = PrefsHelper.get(context)
        val preferredId = prefs.getString("ai_model", DEFAULT_MODEL) ?: DEFAULT_MODEL

        val tried = mutableSetOf<String>()
        val attemptOrder = buildList {
            add(preferredId)
            for (m in FALLBACK_CHAIN) if (m !in this) add(m)
            cachedFreeModels?.let { dynamic ->
                for (m in dynamic) if (m !in this) add(m)
            }
        }

        var lastError: Throwable = RuntimeException("No model attempted")

        for (modelId in attemptOrder) {
            if (modelId in tried) continue
            tried += modelId
            val attempt = callAIOnce(apiKey, modelId, prompt)
            attempt.onSuccess { return Result.success(it) }
            attempt.onFailure { err ->
                lastError = err
                val msg = err.message.orEmpty()
                val is404 = "HTTP 404" in msg || "No endpoints found" in msg
                val is400 = "HTTP 400" in msg
                val is429 = "HTTP 429" in msg
                if (is404 && cachedFreeModels == null) {
                    cachedFreeModels = fetchFreeModels(apiKey)
                }
                if (!is404 && !is400 && !is429) {
                    return Result.failure(err)
                }
            }
        }
        return Result.failure(lastError)
    }

    private fun callAIOnce(apiKey: String, modelId: String, prompt: String): Result<String> {
        return try {
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
                .addHeader("HTTP-Referer", "https://github.com/dilekarkun6/NX-keyboard")
                .addHeader("X-Title", "NX Keyboard")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(RuntimeException("HTTP ${response.code}: $body"))
                }
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: return Result.failure(RuntimeException("Empty response"))
                if (choices.length() == 0) return Result.failure(RuntimeException("No choices"))
                val content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                Result.success(content)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun fetchFreeModels(apiKey: String): List<String> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()
                val result = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val pricing = item.optJSONObject("pricing") ?: continue
                    val prompt = pricing.optString("prompt", "")
                    val completion = pricing.optString("completion", "")
                    if (prompt == "0" && completion == "0") {
                        result += id
                    }
                }
                result
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun resolveApiKey(): String? {
        val userKey = PrefsHelper.get(context).getString("ai_user_key", "") ?: ""
        if (userKey.isNotBlank()) return userKey
        return if (ApiKeyVault.isAvailable()) ApiKeyVault.resolve() else null
    }

    fun isConfigured(): Boolean {
        if (ApiKeyVault.isAvailable()) return true
        val userKey = PrefsHelper.get(context).getString("ai_user_key", "") ?: ""
        return userKey.isNotBlank()
    }
}
