package com.tarnished.grace.data

import android.content.Context

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("grace", Context.MODE_PRIVATE)

    var links: String
        get() = prefs.getString("links", "").orEmpty()
        set(value) {
            prefs.edit().putString("links", value).apply()
        }

    var subscription: String
        get() = prefs.getString("subscription", "").orEmpty()
        set(value) {
            prefs.edit().putString("subscription", value).apply()
        }
}
