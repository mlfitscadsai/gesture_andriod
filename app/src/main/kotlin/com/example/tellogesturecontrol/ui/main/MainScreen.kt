package com.example.tellogesturecontrol.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tellogesturecontrol.tello.TelloState
import java.util.concurrent.Executors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val telloState by viewModel.telloState.collectAsState()
    val detection by viewModel.detectionResult.collectAsState()
    val controlMode by viewModel.controlMode.collectAsState()
    val gestureArmed by viewModel.gestureArmed.collectAsState()
    val useFrontCamera by viewModel.useFrontCamera.collectAsState()
    val videoSource by viewModel.videoSource.collectAsState()
    val currentSsid by viewModel.currentSsid.collectAsState()
    val isBoundToWifi by viewModel.isBoundToWifi.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true
        viewModel.wifiBinder.refreshSsid()
    }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf<String>()
        if (!hasCameraPermission) permsToRequest.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Status & Wi-Fi Connection Bar
        TopTelemetryBar(
            telloState = telloState,
            currentSsid = currentSsid,
            isBoundToWifi = isBoundToWifi,
            fps = detection.fps,
            onConnectToggle = {
                if (telloState.connected && !telloState.simulatorMode) {
                    viewModel.disconnectTello()
                } else {
                    viewModel.connectTello()
                }
            },
            onWifiSettings = { viewModel.openWifiSettings() },
            onToggleSimulator = { viewModel.toggleSimulatorMode() }
        )

        // 2. Camera / Drone Video Viewport with Hand Landmark Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
        ) {
            if (videoSource == VideoSource.TELLO_STREAM && telloState.connected && !telloState.simulatorMode) {
                TelloVideoSurface(
                    onSurfaceReady = { surface -> viewModel.videoReceiver.start(surface) },
                    onSurfaceDestroyed = { viewModel.videoReceiver.stop() },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (hasCameraPermission) {
                CameraGesturePreview(
                    useFrontCamera = useFrontCamera,
                    onAnalyzeFrame = { viewModel.analyzeFrame(it) },
                    modifier = Modifier.fillMaxSize()
                )
                HandOverlay(
                    detection = detection,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Camera permission required for hand gestures",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }

            // Gesture HUD Badge (Top-Left)
            Surface(
                color = Color(0xCC0D1117),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeEmoji = gestureEmoji(detection.rawGestureId)
                    if (activeEmoji.isNotEmpty()) {
                        Text(
                            text = activeEmoji,
                            fontSize = 18.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    val handPrefix = if (detection.handedness.isNotEmpty()) "${detection.handedness}: " else ""
                    val confText = if (detection.rawGestureId >= 0) {
                        " (${(detection.confidence * 100).toInt()}%)"
                    } else ""
                    Text(
                        text = "$handPrefix${detection.rawGestureLabel}$confText",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Camera Switch & Video Source Controls (Top-Right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (telloState.connected && !telloState.simulatorMode) {
                    FilledTonalButton(
                        onClick = {
                            val next = if (videoSource == VideoSource.PHONE_CAMERA) {
                                VideoSource.TELLO_STREAM
                            } else {
                                VideoSource.PHONE_CAMERA
                            }
                            viewModel.setVideoSource(next)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (videoSource == VideoSource.PHONE_CAMERA) "Phone Cam" else "Tello Feed",
                            fontSize = 12.sp
                        )
                    }
                }
                Surface(
                    color = Color(0xCC0D1117),
                    shape = CircleShape
                ) {
                    IconButton(onClick = { viewModel.toggleCameraFacing() }) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom RC Velocity Readout
            Surface(
                color = Color(0xCC0D1117),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                val rc = telloState.lastRc
                Text(
                    text = "CMD: ${telloState.lastCommand.ifEmpty { "idle" }}  |  RC(LR:${rc.leftRight}, FB:${rc.forwardBack}, UD:${rc.upDown})",
                    color = Color(0xFF79C0FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 3. Flight Safety & Mode Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Takeoff / Land / Emergency Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (telloState.inFlight) viewModel.telloClient.land()
                            else viewModel.telloClient.takeoff()
                        },
                        enabled = telloState.connected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telloState.inFlight) Color(0xFF1F6FEB) else Color(0xFF238636)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (telloState.inFlight) Icons.Default.FlightLand else Icons.Default.FlightTakeoff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (telloState.inFlight) "Land" else "Takeoff")
                    }

                    Button(
                        onClick = { viewModel.telloClient.emergency() },
                        enabled = telloState.connected,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Emergency")
                    }
                }

                // Mode Switcher (Gesture vs Manual)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = controlMode == ControlMode.GESTURE,
                        onClick = { viewModel.setControlMode(ControlMode.GESTURE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Gesture Control")
                    }
                    SegmentedButton(
                        selected = controlMode == ControlMode.MANUAL,
                        onClick = { viewModel.setControlMode(ControlMode.MANUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Manual D-Pad")
                    }
                }

                if (controlMode == ControlMode.GESTURE) {
                    // Arm Gesture Control Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF21262D))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arm Gesture Flight Control",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (gestureArmed) "Gestures are actively sending RC commands"
                                else "Preview only — arm to send commands to drone",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = gestureArmed,
                            onCheckedChange = { viewModel.toggleGestureArmed() }
                        )
                    }

                    // Supported Gestures Legend with official hand gesture emojis
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        viewModel.gestureLabels.forEachIndexed { idx, label ->
                            val isActive = detection.rawGestureId == idx
                            val emoji = gestureEmoji(idx)
                            FilterChip(
                                selected = isActive,
                                onClick = {},
                                label = {
                                    Text(
                                        text = if (emoji.isNotEmpty()) "$emoji $label" else "$idx: $label",
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }
                } else {
                    ManualControlPad(
                        enabled = telloState.connected,
                        onMove = { dir -> viewModel.telloClient.move(dir, 30) },
                        onRotate = { cw -> viewModel.telloClient.rotate(cw, 30) }
                    )
                }
            }
        }
    }
}

private fun gestureEmoji(gestureId: Int): String = when (gestureId) {
    0 -> "✋" // Forward — Open palm
    1 -> "✊" // Stop — Upright fist
    2 -> "👆" // Up — Index pointing up
    3 -> "👌" // Land — OK sign
    4 -> "👇" // Down — Index pointing down
    5 -> "👊" // Back — Forward fist
    6 -> "🤛" // Left — Fist with thumb left
    7 -> "🤜" // Right — Fist with thumb right
    else -> ""
}

@Composable
private fun TopTelemetryBar(
    telloState: TelloState,
    currentSsid: String?,
    isBoundToWifi: Boolean,
    fps: Int,
    onConnectToggle: () -> Unit,
    onWifiSettings: () -> Unit,
    onToggleSimulator: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    telloState.simulatorMode -> Color(0xFFD29922)
                                    telloState.connected -> Color(0xFF2EA043)
                                    telloState.connecting -> Color(0xFF58A6FF)
                                    else -> Color(0xFFF85149)
                                }
                            )
                    )
                    Text(
                        text = telloState.statusMessage,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryStd,
                            contentDescription = "Battery",
                            tint = Color(0xFF3FB950),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = telloState.battery?.let { "$it%" } ?: "--%",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "H:${telloState.heightCm}cm",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${fps} FPS",
                        color = Color(0xFF79C0FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onWifiSettings,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isBoundToWifi) Color(0xFF3FB950) else Color(0xFF8B949E),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentSsid ?: "Select Tello Wi-Fi",
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onConnectToggle,
                    enabled = !telloState.connecting && !telloState.simulatorMode,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (telloState.connected && !telloState.simulatorMode) "Disconnect" else "Connect",
                        fontSize = 12.sp
                    )
                }

                FilledTonalButton(
                    onClick = onToggleSimulator,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (telloState.simulatorMode) Color(0xFF9E6A03) else Color(0xFF21262D)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Simulator",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sim", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ManualControlPad(
    enabled: Boolean,
    onMove: (String) -> Unit,
    onRotate: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Horizontal D-Pad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalButton(onClick = { onMove("forward") }, enabled = enabled) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Forward")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onMove("left") }, enabled = enabled) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Left")
                }
                FilledTonalButton(onClick = { onMove("right") }, enabled = enabled) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Right")
                }
            }
            FilledTonalButton(onClick = { onMove("back") }, enabled = enabled) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Back")
            }
        }

        // Right: Altitude & Yaw
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onMove("up") }, enabled = enabled) {
                    Text("Up", fontSize = 12.sp)
                }
                FilledTonalButton(onClick = { onMove("down") }, enabled = enabled) {
                    Text("Down", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onRotate(false) }, enabled = enabled) {
                    Icon(Icons.Default.RotateLeft, contentDescription = "CCW")
                }
                FilledTonalButton(onClick = { onRotate(true) }, enabled = enabled) {
                    Icon(Icons.Default.RotateRight, contentDescription = "CW")
                }
            }
        }
    }
}

@Composable
private fun CameraGesturePreview(
    useFrontCamera: Boolean,
    onAnalyzeFrame: (androidx.camera.core.ImageProxy) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        onAnalyzeFrame(imageProxy)
                    }
                }

            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun TelloVideoSurface(
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        onSurfaceReady(Surface(surface))
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}
