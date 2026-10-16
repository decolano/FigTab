#pragma once
#include <cstddef>
#include <cstdint>
#include <unordered_map>
#include <vector>
#include "include/core/SkCanvas.h"
#include "include/core/SkPaint.h"
#include "include/core/SkPath.h"
#include "include/core/SkRRect.h"
#include "include/core/SkRect.h"
#include "scene_patch.h"

namespace figtab {

enum class Geom : uint8_t { None, Rect, RRect, Path };

struct StrokeSpec {
    bool has = false;
    float weight = 1.f, miter = 4.f;
    uint32_t align = 1, cap = 0, join = 0;
    std::vector<float> dash;
    std::vector<SkPaint> paints;
};

struct EffectSpec {
    uint32_t type = 0;   // 0 drop shadow, 1 inner shadow, 2 layer blur, 3 background blur
    uint32_t color = 0;
    float dx = 0, dy = 0, blur = 0, spread = 0;
};

struct RenderNode {
    uint64_t id = 0;
    uint32_t kind = 0, flags = 0, blend = 0;
    float m[6] = {1, 0, 0, 1, 0, 0};
    float w = 0, h = 0, opacity = 1;
    std::vector<uint64_t> children;   // back -> front

    // Cached at upsert time so drawing allocates nothing.
    Geom geom = Geom::None;
    bool isLine = false;
    SkRect rect = SkRect::MakeEmpty();
    SkRRect rrect;
    SkPath path;
    std::vector<SkPaint> fills;
    StrokeSpec stroke;
    std::vector<EffectSpec> effects;
    float pad = 1.f;                   // how far painting can extend past [0,w]x[0,h] (stroke, shadows, blur)
};

// Owned and mutated ONLY by the render thread.
class RenderScene {
public:
    bool applyPatch(const uint8_t* data, size_t len);   // false on malformed input
    void draw(SkCanvas* canvas, const SkRect& visibleWorldRect) const;
private:
    void drawNode(SkCanvas*, const RenderNode&, const SkRect& parentSpaceCull) const;
    std::unordered_map<uint64_t, RenderNode> nodes_;
    std::vector<uint64_t> roots_;
};

}  // namespace figtab
