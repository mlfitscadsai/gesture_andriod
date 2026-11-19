package com.example.tellogesturecontrol.gesture

/**
 * Sliding-window majority-vote buffer matching `GestureBuffer` in
 * `tello-gesture-control/gestures/gesture_recognition.py`.
 */
class GestureBuffer(private val bufferLen: Int = 5) {
    private val buffer = ArrayDeque<Int>(bufferLen)

    @Synchronized
    fun addGesture(gestureId: Int) {
        if (buffer.size >= bufferLen) {
            buffer.removeFirst()
        }
        buffer.addLast(gestureId)
    }

    /**
     * Returns the dominant gesture ID if it appears at least `bufferLen - 1` times,
     * clearing the buffer upon emission; otherwise returns null.
     */
    @Synchronized
    fun getGesture(): Int? {
        if (buffer.isEmpty()) return null
        val counts = HashMap<Int, Int>()
        var bestId = buffer.first()
        var bestCount = 0
        for (id in buffer) {
            val c = (counts[id] ?: 0) + 1
            counts[id] = c
            if (c > bestCount) {
                bestCount = c
                bestId = id
            }
        }
        return if (bestCount >= bufferLen - 1) {
            buffer.clear()
            bestId
        } else {
            null
        }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
