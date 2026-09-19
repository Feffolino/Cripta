package com.cripta.app.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import com.cripta.app.data.VaultRepository
import com.cripta.app.work.ConversionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: VaultRepository,
) : ViewModel() {

    /** Live progress/result of the in-app URL downloader. */
    val state: StateFlow<VaultRepository.DownloadState> = repo.downloadState

    fun cancel(ctx: Context) = ConversionService.cancelConvert(ctx)

    /** Clear a terminal (done/failed/cancelled) state so the UI returns to the idle form. */
    fun dismissResult() {
        if (!state.value.active) repo.setDownloadState(VaultRepository.DownloadState())
    }
}
