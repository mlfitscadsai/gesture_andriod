package com.example.tellogesturecontrol.ui.main

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import com.example.tellogesturecontrol.gesture.HandDetectionResult
import com.example.tellogesturecontrol.gesture.HandGestureRecognizer
import com.example.tellogesturecontrol.tello.TelloClient
import com.example.tellogesturecontrol.tello.TelloGestureController
import com.example.tellogesturecontrol.tello.TelloState
import com.example.tellogesturecontrol.tello.TelloVideoReceiver
import com.example.tellogesturecontrol.tello.WifiNetworkBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ControlMode {
    GESTURE,
    MANUAL
}

enum class VideoSource {
    PHONE_CAMERA,
    TELLO_STREAM
}

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val wifiBinder = WifiNetworkBinder(application)
    val telloClient = TelloClient()
    val videoReceiver = TelloVideoReceiver()
    private val gestureController = TelloGestureController(telloClient)
    private val gestureRecognizer = HandGestureRecognizer(application)

    val telloState: StateFlow<TelloState> = telloClient.state
    val isWifiAvailable: StateFlow<Boolean> = wifiBinder.isWifiAvailable
    val isBoundToWifi: StateFlow<Boolean> = wifiBinder.isBoundToWifi
    val currentSsid: StateFlow<String?> = wifiBinder.currentSsid

    private val _detectionResult = MutableStateFlow(HandDetectionResult())
    val detectionResult: StateFlow<HandDetectionResult> = _detectionResult.asStateFlow()

    private val _controlMode = MutableStateFlow(ControlMode.GESTURE)
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    private val _gestureArmed = MutableStateFlow(false)
    val gestureArmed: StateFlow<Boolean> = _gestureArmed.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _videoSource = MutableStateFlow(VideoSource.PHONE_CAMERA)
    val videoSource: StateFlow<VideoSource> = _videoSource.asStateFlow()

    val gestureLabels: List<String> = gestureRecognizer.gestureLabels

    init {
        wifiBinder.start()
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            val result = gestureRecognizer.processImageProxy(
                imageProxy = imageProxy,
                isFrontCamera = _useFrontCamera.value
            )
            _detectionResult.value = result

            if (_controlMode.value == ControlMode.GESTURE &&
                _gestureArmed.value &&
                telloState.value.connected
            ) {
                gestureController.onGesture(result.bufferedGestureId)
            }
        } finally {
            imageProxy.close()
        }
    }

    fun setControlMode(mode: ControlMode) {
        _controlMode.value = mode
        if (mode == ControlMode.MANUAL) {
            _gestureArmed.value = false
            telloClient.sendRcControl(0, 0, 0, 0)
        }
        gestureRecognizer.clearBuffer()
        gestureController.reset()
    }

    fun toggleGestureArmed() {
        val next = !_gestureArmed.value
        _gestureArmed.value = next
        gestureRecognizer.clearBuffer()
        gestureController.reset()
        if (!next) {
            telloClient.sendRcControl(0, 0, 0, 0)
        }
    }

    fun toggleCameraFacing() {
        _useFrontCamera.value = !_useFrontCamera.value
        gestureRecognizer.clearBuffer()
    }

    fun setVideoSource(source: VideoSource) {
        _videoSource.value = source
    }

    fun toggleSimulatorMode() {
        val next = !telloState.value.simulatorMode
        telloClient.setSimulatorMode(next)
        gestureController.reset()
    }

    fun connectTello() {
        wifiBinder.bindToCurrentWifi()
        telloClient.connect()
    }

    fun disconnectTello() {
        _gestureArmed.value = false
        telloClient.disconnect()
        videoReceiver.stop()
    }

    fun bindWifi() {
        wifiBinder.bindToCurrentWifi()
    }

    fun openWifiSettings() {
        wifiBinder.openWifiSettings()
    }

    override fun onCleared() {
        super.onCleared()
        videoReceiver.stop()
        telloClient.disconnect()
        wifiBinder.stop()
        gestureRecognizer.close()
    }
}
