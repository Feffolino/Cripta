package com.cripta.app

import android.app.Application
import com.cripta.crypto.TinkInit
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CriptaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TinkInit.ensureInitialized()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }
}
