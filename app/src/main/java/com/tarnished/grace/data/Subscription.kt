package com.tarnished.grace.data

import java.net.HttpURLConnection
import java.net.URL

object Subscription {
    fun fetch(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "v2rayN/6")
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
