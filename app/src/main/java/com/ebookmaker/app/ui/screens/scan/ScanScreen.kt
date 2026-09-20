package com.ebookmaker.app.ui.screens.scan

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ebookmaker.app.data.camera.CameraController
import com.ebookmaker.app.domain.model.EnhancementMode
import com.ebookmaker.app.domain.model.ScanningState
import com.ebookmaker.app.ui.screens.scan.components.DetectionOverlay
import com.ebookmaker.app.ui.theme.PrimaryBlue
import com.ebookmaker.app.ui.theme.ScannerGreen
import com.ebookmaker.app.ui.theme.ScannerYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ScanScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToManage: () -> Unit,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory(sessionId))
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scanningState by viewModel.scanningState.collectAsState()
    val detectionResult by viewModel.detectionResult.collectAsState()
    val frameDimensions by viewModel.frameDimensions.collectAsState()
    val enhancementMode by viewModel.enhancementMode.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val lastThumbnailPath by viewModel.lastThumbnailPath.collectAsState()

    var showFlashOverlay by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val cameraController = remember { CameraController(context) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(cameraController) {
        viewModel.cameraController = cameraController
    }

    LaunchedEffect(Unit) {
        viewModel.captureEffectEvent.collect {
            showFlashOverlay = true
            delay(120)
            showFlashOverlay = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraController.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    post {
                        coroutineScope.launch {
                            cameraController.startCamera(lifecycleOwner, this@apply, viewModel.frameAnalyzer)
                        }
                    }
                }
            }
        )

        // 2. Detection Overlay (Green / Yellow boundary line)
        DetectionOverlay(
            quad = detectionResult?.quad,
            frameWidth = frameDimensions.first,
            frameHeight = frameDimensions.second,
            scanningState = scanningState
        )

        // 3. Shutter Flash Animation
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // 4. Top Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
            }

            // Page count indicator
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue)
            ) {
                Text(
                    text = "${pageCount}쪽 스캔됨",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row {
                // Enhancement Mode Selector
                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "필터", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("컬러 유지 (선명)") },
                            onClick = {
                                viewModel.setEnhancementMode(EnhancementMode.COLOR)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("그레이스케일") },
                            onClick = {
                                viewModel.setEnhancementMode(EnhancementMode.GRAYSCALE)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("문서 흑백 이진화 (B&W)") },
                            onClick = {
                                viewModel.setEnhancementMode(EnhancementMode.BINARIZED)
                                showFilterMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Flash / Torch toggle
                val isTorchOn by cameraController.isTorchOn.collectAsState()
                IconButton(
                    onClick = { cameraController.toggleTorch() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "조명",
                        tint = if (isTorchOn) ScannerYellow else Color.White
                    )
                }
            }
        }

        // 5. Center Guidance HUD
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = scanningState) {
                is ScanningState.Searching -> {
                    GuidanceBadge(text = "책 페이지를 화면에 맞춰주세요", color = Color.White)
                }
                is ScanningState.Stabilizing -> {
                    GuidanceBadge(
                        text = "흔들리지 않게 고정해주세요",
                        color = ScannerYellow
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.stableFrameCount.toFloat() / state.targetFrames.toFloat() },
                        modifier = Modifier
                            .width(180.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ScannerGreen,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
                is ScanningState.Capturing -> {
                    GuidanceBadge(text = "촬영 중...", color = ScannerGreen)
                }
                is ScanningState.WaitPageTurn -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.skipWaitPageTurn() }
                    ) {
                        GuidanceBadge(text = "다음 페이지를 넘겨주세요", color = Color.Cyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("터치하여 바로 다음 촬영", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                is ScanningState.Paused -> {
                    GuidanceBadge(text = "자동 스캔 일시정지됨", color = Color.LightGray)
                }
                else -> {}
            }
        }

        // 6. Bottom Controls Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail of last page -> navigate to page manager
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onNavigateToManage() },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastThumbnailPath != null) {
                        AsyncImage(
                            model = File(lastThumbnailPath!!),
                            contentDescription = "최근 페이지",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("${pageCount}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Shutter Button (Manual trigger)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (scanningState is ScanningState.Capturing) ScannerGreen else Color.White)
                        .clickable { viewModel.manualCapture() }
                )

                // Pause / Resume Auto Scanning
                IconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.DarkGray, CircleShape)
                ) {
                    Icon(
                        imageVector = if (scanningState is ScanningState.Paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "일시정지/재개",
                        tint = if (scanningState is ScanningState.Paused) ScannerGreen else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Done / Manage button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBlue)
                    .clickable { onNavigateToManage() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "촬영 완료 및 페이지 확인 (${pageCount}쪽)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun GuidanceBadge(text: String, color: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
