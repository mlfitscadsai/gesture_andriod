package com.example.tellogesturecontrol.tello

/**
 * Maps buffered gesture IDs (`0..7`, `-1`) to Tello RC velocities and commands,
 * matching `TelloGestureController` in `tello-gesture-control/gestures/tello_gesture_controller.py`.
 */
class TelloGestureController(private val telloClient: TelloClient) {

    private var forwBackVelocity = 0
    private var upDownVelocity = 0
    private var leftRightVelocity = 0
    private var yawVelocity = 0
    private var isLanding = false

    fun reset() {
        forwBackVelocity = 0
        upDownVelocity = 0
        leftRightVelocity = 0
        yawVelocity = 0
        isLanding = false
    }

    fun onGesture(gestureId: Int?) {
        if (gestureId == null) return
        if (isLanding) {
            if (gestureId == 1) { // Stop resets landing lock
                isLanding = false
            } else {
                return
            }
        }

        when (gestureId) {
            0 -> { // Forward
                forwBackVelocity = 30
            }
            1 -> { // Stop
                forwBackVelocity = 0
                upDownVelocity = 0
                leftRightVelocity = 0
                yawVelocity = 0
            }
            2 -> { // Up
                upDownVelocity = 25
            }
            3 -> { // Land
                isLanding = true
                forwBackVelocity = 0
                upDownVelocity = 0
                leftRightVelocity = 0
                yawVelocity = 0
                telloClient.land()
            }
            4 -> { // Down
                upDownVelocity = -25
            }
            5 -> { // Back
                forwBackVelocity = -30
            }
            6 -> { // Left
                leftRightVelocity = 20
            }
            7 -> { // Right
                leftRightVelocity = -20
            }
            -1 -> { // No hand
                forwBackVelocity = 0
                upDownVelocity = 0
                leftRightVelocity = 0
                yawVelocity = 0
            }
        }

        telloClient.sendRcControl(
            leftRight = leftRightVelocity,
            forwardBack = forwBackVelocity,
            upDown = upDownVelocity,
            yaw = yawVelocity
        )
    }
}
