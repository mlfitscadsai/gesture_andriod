package com.example.tellogesturecontrol.tello

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class TelloState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val simulatorMode: Boolean = false,
    val inFlight: Boolean = false,
    val battery: Int? = null,
    val heightCm: Int = 0,
    val tempC: Int? = null,
    val flightTimeSec: Int = 0,
    val lastCommand: String = "",
    val lastResponse: String = "",
    val lastRc: RcVelocities = RcVelocities(),
    val statusMessage: String = "Disconnected"
)

data class RcVelocities(
    val leftRight: Int = 0,
    val forwardBack: Int = 0,
    val upDown: Int = 0,
    val yaw: Int = 0
)

/**
 * DJI Tello SDK 2.0 UDP client (`192.168.10.1:8889` commands, `0.0.0.0:8890` state telemetry)
 * with built-in Simulator Mode for offline gesture/UI testing without a physical drone.
 */
class TelloClient {

    companion object {
        const val TELLO_IP = "192.168.10.1"
        const val COMMAND_PORT = 8889
        const val STATE_PORT = 8890
        private const val SOCKET_TIMEOUT_MS = 4000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    private val _state = MutableStateFlow(TelloState())
    val state: StateFlow<TelloState> = _state.asStateFlow()

    private var commandSocket: DatagramSocket? = null
    private var stateSocket: DatagramSocket? = null
    private var stateJob: Job? = null
    private var telloAddress: InetAddress? = null

    fun setSimulatorMode(enabled: Boolean) {
        if (enabled) {
            disconnect()
            _state.value = TelloState(
                connected = true,
                simulatorMode = true,
                battery = 92,
                heightCm = 0,
                tempC = 38,
                statusMessage = "Simulator Active"
            )
        } else {
            _state.value = TelloState(statusMessage = "Disconnected")
        }
    }

    fun connect() {
        if (_state.value.simulatorMode) return
        scope.launch {
            _state.update {
                it.copy(
                    connecting = true,
                    connected = false,
                    statusMessage = "Connecting to $TELLO_IP..."
                )
            }
            try {
                closeSockets()
                telloAddress = InetAddress.getByName(TELLO_IP)
                commandSocket = DatagramSocket().apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                }
                startStateListener()

                val resp = sendCommandInternal("command")
                if (resp.equals("ok", ignoreCase = true)) {
                    _state.update {
                        it.copy(
                            connected = true,
                            connecting = false,
                            statusMessage = "Connected to Tello"
                        )
                    }
                    sendCommandInternal("streamon")
                    val batResp = sendCommandInternal("battery?")
                    batResp.toIntOrNull()?.let { bat ->
                        _state.update { it.copy(battery = bat) }
                    }
                } else {
                    _state.update {
                        it.copy(
                            connected = false,
                            connecting = false,
                            statusMessage = "Tello failed: $resp"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        connected = false,
                        connecting = false,
                        statusMessage = "Connection error: ${e.message ?: "Timeout"}"
                    )
                }
            }
        }
    }

    fun disconnect() {
        stateJob?.cancel()
        stateJob = null
        scope.launch {
            if (_state.value.connected && !_state.value.simulatorMode) {
                try {
                    sendCommandInternal("streamoff")
                } catch (_: Exception) {
                }
            }
            closeSockets()
        }
        _state.value = TelloState(statusMessage = "Disconnected")
    }

    fun takeoff() {
        val current = _state.value
        if (!current.connected) return
        if (current.simulatorMode) {
            _state.update {
                it.copy(
                    inFlight = true,
                    heightCm = 80,
                    lastCommand = "takeoff",
                    lastResponse = "ok (sim)",
                    statusMessage = "In Flight (Simulator)"
                )
            }
            return
        }
        scope.launch {
            val resp = sendCommandInternal("takeoff")
            if (resp.equals("ok", ignoreCase = true)) {
                _state.update { it.copy(inFlight = true, statusMessage = "In Flight") }
            }
        }
    }

    fun land() {
        val current = _state.value
        if (!current.connected) return
        if (current.simulatorMode) {
            _state.update {
                it.copy(
                    inFlight = false,
                    heightCm = 0,
                    lastCommand = "land",
                    lastResponse = "ok (sim)",
                    lastRc = RcVelocities(),
                    statusMessage = "Landed (Simulator)"
                )
            }
            return
        }
        scope.launch {
            val resp = sendCommandInternal("land")
            if (resp.equals("ok", ignoreCase = true)) {
                _state.update {
                    it.copy(
                        inFlight = false,
                        lastRc = RcVelocities(),
                        statusMessage = "Landed"
                    )
                }
            }
        }
    }

    fun emergency() {
        val current = _state.value
        if (!current.connected) return
        if (current.simulatorMode) {
            _state.update {
                it.copy(
                    inFlight = false,
                    heightCm = 0,
                    lastCommand = "emergency",
                    lastResponse = "ok (sim)",
                    lastRc = RcVelocities(),
                    statusMessage = "EMERGENCY STOP (Sim)"
                )
            }
            return
        }
        scope.launch {
            sendCommandInternal("emergency")
            _state.update {
                it.copy(
                    inFlight = false,
                    lastRc = RcVelocities(),
                    statusMessage = "EMERGENCY STOP"
                )
            }
        }
    }

    fun sendRcControl(leftRight: Int, forwardBack: Int, upDown: Int, yaw: Int) {
        val current = _state.value
        if (!current.connected) return
        val lr = leftRight.coerceIn(-100, 100)
        val fb = forwardBack.coerceIn(-100, 100)
        val ud = upDown.coerceIn(-100, 100)
        val yw = yaw.coerceIn(-100, 100)
        val cmd = "rc $lr $fb $ud $yw"
        val rc = RcVelocities(lr, fb, ud, yw)

        if (current.simulatorMode) {
            val nextHeight = if (current.inFlight) {
                (current.heightCm + ud / 5).coerceIn(20, 300)
            } else {
                current.heightCm
            }
            _state.update {
                it.copy(
                    lastCommand = cmd,
                    lastResponse = "rc sent (sim)",
                    lastRc = rc,
                    heightCm = nextHeight
                )
            }
            return
        }

        scope.launch {
            try {
                val socket = commandSocket ?: return@launch
                val addr = telloAddress ?: return@launch
                val bytes = cmd.toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(bytes, bytes.size, addr, COMMAND_PORT))
                _state.update { it.copy(lastCommand = cmd, lastRc = rc) }
            } catch (_: Exception) {
            }
        }
    }

