package app.figtab.render

import app.figtab.domain.scene.*

/**
 * Encodes domain scene state into the binary patch format defined in native scene_patch.h.
 * Layout (little-endian):
 *   PatchHeader {u32 magic, u32 version, u32 opCount, u32 reserved}            = 16 bytes
 *   per op: OpHeader {u32 op, u32 payloadBytes} + payload
 *   UpsertNode payload = NodeRecord = 56 bytes:
 *     u64 id, u32 kind, u32 flags, f32 m[6], f32 w, f32 h, f32 opacity, u32 blend
 */
object ScenePatchEncoder {
    private const val MAGIC = 0x46545031
    private const val VERSION = 1
    private const val OP_UPSERT = 1
    private const val OP_SET_CHILDREN = 3
    private const val OP_SET_ROOTS = 4
    private const val OP_CLEAR_ALL = 5
    private const val NODE_RECORD_BYTES = 56

    private const val FLAG_VISIBLE = 1
    private const val FLAG_CLIPS = 2
    private const val FLAG_EFFECTS = 4

    /** Full snapshot: clear, then upsert every node, then child lists and roots. */
    fun encodeFull(canvas: Canvas, out: PatchBuffer) {
        out.reset()
        var ops = 1 + 1 // ClearAll + SetRoots
        for (n in canvas.nodes.values) {
            ops++
            if (n is ContainerNode) ops++
        }
        out.ensure(16)
        out.buffer.putInt(MAGIC).putInt(VERSION).putInt(ops).putInt(0)

        header(out, OP_CLEAR_ALL, 0)

        for (n in canvas.nodes.values) {
            header(out, OP_UPSERT, NODE_RECORD_BYTES)
            out.ensure(NODE_RECORD_BYTES)
            val buf = out.buffer
            var flags = 0
            if (n.visible) flags = flags or FLAG_VISIBLE
            val clips = when (n) {
                is FrameNode -> n.clipsContent
                is ComponentNode -> n.clipsContent
                else -> false
            }
            if (clips) flags = flags or FLAG_CLIPS
            if (n.effects.isNotEmpty()) flags = flags or FLAG_EFFECTS
            val t = n.transform
            buf.putLong(n.id.raw).putInt(kindOf(n)).putInt(flags)
            buf.putFloat(t.a).putFloat(t.b).putFloat(t.c).putFloat(t.d).putFloat(t.tx).putFloat(t.ty)
            buf.putFloat(n.width).putFloat(n.height).putFloat(n.opacity).putInt(n.blendMode.ordinal)
        }

        for (n in canvas.nodes.values) {
            if (n !is ContainerNode) continue
            val kids = n.children
            header(out, OP_SET_CHILDREN, 8 + 4 + 8 * kids.size)
            out.ensure(8 + 4 + 8 * kids.size)
            out.buffer.putLong(n.id.raw).putInt(kids.size)
            for (k in kids) out.buffer.putLong(k.raw)
        }

        header(out, OP_SET_ROOTS, 4 + 8 * canvas.roots.size)
        out.ensure(4 + 8 * canvas.roots.size)
        out.buffer.putInt(canvas.roots.size)
        for (r in canvas.roots) out.buffer.putLong(r.raw)
    }

    private fun header(out: PatchBuffer, op: Int, payloadBytes: Int) {
        out.ensure(8)
        out.buffer.putInt(op).putInt(payloadBytes)
    }

    private fun kindOf(n: SceneNode): Int = when (n) {
        is SectionNode -> 1
        is FrameNode -> 2
        is GroupNode -> 3
        is ShapeNode -> 4
        is VectorNetworkNode -> 5
        is TextNode -> 6
        is ComponentNode -> 7
        is InstanceNode -> 8
    }
}
