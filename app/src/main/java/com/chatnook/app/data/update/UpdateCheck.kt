package com.chatnook.app.data.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A newer published build. */
data class Update(val version: String, val pageUrl: String)

/**
 * Asks the public GitHub releases API what the latest tag is.
 *
 * No dependency and no auth: one GET per launch, and any failure (offline, rate limit,
 * a private repo) simply means "no prompt".
 */
object UpdateCheck {
    private const val LATEST =
        "https://api.github.com/repos/akashthemagnificent-ux/chatnook-android/releases/latest"

    suspend fun check(current: String): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = Json.parseToJsonElement(body).jsonObject
            val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val page = json["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            if (isNewer(tag, current)) Update(tag.trimStart('v', 'V'), page) else null
        }.getOrNull()
    }

    /**
     * Dotted-numeric comparison, ignoring a leading `v` and any suffix: 0.1.12 beats
     * 0.1.9, which a string compare gets wrong. Missing parts count as zero.
     */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value.trim()
            .trimStart('v', 'V')
            .split('.', '-', '+')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
