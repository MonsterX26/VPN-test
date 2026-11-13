package com.tarnished.grace

import android.app.Application

class GraceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Core.init(this)
    }
}
