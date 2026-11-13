package com.tarnished.grace

import android.content.Context
import com.tarnished.grace.data.Engine
import com.tarnished.grace.data.Store

object Core {
    const val HTTP_PORT = 10809
    const val SOCKS_PORT = 10808

    lateinit var store: Store
    lateinit var engine: Engine

    fun init(context: Context) {
        if (!::store.isInitialized) {
            val app = context.applicationContext
            store = Store(app)
            engine = Engine(app)
        }
    }
}
