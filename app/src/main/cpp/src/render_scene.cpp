#include "render_scene.h"
#include <cstring>
#include "include/core/SkColor.h"
#include "include/core/SkMatrix.h"
#include "include/core/SkPaint.h"

namespace figtab {

namespace {
struct Reader {
    const uint8_t* p; const uint8_t* end;
    bool read(void* dst, size_t n) {
        if (size_t(end - p) < n) return false;
        std::memcpy(dst, p, n); p += n; return true;
    }
    size_t left() const { return size_t(end - p); }
};
}  // namespace

bool RenderScene::applyPatch(const uint8_t* data, size_t len) {
    Reader r{data, data + len};
    PatchHeader h;
    if (!r.read(&h, sizeof h) || h.magic != kPatchMagic || h.version != kPatchVersion) return false;
    for (uint32_t i = 0; i < h.opCount; ++i) {
        OpHeader oh;
        if (!r.read(&oh, sizeof oh) || r.left() < oh.payloadBytes) return false;
        Reader body{r.p, r.p + oh.payloadBytes};
        r.p += oh.payloadBytes;   // always skip by declared size ⇒ forward compatible
        switch (oh.op) {
            case PatchOp::UpsertNode: {
                NodeRecord rec;
                if (!body.read(&rec, sizeof rec)) return false;
                RenderNode& n = nodes_[rec.id];
                n.id = rec.id; n.kind = rec.kind; n.flags = rec.flags; n.blend = rec.blendMode;
                std::memcpy(n.m, rec.m, sizeof n.m);
                n.w = rec.width; n.h = rec.height; n.opacity = rec.opacity;
                break;
            }
            case PatchOp::RemoveNode: {
                uint64_t id;
                if (!body.read(&id, 8)) return false;
                nodes_.erase(id);
                break;
            }
            case PatchOp::SetChildren: {
                uint64_t parent; uint32_t n;
                if (!body.read(&parent, 8) || !body.read(&n, 4)) return false;
                if (body.left() < size_t(n) * 8) return false;
                RenderNode& node = nodes_[parent];
                node.children.resize(n);
                if (n) std::memcpy(node.children.data(), body.p, size_t(n) * 8);
                break;
            }
            case PatchOp::SetRoots: {
                uint32_t n;
                if (!body.read(&n, 4)) return false;
                if (body.left() < size_t(n) * 8) return false;
                roots_.resize(n);
                if (n) std::memcpy(roots_.data(), body.p, size_t(n) * 8);
                break;
            }
            case PatchOp::ClearAll:
                nodes_.clear(); roots_.clear();
                break;
            default:
                break;  // unknown op: skipped
        }
    }
    return true;
}

void RenderScene::draw(SkCanvas* canvas, const SkRect& cull) const {
    for (uint64_t id : roots_) {
        auto it = nodes_.find(id);
        if (it != nodes_.end()) drawNode(canvas, it->second, cull);
    }
}

void RenderScene::drawNode(SkCanvas* canvas, const RenderNode& n, const SkRect& cull) const {
    if (!(n.flags & kFlagVisible)) return;
    SkAutoCanvasRestore acr(canvas, true);
    // Skia MakeAll(scaleX, skewX, transX, skewY, scaleY, transY, p0, p1, p2):
    // our (a,b,c,d,tx,ty) → scaleX=a, skewY=b, skewX=c, scaleY=d.
    canvas->concat(SkMatrix::MakeAll(n.m[0], n.m[2], n.m[4], n.m[1], n.m[3], n.m[5], 0, 0, 1));

    // Phase 2 replaces this placeholder with real painters (fills, strokes, text, effects) and culling.
    SkPaint p;
    p.setAntiAlias(true);
    switch (static_cast<NodeKind>(n.kind)) {
        case NodeKind::Frame: case NodeKind::Section: case NodeKind::Component:
            p.setColor(SkColorSetARGB(255, 0xF5, 0xF5, 0xF5));
            canvas->drawRect(SkRect::MakeWH(n.w, n.h), p);
            break;
        case NodeKind::Shape:
            p.setColor(SkColorSetARGB(int(255 * n.opacity), 0x4C, 0x8D, 0xF6));
            canvas->drawRect(SkRect::MakeWH(n.w, n.h), p);
            break;
        default:
            break;  // groups etc. draw only their children
    }
    for (uint64_t cid : n.children) {
        auto it = nodes_.find(cid);
        if (it != nodes_.end()) drawNode(canvas, it->second, cull);
    }
}

}  // namespace figtab
