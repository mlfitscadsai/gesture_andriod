package com.example.tellogesturecontrol.tello

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * Receives Tello's raw H.264 UDP video stream on port `11111`,
 * reassembles NAL units, and decodes them to a [Surface] via [MediaCodec].
 */
class TelloVideoReceiver {

    companion object {
        const val VIDEO_PORT = 11111
        private const val VIDEO_WIDTH = 960
        private const val VIDEO_HEIGHT = 720
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiveJob: Job? = null
    private var socket: DatagramSocket? = null
    private var codec: MediaCodec? = null

    fun start(surface: Surface) {
        stop()
        receiveJob = scope.launch {
            try {
                val mediaFormat = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    VIDEO_WIDTH,
                    VIDEO_HEIGHT
                )
                val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(mediaFormat, surface, null, 0)
                    start()
                }
                codec = decoder

                socket = DatagramSocket(VIDEO_PORT).apply {
                    receiveBufferSize = 65536 * 4
                }

                val packetBuffer = ByteArray(2048)
                val packet = DatagramPacket(packetBuffer, packetBuffer.size)
                val nalBuffer = ByteArrayOutputStream(65536)
                val bufferInfo = MediaCodec.BufferInfo()

                while (isActive) {
                    socket?.receive(packet)
                    val len = packet.length
                    if (len <= 0) continue

                    nalBuffer.write(packet.data, 0, len)
                    // Tello sends 1460-byte UDP chunks; a chunk < 1460 bytes marks end of frame.
                    if (len < 1460) {
                        val frameData = nalBuffer.toByteArray()
                        nalBuffer.reset()
                        decodeFrame(decoder, frameData, bufferInfo)
                    } else if (nalBuffer.size() > 512 * 1024) {
                        nalBuffer.reset()
                    }
                }
            } catch (_: Exception) {
            } finally {
                releaseCodec()
            }
        }
    }

    private fun decodeFrame(
        decoder: MediaCodec,
        frameData: ByteArray,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        try {
            val inputIndex = decoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(frameData)
                decoder.queueInputBuffer(
                    inputIndex,
                    0,
                    frameData.size,
                    System.nanoTime() / 1000,
                    0
                )
            }

            var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                decoder.releaseOutputBuffer(outputIndex, true)
                outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        releaseCodec()
    }

    private fun releaseCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
    }
}
