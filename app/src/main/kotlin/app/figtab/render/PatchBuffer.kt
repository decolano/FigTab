package app.figtab.render

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Growable little-endian DIRECT buffer for scene patches. Reused across frames to avoid garbage. */
class PatchBuffer(initialCapacity: Int = 64 * 1024) {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set

    val length: Int get() = buffer.position()

    fun reset() { buffer.clear() }

    private fun ensure(extra: Int) {
        if (buffer.remaining() >= extra) return
        var cap = buffer.capacity() * 2
        while (cap - buffer.position() < extra) cap *= 2
        val bigger = allocate(cap)
        buffer.flip()
        bigger.put(buffer)
        buffer = bigger
    }

    fun putInt(v: Int): PatchBuffer { ensure(4); buffer.putInt(v); return this }
    fun putLong(v: Long): PatchBuffer { ensure(8); buffer.putLong(v); return this }
    fun putFloat(v: Float): PatchBuffer { ensure(4); buffer.putFloat(v); return this }

    /** Overwrite an int at an absolute position (used to back-fill op sizes). */
    fun setInt(position: Int, v: Int) { buffer.putInt(position, v) }

    private fun allocate(cap: Int): ByteBuffer = ByteBuffer.allocateDirect(cap).order(ByteOrder.LITTLE_ENDIAN)
}
