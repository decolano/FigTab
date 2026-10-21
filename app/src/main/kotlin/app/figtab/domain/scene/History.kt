package app.figtab.domain.scene

/** Undo/redo by snapshot. The scene graph is persistent, so a snapshot is just a reference. */
data class Snapshot(val canvas: Canvas, val selection: Set<NodeId>)

class History(private val limit: Int = 200) {
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(before: Snapshot) {
        undoStack.addLast(before)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: Snapshot): Snapshot? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    fun redo(current: Snapshot): Snapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }
}
