package com.ebookmaker.app.domain.statemachine

import com.ebookmaker.app.domain.model.DetectionResult
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanningState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class ScanningStateMachine(
    private val requiredStableFrames: Int = 8,
    private val cornerMovementThreshold: Float = 16f,
    private val sharpnessThreshold: Float = 60f,
    private val minAreaRatio: Float = 0.20f,
    private val pageTurnDiffThreshold: Float = 25f
) {
    private val _state = MutableStateFlow<ScanningState>(ScanningState.Searching)
    val state: StateFlow<ScanningState> = _state.asStateFlow()

    private val quadHistory = ArrayDeque<Quadrilateral>()
    private var isPageTurnInitiated = false
    private var lastCaptureTime = 0L

    fun pause() {
        _state.value = ScanningState.Paused
        quadHistory.clear()
    }

    fun resume() {
        _state.value = ScanningState.Searching
        quadHistory.clear()
        isPageTurnInitiated = false
    }

    fun isPaused(): Boolean = _state.value is ScanningState.Paused

    fun onCaptureTriggered() {
        _state.value = ScanningState.Capturing
        lastCaptureTime = System.currentTimeMillis()
        quadHistory.clear()
    }

    fun onCaptureCompleted() {
        _state.value = ScanningState.WaitPageTurn
        isPageTurnInitiated = false
        quadHistory.clear()
    }

    fun skipPageTurnWait() {
        _state.value = ScanningState.Searching
        isPageTurnInitiated = false
        quadHistory.clear()
    }

    fun onFrame(detection: DetectionResult, frameWidth: Int, frameHeight: Int): ScanningState {
        val currentState = _state.value

        if (currentState is ScanningState.Paused || currentState is ScanningState.Capturing) {
            return currentState
        }

        // Wait for page turn logic
        if (currentState is ScanningState.WaitPageTurn) {
            // Check frame diff: turning page causes a spike in frame diff
            if (detection.frameDiff > pageTurnDiffThreshold) {
                isPageTurnInitiated = true
            } else if (isPageTurnInitiated && detection.frameDiff < pageTurnDiffThreshold * 0.6f) {
                // Page turn completed and view is steady again
                isPageTurnInitiated = false
                _state.value = ScanningState.Searching
                quadHistory.clear()
                return ScanningState.Searching
            }
            return currentState
        }

        val quad = detection.quad
        val frameArea = frameWidth * frameHeight.toFloat()

        // Verify page detection & minimum area ratio
        if (quad == null || (frameArea > 0 && quad.area / frameArea < minAreaRatio)) {
            quadHistory.clear()
            _state.value = ScanningState.Searching
            return ScanningState.Searching
        }

        // Add to history and evaluate corner drift
        quadHistory.addLast(quad)
        if (quadHistory.size > requiredStableFrames) {
            quadHistory.removeFirst()
        }

        val isMovementStable = if (quadHistory.size >= 2) {
            var stable = true
            val latest = quadHistory.last()
            for (prev in quadHistory) {
                if (!latest.isMaxMovementLessThan(prev, cornerMovementThreshold)) {
                    stable = false
                    break
                }
            }
            stable
        } else {
            false
        }

        val isSharp = detection.sharpness >= sharpnessThreshold

        if (!isMovementStable) {
            // Movement too large, reset history partially
            if (quadHistory.size > 2) {
                while (quadHistory.size > 2) quadHistory.removeFirst()
            }
            _state.value = ScanningState.Searching
            return ScanningState.Searching
        }

        // Corners are stable, update stabilizing progress
        val currentStableFrames = quadHistory.size
        if (currentStableFrames < requiredStableFrames) {
            _state.value = ScanningState.Stabilizing(currentStableFrames, requiredStableFrames)
            return _state.value
        }

        // Stabilized! Check sharpness
        if (isSharp) {
            _state.value = ScanningState.Capturing
            lastCaptureTime = System.currentTimeMillis()
            quadHistory.clear()
            return ScanningState.Capturing
        } else {
            // Stable but blurry (e.g. out of focus)
            _state.value = ScanningState.Stabilizing(requiredStableFrames - 1, requiredStableFrames)
            return _state.value
        }
    }

    fun reset() {
        _state.value = ScanningState.Searching
        quadHistory.clear()
        isPageTurnInitiated = false
    }
}
