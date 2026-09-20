package com.ebookmaker.app.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ebookmaker.app.EbookApplication
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ExportUiState {
    object Idle : ExportUiState()
    data class Exporting(val current: Int, val total: Int) : ExportUiState()
    data class Success(val pdfFile: File) : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}

class ExportViewModel(
    private val sessionId: Long,
    private val repository: SessionRepository = EbookApplication.instance.sessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session: StateFlow<ScanSession?> = _session.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSession(sessionId).collect {
                _session.value = it
            }
        }
        startExport()
    }

    fun startExport() {
        if (_uiState.value is ExportUiState.Exporting) return

        viewModelScope.launch {
            _uiState.value = ExportUiState.Exporting(0, 0)
            val result = repository.exportPdf(sessionId) { current, total ->
                _uiState.value = ExportUiState.Exporting(current, total)
            }

            if (result.isSuccess) {
                _uiState.value = ExportUiState.Success(result.getOrThrow())
            } else {
                _uiState.value = ExportUiState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "PDF 생성에 실패했습니다."
                )
            }
        }
    }

    class Factory(private val sessionId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExportViewModel(sessionId) as T
        }
    }
}
