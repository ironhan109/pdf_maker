package com.ebookmaker.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookmaker.app.EbookApplication
import com.ebookmaker.app.domain.model.ScanSession
import com.ebookmaker.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: SessionRepository = EbookApplication.instance.sessionRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<ScanSession>>(emptyList())
    val sessions: StateFlow<List<ScanSession>> = _sessions.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllSessions().collect {
                _sessions.value = it
            }
        }
    }

    fun createNewSession(title: String, onCreated: (sessionId: Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createSession(title)
            onCreated(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
