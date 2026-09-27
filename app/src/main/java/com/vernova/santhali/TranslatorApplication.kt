package com.vernova.santhali

import android.app.Application
import android.util.Log

class TranslatorApplication : Application() {
    companion object {
        private const val TAG = "TranslatorApp"
        lateinit var instance: TranslatorApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Application started — Offline Hindi ↔ Santhali Translator")
    }
}
