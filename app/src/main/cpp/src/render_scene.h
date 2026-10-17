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
#include "include/core/SkRefCnt.h"
#include "include/core/SkTextBlob.h"
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

struct TextPiece {
    sk_sp<SkTextBlob> blob;
    float x = 0, y = 0;     // origin of the blob's baseline, node-local
    int run = 0;            // index into RenderNode::textFills
};

struct TextDeco {           // underline / strikethrough rectangle
    float x = 0, y = 0, w = 0, thick = 1;
    int run = 0;
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
    // Text (laid out once at upsert time)
    bool hasText = false;
    std::vector<TextPiece> textPieces;
    std::vector<TextDeco> textDecos;
    std::vector<std::vector<SkPaint>> textFills;   // per run

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
