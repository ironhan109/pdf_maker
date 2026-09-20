package com.ebookmaker.app.ui.screens.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ebookmaker.app.EbookApplication
import com.ebookmaker.app.domain.model.ScanPage
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PageManagementViewModel(
    private val sessionId: Long,
    private val repository: SessionRepository = EbookApplication.instance.sessionRepository
) : ViewModel() {

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session: StateFlow<ScanSession?> = _session.asStateFlow()

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    private val _selectedPageForPreview = MutableStateFlow<ScanPage?>(null)
    val selectedPageForPreview: StateFlow<ScanPage?> = _selectedPageForPreview.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSession(sessionId).collect {
                _session.value = it
            }
        }
        viewModelScope.launch {
            repository.getPagesForSession(sessionId).collect {
                _pages.value = it
            }
        }
    }

    fun selectPageForPreview(page: ScanPage?) {
        _selectedPageForPreview.value = page
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch {
            repository.deletePage(pageId)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val currentList = _pages.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return

        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)

        viewModelScope.launch {
            repository.reorderPages(sessionId, currentList.map { it.id })
        }
    }

    class Factory(private val sessionId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PageManagementViewModel(sessionId) as T
        }
    }
}
