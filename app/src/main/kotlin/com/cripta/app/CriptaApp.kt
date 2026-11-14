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
        fun sharedLinkStore(): com.cripta.app.data.SharedLinkStore
        fun dupScanStore(): com.cripta.app.data.dedup.DupScanStore
        fun sharedFilesStore(): com.cripta.app.data.SharedFilesStore
        fun appUpdater(): com.cripta.app.update.AppUpdater
    }

    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        com.cripta.app.ui.theme.CriptaIcons.init(resources)
        CrashLog.install(this)
        TinkInit.ensureInitialized()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        // At cold start no conversion can be in flight, so any leftover decrypted conversion
        // temp files are orphans from a killed process: listed now, shredded in the background.
        runCatching {
            EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                .vaultRepository().sweepConversionTemp()
        }
        // An update already installed leaves its APK behind in the cache: removed, off the main thread.
        kotlin.concurrent.thread(name = "drop-installed-update", isDaemon = true) {
            runCatching { EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java).appUpdater().dropInstalled(this) }
        }
    }

    /** Register Coil's animated-image decoder so animated GIF/WebP/HEIF play in the viewer.
     *  minSdk is 31, so the platform ImageDecoder path is always available. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
}