    fun move(direction: String, distanceCm: Int = 30) {
        val current = _state.value
        if (!current.connected) return
        val cmd = "$direction ${distanceCm.coerceIn(20, 500)}"
        if (current.simulatorMode) {
            _state.update {
                it.copy(
                    lastCommand = cmd,
                    lastResponse = "ok (sim)"
                )
            }
            return
        }
        scope.launch { sendCommandInternal(cmd) }
    }

    fun rotate(clockwise: Boolean, degrees: Int = 30) {
        val current = _state.value
        if (!current.connected) return
        val dir = if (clockwise) "cw" else "ccw"
        val cmd = "$dir ${degrees.coerceIn(1, 360)}"
        if (current.simulatorMode) {
            _state.update {
                it.copy(
                    lastCommand = cmd,
                    lastResponse = "ok (sim)"
                )
            }
            return
        }
        scope.launch { sendCommandInternal(cmd) }
    }

    private suspend fun sendCommandInternal(cmd: String): String = commandMutex.withLock {
        val socket = commandSocket ?: return "no socket"
        val addr = telloAddress ?: return "no address"
        return try {
            val bytes = cmd.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes, bytes.size, addr, COMMAND_PORT))
            _state.update { it.copy(lastCommand = cmd) }

            val buf = ByteArray(1024)
            val respPacket = DatagramPacket(buf, buf.size)
            socket.receive(respPacket)
            val response = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8).trim()
            _state.update { it.copy(lastResponse = response) }
            response
        } catch (e: SocketTimeoutException) {
            _state.update { it.copy(lastResponse = "timeout") }
            "timeout"
        } catch (e: Exception) {
            val msg = e.message ?: "error"
            _state.update { it.copy(lastResponse = msg) }
            msg
        }
    }

    private fun startStateListener() {
        stateJob?.cancel()
        stateJob = scope.launch {
            try {
                stateSocket = DatagramSocket(STATE_PORT)
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)
                while (isActive) {
                    stateSocket?.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    parseTelemetry(raw)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun parseTelemetry(raw: String) {
        var bat: Int? = null
        var height: Int? = null
        var tempH: Int? = null
        var time: Int? = null

        for (pair in raw.split(';')) {
            val idx = pair.indexOf(':')
            if (idx <= 0) continue
            val key = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim().toIntOrNull() ?: continue
            when (key) {
                "bat" -> bat = value
                "h" -> height = value
                "temph" -> tempH = value
                "time" -> time = value
            }
        }

        _state.update { prev ->
            prev.copy(
                battery = bat ?: prev.battery,
                heightCm = height ?: prev.heightCm,
                tempC = tempH ?: prev.tempC,
                flightTimeSec = time ?: prev.flightTimeSec
            )
        }
    }

    private fun closeSockets() {
        try {
            commandSocket?.close()
        } catch (_: Exception) {
        }
        try {
            stateSocket?.close()
        } catch (_: Exception) {
        }
        commandSocket = null
        stateSocket = null
    }
}
