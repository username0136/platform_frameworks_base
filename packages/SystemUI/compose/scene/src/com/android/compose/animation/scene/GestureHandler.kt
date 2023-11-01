package com.android.compose.animation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

interface GestureHandler {
    val draggable: DraggableHandler
    val nestedScroll: NestedScrollHandler
}

interface DraggableHandler {
    fun onDragStarted(startedPosition: Offset, pointersDown: Int = 1)
    fun onDelta(pixels: Float)
    fun onDragStopped(velocity: Float)
}

interface NestedScrollHandler {
    val connection: NestedScrollConnection
}
