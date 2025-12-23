package com.dam2.flashdownloader.ui.utils

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Extension para habilitar Drag & Drop en LazyColumn sin librerías externas.
 * Implementación simplificada y robusta.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(
            state = lazyListState,
            onMove = onMove,
            scope = scope
        )
    }
    return state
}

class DragDropState(
    internal val state: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var draggingItemInitialOffset by mutableStateOf(0)
        private set

    internal var draggingItemOffset by mutableStateOf(0f)
        private set

    fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.y.toInt() in item.offset..(item.offset + item.size)
            }?.let { item ->
                draggingItemIndex = item.index
                draggingItemInitialOffset = item.offset
                draggingItemOffset = 0f
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }

    fun onDrag(change: PointerInputChange, dragAmount: Offset) {
        val draggedIndex = draggingItemIndex ?: return
        draggingItemOffset += dragAmount.y
        
        val draggedItemInfo = state.layoutInfo.visibleItemsInfo
            .find { it.index == draggedIndex } ?: return

        val currentMidpoint = draggingItemInitialOffset + draggingItemOffset + (draggedItemInfo.size / 2)
        
        // Buscar el ítem sobre el que estamos pasando
        val targetItem = state.layoutInfo.visibleItemsInfo
            .find { item ->
                item.index != draggedIndex &&
                currentMidpoint.toInt() in item.offset..(item.offset + item.size)
            }
            
        if (targetItem != null) {
            val targetIndex = targetItem.index
            if (targetIndex != draggedIndex) {
                onMove(draggedIndex, targetIndex)
                draggingItemIndex = targetIndex
                // Reset visual offset to prevent jitter, but this is simplified. 
                // In a perfect world we would animate the swap.
                // For now, accepting the snap.
                draggingItemInitialOffset += (targetItem.offset - draggedItemInfo.offset)
                draggingItemOffset -= (targetItem.offset - draggedItemInfo.offset)
            }
        }
    }
}

fun Modifier.dragGestureHandler(
    state: DragDropState
): Modifier = composed {
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset -> state.onDragStart(offset) },
            onDrag = { change, dragAmount -> 
                change.consume()
                state.onDrag(change, dragAmount)
            },
            onDragEnd = { state.onDragInterrupted() },
            onDragCancel = { state.onDragInterrupted() }
        )
    }
}

/**
 * Modifier para aplicar feedback visual al item siendo arrastrado
 */
fun Modifier.dragContainer(
    dragDropState: DragDropState,
    index: Int
): Modifier = composed {
    val isDragging = dragDropState.draggingItemIndex == index
    
    // Animación de elevación
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 2.dp,
        label = "drag_elevation"
    )
    
    // Offset visual para el item arrastrado
    val offsetY = if (isDragging) {
        dragDropState.draggingItemOffset
    } else {
        0f
    }
    
    this
        .graphicsLayer {
            // Aplicar offset vertical
            translationY = offsetY
            // Añadir sombra/elevación
            shadowElevation = elevation.toPx()
            // Reducir opacidad ligeramente cuando se arrastra
            alpha = if (isDragging) 0.9f else 1f
        }
}
