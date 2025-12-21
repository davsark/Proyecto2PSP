package com.dam2.flashdownloader.ui.utils

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

/**
 * Estado para manejar drag and drop en LazyColumn
 */
@Stable
class DragDropState(
    private val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    
    var draggedOffset by mutableStateOf(Offset.Zero)
        private set
    
    private var draggedItem: LazyListItemInfo? = null
    private var overscrollJob: Job? = null
    
    fun onDragStart(index: Int) {
        draggedIndex = index
        draggedItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }
    
    fun onDrag(offset: Offset) {
        draggedOffset += offset
        
        val draggedItem = this.draggedItem ?: return
        val startOffset = draggedItem.offset + draggedOffset.y
        val endOffset = startOffset + draggedItem.size
        
        val hoveredItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val itemMiddle = item.offset + item.size / 2f
            itemMiddle in startOffset..endOffset && item.index != draggedIndex
        }
        
        if (hoveredItem != null) {
            val draggedIdx = draggedIndex ?: return
            val targetIdx = hoveredItem.index
            
            onMove(draggedIdx, targetIdx)
            draggedIndex = targetIdx
            this.draggedItem = hoveredItem
            draggedOffset = Offset.Zero
        }
    }
    
    fun onDragEnd() {
        draggedIndex = null
        draggedOffset = Offset.Zero
        draggedItem = null
        overscrollJob?.cancel()
    }
}

/**
 * Modifier para hacer un item draggable
 */
fun Modifier.draggableItem(
    dragDropState: DragDropState,
    index: Int
): Modifier = this.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            dragDropState.onDragStart(index)
        },
        onDrag = { change, offset ->
            change.consume()
            dragDropState.onDrag(offset)
        },
        onDragEnd = {
            dragDropState.onDragEnd()
        },
        onDragCancel = {
            dragDropState.onDragEnd()
        }
    )
}

/**
 * Crea un DragDropState para LazyColumn
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    return remember(lazyListState) {
        DragDropState(lazyListState, onMove)
    }
}
