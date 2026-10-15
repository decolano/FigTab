package app.figtab.render

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Growable little-endian DIRECT buffer for scene patches. Reused across frames to avoid garbage. */
class PatchBuffer(initialCapacity: Int = 64 * 1024) {
    var buffer: ByteBuffer = allocate(initialCapacity)
        private set

    val length: Int get() = buffer.position()

    fun reset() { buffer.clear() }

    fun ensure(extra: Int) {
        if (buffer.remaining() >= extra) return
        var cap = buffer.capacity() * 2
        while (cap - buffer.position() < extra) cap *= 2
        val bigger = allocate(cap)
        buffer.flip()
        bigger.put(buffer)
        buffer = bigger
    }

    private fun allocate(cap: Int): ByteBuffer = ByteBuffer.allocateDirect(cap).order(ByteOrder.LITTLE_ENDIAN)
}
