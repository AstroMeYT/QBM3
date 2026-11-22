package com.gatlin.qbpractice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Tossup(
    val id: String,
    val question: String,
    val answer: String,
    val category: String,
    val subcategory: String,
    val difficulty: Int,
    val setName: String,
    val year: Int,
)

object QbApi {
    private const val BASE = "https://www.qbreader.org/api"

    /** Category values accepted by the API. */
    val CATEGORIES = listOf(
        "Literature", "History", "Science", "Fine Arts", "Religion", "Mythology",
        "Philosophy", "Social Science", "Current Events", "Geography", "Other Academic", "Trash",
    )

    /** Difficulty values (0-10) accepted by the API, with QB Reader's level names. */
    val DIFFICULTIES: Map<Int, String> = linkedMapOf(
        0 to "Unrated",
        1 to "Middle school",
        2 to "Easy high school",
        3 to "Regular high school",
        4 to "Hard high school",
        5 to "National high school",
        6 to "1-dot (easy college)",
        7 to "2-dot (regular college)",
        8 to "3-dot (hard college)",
        9 to "4-dot (national college)",
        10 to "Open",
    )

    /** GET /api/random-tossup. Omitting a filter means "all" on the server. */
    suspend fun randomTossups(
        difficulties: Set<Int>,
        categories: Set<String>,
        number: Int = 10,
    ): List<Tossup> = withContext(Dispatchers.IO) {
        val params = mutableListOf("number=$number")
        if (difficulties.size < DIFFICULTIES.size) {
            params += "difficulties=" + URLEncoder.encode(difficulties.sorted().joinToString(","), "UTF-8")
        }
        if (categories.size < CATEGORIES.size) {
            params += "categories=" + URLEncoder.encode(categories.joinToString(","), "UTF-8")
        }
        val conn = URL("$BASE/random-tossup?" + params.joinToString("&")).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) error("Server returned ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).getJSONArray("tossups")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val set = o.optJSONObject("set")
                Tossup(
                    id = o.optString("_id"),
                    question = o.optString("question_sanitized", o.optString("question")),
                    answer = o.optString("answer_sanitized", o.optString("answer")),
                    category = o.optString("category"),
                    subcategory = o.optString("subcategory"),
                    difficulty = o.optInt("difficulty"),
                    setName = set?.optString("name") ?: "",
                    year = set?.optInt("year") ?: 0,
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
