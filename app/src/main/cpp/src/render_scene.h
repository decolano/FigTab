#pragma once
#include <cstddef>
#include <cstdint>
#include <unordered_map>
#include <vector>
#include "include/core/SkCanvas.h"
#include "include/core/SkRect.h"
#include "scene_patch.h"

namespace figtab {

struct RenderNode {
    uint64_t id = 0;
    uint32_t kind = 0, flags = 0, blend = 0;
    float m[6] = {1, 0, 0, 1, 0, 0};
    float w = 0, h = 0, opacity = 1;
    std::vector<uint64_t> children;   // back → front
};

// Owned and mutated ONLY by the render thread.
class RenderScene {
public:
    bool applyPatch(const uint8_t* data, size_t len);   // false on malformed input
    void draw(SkCanvas* canvas, const SkRect& visibleWorldRect) const;
private:
    void drawNode(SkCanvas*, const RenderNode&, const SkRect& cull) const;
    std::unordered_map<uint64_t, RenderNode> nodes_;
    std::vector<uint64_t> roots_;
};

}  // namespace figtab
