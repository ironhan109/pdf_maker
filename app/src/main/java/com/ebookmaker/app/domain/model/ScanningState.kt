package com.ebookmaker.app.domain.model

sealed class ScanningState {
    // Searching for page in frame
    object Searching : ScanningState()

    // Page found, checking stability and sharpness over consecutive frames
    data class Stabilizing(val stableFrameCount: Int, val targetFrames: Int) : ScanningState()

    // Triggering auto capture
    object Capturing : ScanningState()

    // Post-capture cooldown
    object Cooldown : ScanningState()

    // Waiting for the user to turn the page
    object WaitPageTurn : ScanningState()

    // Manually paused by user
    object Paused : ScanningState()
}
