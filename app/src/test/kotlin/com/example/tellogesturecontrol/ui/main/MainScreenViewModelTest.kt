package com.example.tellogesturecontrol.ui.main

import com.example.tellogesturecontrol.gesture.GestureBuffer
import com.example.tellogesturecontrol.tello.TelloClient
import com.example.tellogesturecontrol.tello.TelloGestureController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun gestureBuffer_emitsAfterMajorityAndClears() {
        val buffer = GestureBuffer(bufferLen = 5)
        buffer.addGesture(0)
        buffer.addGesture(0)
        buffer.addGesture(1)
        assertNull(buffer.getGesture())

        buffer.addGesture(0)
        buffer.addGesture(0)
        // 4 out of 5 are gesture 0 (>= bufferLen - 1)
        assertEquals(0, buffer.getGesture())
        // Buffer is cleared after emission
        assertNull(buffer.getGesture())
    }

    @Test
    fun telloGestureController_mapsGesturesToRcVelocitiesInSimulator() {
        val client = TelloClient()
        client.setSimulatorMode(true)
        client.takeoff()
        val controller = TelloGestureController(client)

        controller.onGesture(0) // Forward -> FB = 30
        assertEquals(30, client.state.value.lastRc.forwardBack)

        controller.onGesture(6) // Left -> LR = 20
        assertEquals(20, client.state.value.lastRc.leftRight)

        controller.onGesture(2) // Up -> UD = 25
        assertEquals(25, client.state.value.lastRc.upDown)

        controller.onGesture(1) // Stop -> all 0
        assertEquals(0, client.state.value.lastRc.forwardBack)
        assertEquals(0, client.state.value.lastRc.leftRight)
        assertEquals(0, client.state.value.lastRc.upDown)

        controller.onGesture(3) // Land
        assertEquals(false, client.state.value.inFlight)
    }
}
