package com.example.network

import com.example.BuildConfig
import com.example.util.TextNormalizer
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiRetrofitApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

enum class AiActionType(val labelBn: String) {
    FORMAT_MARKDOWN("মার্কডাউন ফরম্যাট"),
    SUMMARIZE_BN("বাংলা সারসংক্ষেপ"),
    GRAMMAR_FIX("বানান ও ব্যাকরণ সংশোধন"),
    TRANSLATE_BN_EN("বাংলা <-> ইংরেজি অনুবাদ")
}

object GeminiAiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val api: GeminiRetrofitApi by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiRetrofitApi::class.java)
    }

    suspend fun processSectionText(
        action: AiActionType,
        inputText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("সেকশনে কোনো টেক্সট নেই"))
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key সেট করা নেই। অনুগ্রহ করে AI Studio-র Secrets প্যানেলে GEMINI_API_KEY যুক্ত করুন।")
            )
        }

        val instruction = when (action) {
            AiActionType.FORMAT_MARKDOWN ->
                "Format the user's text into clean, well-structured Markdown with proper headings, bullet lists, GFM tables (if applicable), and KaTeX math ($...$ or $$...$$). Preserve Bengali Unicode (including ZWNJ/ZWJ) accurately. Return ONLY the formatted Markdown without extra commentary."
            AiActionType.SUMMARIZE_BN ->
                "Summarize the following text clearly and concisely in standard Bengali (বাংলা) using bullet points. Return ONLY the Bengali summary."
            AiActionType.GRAMMAR_FIX ->
                "Correct all spelling, punctuation, and grammatical errors in the following Bengali/English text while keeping its original meaning and Markdown formatting intact. Return ONLY the corrected text."
            AiActionType.TRANSLATE_BN_EN ->
                "If the following text is primarily in Bengali, translate it into natural, accurate English. If it is primarily in English, translate it into natural, standard Bengali (বাংলা). Preserve Markdown formatting. Return ONLY the translated text."
        }

        return@withContext try {
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = trimmed)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = instruction)))
            )
            val response = api.generateContent(apiKey = apiKey, request = request)
            val output = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (output.isNullOrBlank()) {
                Result.failure(IllegalStateException("এআই থেকে কোনো উত্তর পাওয়া যায়নি"))
            } else {
                Result.success(TextNormalizer.normalizeAndClean(output, cleanAiMathAndTables = true))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
