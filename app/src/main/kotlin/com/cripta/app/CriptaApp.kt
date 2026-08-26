package com.cripta.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.cripta.crypto.TinkInit
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CriptaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        TinkInit.ensureInitialized()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }

    /** Register Coil's animated-image decoder so animated GIF/WebP/HEIF play in the viewer.
     *  minSdk is 31, so the platform ImageDecoder path is always available. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
}
