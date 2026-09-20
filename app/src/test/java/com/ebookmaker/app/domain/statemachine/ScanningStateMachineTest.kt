package com.ebookmaker.app.domain.statemachine

import com.ebookmaker.app.domain.model.DetectionResult
import com.ebookmaker.app.domain.model.Quadrilateral
import com.ebookmaker.app.domain.model.ScanningState
import com.ebookmaker.app.domain.model.VisionPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanningStateMachineTest {

    private lateinit var stateMachine: ScanningStateMachine

    @Before
    fun setUp() {
        stateMachine = ScanningStateMachine(
            requiredStableFrames = 4,
            cornerMovementThreshold = 10f,
            sharpnessThreshold = 50f,
            minAreaRatio = 0.20f,
            pageTurnDiffThreshold = 25f
        )
    }

    private fun createDummyQuad(offset: Float = 0f): Quadrilateral {
        return Quadrilateral(
            topLeft = VisionPoint(100f + offset, 100f + offset),
            topRight = VisionPoint(700f + offset, 100f + offset),
            bottomRight = VisionPoint(700f + offset, 900f + offset),
            bottomLeft = VisionPoint(100f + offset, 900f + offset)
        )
    }

    @Test
    fun initialState_isSearching() {
        assertEquals(ScanningState.Searching, stateMachine.state.value)
    }

    @Test
    fun onFrame_withNoQuad_staysSearching() {
        val result = DetectionResult(quad = null, sharpness = 100f, isStable = false, frameDiff = 0f)
        val state = stateMachine.onFrame(result, 1000, 1000)
        assertEquals(ScanningState.Searching, state)
    }

    @Test
    fun onFrame_withStableFrames_transitionsToStabilizingThenCapturing() {
        val quad = createDummyQuad(0f)
        val frameWidth = 1000
        val frameHeight = 1000

        // Frame 1
        var state = stateMachine.onFrame(DetectionResult(quad, sharpness = 60f, isStable = false, frameDiff = 0f), frameWidth, frameHeight)
        assertTrue(state is ScanningState.Stabilizing)

        // Frame 2
        state = stateMachine.onFrame(DetectionResult(quad, sharpness = 60f, isStable = false, frameDiff = 0f), frameWidth, frameHeight)
        assertTrue(state is ScanningState.Stabilizing)

        // Frame 3
        state = stateMachine.onFrame(DetectionResult(quad, sharpness = 60f, isStable = false, frameDiff = 0f), frameWidth, frameHeight)
        assertTrue(state is ScanningState.Stabilizing)

        // Frame 4 -> should reach requiredStableFrames (4) and trigger Capturing
        state = stateMachine.onFrame(DetectionResult(quad, sharpness = 60f, isStable = false, frameDiff = 0f), frameWidth, frameHeight)
        assertEquals(ScanningState.Capturing, state)
    }

    @Test
    fun onFrame_withBlurryFrame_doesNotCapture() {
        val quad = createDummyQuad(0f)
        val frameWidth = 1000
        val frameHeight = 1000

        // 4 frames with low sharpness (< 50f)
        for (i in 1..4) {
            stateMachine.onFrame(DetectionResult(quad, sharpness = 20f, isStable = false, frameDiff = 0f), frameWidth, frameHeight)
        }

        // Should still be in stabilizing or searching, NOT capturing
        assertTrue(stateMachine.state.value is ScanningState.Stabilizing)
    }

    @Test
    fun pauseAndResume_worksCorrectly() {
        stateMachine.pause()
        assertEquals(ScanningState.Paused, stateMachine.state.value)
        assertTrue(stateMachine.isPaused())

        stateMachine.resume()
        assertEquals(ScanningState.Searching, stateMachine.state.value)
    }

    @Test
    fun pageTurnFlow_transitionsToSearchingAfterTurn() {
        stateMachine.onCaptureTriggered()
        assertEquals(ScanningState.Capturing, stateMachine.state.value)

        stateMachine.onCaptureCompleted()
        assertEquals(ScanningState.WaitPageTurn, stateMachine.state.value)

        val quad = createDummyQuad(0f)
        // High diff simulates turning page
        stateMachine.onFrame(DetectionResult(quad, sharpness = 50f, isStable = false, frameDiff = 40f), 1000, 1000)
        assertEquals(ScanningState.WaitPageTurn, stateMachine.state.value)

        // Lower diff indicates page turn ended and view settled
        stateMachine.onFrame(DetectionResult(quad, sharpness = 50f, isStable = false, frameDiff = 5f), 1000, 1000)
        assertEquals(ScanningState.Searching, stateMachine.state.value)
    }
}
