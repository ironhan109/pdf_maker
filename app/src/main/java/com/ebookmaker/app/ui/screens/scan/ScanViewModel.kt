package com.ebookmaker.app.ui.screens.scan

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ebookmaker.app.EbookApplication
import com.ebookmaker.app.data.camera.CameraController
import com.ebookmaker.app.data.local.FileManager
import com.ebookmaker.app.data.vision.FrameAnalyzer
import com.ebookmaker.app.domain.model.DetectionResult
import com.ebookmaker.app.domain.model.EnhancementMode
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanPage
import com.ebookmaker.app.domain.model.ScanningState
import com.ebookmaker.app.domain.model.SessionStatus
import com.ebookmaker.app.domain.repository.SessionRepository
import com.ebookmaker.app.domain.statemachine.ScanningStateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ScanViewModel(
    private val sessionId: Long,
    private val repository: SessionRepository = EbookApplication.instance.sessionRepository,
    private val fileManager: FileManager = EbookApplication.instance.fileManager
) : ViewModel() {

    private val stateMachine = ScanningStateMachine()
    val scanningState: StateFlow<ScanningState> = stateMachine.state

    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()

    private val _frameDimensions = MutableStateFlow(Pair(0, 0))
    val frameDimensions: StateFlow<Pair<Int, Int>> = _frameDimensions.asStateFlow()

    private val _enhancementMode = MutableStateFlow(EnhancementMode.COLOR)
    val enhancementMode: StateFlow<EnhancementMode> = _enhancementMode.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _lastThumbnailPath = MutableStateFlow<String?>(null)
    val lastThumbnailPath: StateFlow<String?> = _lastThumbnailPath.asStateFlow()

    private val _captureEffectEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureEffectEvent: SharedFlow<Unit> = _captureEffectEvent.asSharedFlow()

    private val isCapturing = AtomicBoolean(false)
    var cameraController: CameraController? = null

    val frameAnalyzer = FrameAnalyzer { result, width, height ->
        _frameDimensions.value = Pair(width, height)
        _detectionResult.value = result

        val nextState = stateMachine.onFrame(result, width, height)
        if (nextState is ScanningState.Capturing) {
            triggerCapture(result.quad)
        }
    }

    init {
        viewModelScope.launch {
            repository.updateSessionStatus(sessionId, SessionStatus.SCANNING)
            repository.getPagesForSession(sessionId).collect { pages ->
                _pageCount.value = pages.size
                _lastThumbnailPath.value = pages.lastOrNull()?.thumbnailPath
            }
        }
    }

    fun setEnhancementMode(mode: EnhancementMode) {
        _enhancementMode.value = mode
    }

    fun togglePause() {
        if (stateMachine.isPaused()) {
            stateMachine.resume()
            viewModelScope.launch { repository.updateSessionStatus(sessionId, SessionStatus.SCANNING) }
        } else {
            stateMachine.pause()
            viewModelScope.launch { repository.updateSessionStatus(sessionId, SessionStatus.PAUSED) }
        }
    }

    fun skipWaitPageTurn() {
        stateMachine.skipPageTurnWait()
    }

    fun manualCapture() {
        triggerCapture(_detectionResult.value?.quad)
    }

    private fun triggerCapture(quad: Quadrilateral?) {
        if (!isCapturing.compareAndSet(false, true)) return

        stateMachine.onCaptureTriggered()
        _captureEffectEvent.tryEmit(Unit)
        vibrateFeedback()

        val tempFile = fileManager.createTempCaptureFile()
        val controller = cameraController ?: run {
            isCapturing.set(false)
            stateMachine.reset()
            return
        }

        controller.takePicture(tempFile) { result ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (result.isSuccess) {
                        val file = result.getOrThrow()
                        repository.addCapturedPage(
                            sessionId = sessionId,
                            tempImageFile = file,
                            detectedQuad = quad,
                            enhancementMode = _enhancementMode.value
                        )
                        stateMachine.onCaptureCompleted()
                    } else {
                        stateMachine.reset()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stateMachine.reset()
                } finally {
                    isCapturing.set(false)
                }
            }
        }
    }

    private fun vibrateFeedback() {
        try {
            val context = EbookApplication.instance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController?.shutdown()
    }

    class Factory(private val sessionId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(sessionId) as T
        }
    }
}
