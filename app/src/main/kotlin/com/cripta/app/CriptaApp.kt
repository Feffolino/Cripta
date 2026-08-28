package com.cripta.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.cripta.app.data.VaultRepository
import com.cripta.crypto.TinkInit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class CriptaApp : Application(), ImageLoaderFactory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun vaultRepository(): VaultRepository
    }

    override fun onCreate() {
        super.onCreate()
        TinkInit.ensureInitialized()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        // At cold start no conversion can be in flight, so any leftover decrypted conversion
        // temp files are orphans from a killed process: shred them before anything else runs.
        runCatching {
            EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                .vaultRepository().sweepConversionTemp()
        }
    }

    /** Register Coil's animated-image decoder so animated GIF/WebP/HEIF play in the viewer.
     *  minSdk is 31, so the platform ImageDecoder path is always available. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
}
