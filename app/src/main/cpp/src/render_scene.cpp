#include "render_scene.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include "include/core/SkBlendMode.h"
#include "include/core/SkColor.h"
#include "include/core/SkImageFilter.h"
#include "include/core/SkMaskFilter.h"
#include "include/core/SkMatrix.h"
#include "include/core/SkPathEffect.h"
#include "include/core/SkShader.h"
#include "include/effects/SkDashPathEffect.h"
#include "include/effects/SkGradientShader.h"
#include "include/effects/SkImageFilters.h"

namespace figtab {

namespace {

constexpr float kPi = 3.14159265358979f;

struct Reader {
    const uint8_t* p; const uint8_t* end;
    bool read(void* dst, size_t n) {
        if (size_t(end - p) < n) return false;
        std::memcpy(dst, p, n); p += n; return true;
    }
    size_t left() const { return size_t(end - p); }
    bool u32(uint32_t& v) { return read(&v, 4); }
    bool f32(float& v) { return read(&v, 4); }
};

SkBlendMode toBlend(uint32_t b) {
    switch (b) {
        case 2:  return SkBlendMode::kMultiply;
        case 3:  return SkBlendMode::kScreen;
        case 4:  return SkBlendMode::kOverlay;
        case 5:  return SkBlendMode::kDarken;
        case 6:  return SkBlendMode::kLighten;
        case 7:  return SkBlendMode::kColorDodge;
        case 8:  return SkBlendMode::kColorBurn;
        case 9:  return SkBlendMode::kHardLight;
        case 10: return SkBlendMode::kSoftLight;
        case 11: return SkBlendMode::kDifference;
        case 12: return SkBlendMode::kExclusion;
        case 13: return SkBlendMode::kHue;
        case 14: return SkBlendMode::kSaturation;
        case 15: return SkBlendMode::kColor;
        case 16: return SkBlendMode::kLuminosity;
        default: return SkBlendMode::kSrcOver;   // 0 pass-through, 1 normal
    }
}

bool readPaint(Reader& r, SkPaint& out) {
    uint32_t type, color, stops;
    float opacity;
    if (!r.u32(type) || !r.f32(opacity) || !r.u32(color) || !r.u32(stops) || stops > 256) return false;
    std::vector<SkColor> cols(stops);
    std::vector<SkScalar> pos(stops);
    for (uint32_t i = 0; i < stops; ++i) {
        uint32_t c; float p;
        if (!r.f32(p) || !r.u32(c)) return false;
        pos[i] = p; cols[i] = c;
    }
    float x[6] = {1, 0, 0, 1, 0, 0};
    if (type != 0) {
        for (float& v : x) if (!r.f32(v)) return false;
    }
    SkPaint paint;
    paint.setAntiAlias(true);
    sk_sp<SkShader> shader;
    if (type != 0 && stops >= 2) {
        const SkMatrix lm = SkMatrix::MakeAll(x[0], x[2], x[4], x[1], x[3], x[5], 0, 0, 1);
        if (type == 1) {
            const SkPoint pts[2] = {SkPoint::Make(0.f, 0.5f), SkPoint::Make(1.f, 0.5f)};
            shader = SkGradientShader::MakeLinear(pts, cols.data(), pos.data(), int(stops), SkTileMode::kClamp, 0, &lm);
        } else {
            shader = SkGradientShader::MakeRadial(SkPoint::Make(0.5f, 0.5f), 0.5f, cols.data(), pos.data(),
                                                  int(stops), SkTileMode::kClamp, 0, &lm);
        }
    }
    if (shader) {
        paint.setShader(shader);
        paint.setAlphaf(opacity);
    } else {
        paint.setColor(static_cast<SkColor>(stops ? cols[0] : color));
        paint.setAlphaf(paint.getAlphaf() * opacity);
    }
    out = paint;
    return true;
}

void buildGeometry(RenderNode& n, uint32_t shape, const float radii[4], uint32_t pts, float inner, const float arc[3]) {
    n.geom = Geom::None;
    n.isLine = false;
    n.path.reset();
    const float w = n.w, h = n.h;
    const SkRect r = SkRect::MakeWH(w, h);
    n.rect = r;
    switch (shape) {
        case 1: {
            const bool rounded = radii[0] > 0 || radii[1] > 0 || radii[2] > 0 || radii[3] > 0;
            if (!rounded) { n.geom = Geom::Rect; break; }
            const SkVector rad[4] = {{radii[0], radii[0]}, {radii[1], radii[1]}, {radii[2], radii[2]}, {radii[3], radii[3]}};
            n.rrect.setRectRadii(r, rad);          // order: UL, UR, LR, LL
            n.geom = n.rrect.isRect() ? Geom::Rect : Geom::RRect;
            break;
        }
        case 2: {
            const float start = arc[0], sweep = arc[1], ir = std::clamp(arc[2], 0.f, 0.99f);
            const bool full = std::fabs(sweep) >= 359.99f;
            const SkRect in = SkRect::MakeXYWH(w * (1 - ir) / 2, h * (1 - ir) / 2, w * ir, h * ir);
            if (full && ir <= 0.f) {
                n.path.addOval(r);
            } else if (full) {
                n.path.setFillType(SkPathFillType::kEvenOdd);
                n.path.addOval(r);
                n.path.addOval(in);
            } else if (ir <= 0.f) {
                n.path.moveTo(w / 2, h / 2);
                n.path.arcTo(r, start, sweep, false);
                n.path.close();
            } else {
                n.path.arcTo(r, start, sweep, true);
                n.path.arcTo(in, start + sweep, -sweep, false);
                n.path.close();
            }
            n.geom = Geom::Path;
            break;
        }
        case 3: case 4: {
            const uint32_t count = std::max<uint32_t>(pts, 3);
            const float cx = w / 2, cy = h / 2, rx = w / 2, ry = h / 2;
            const bool star = (shape == 3);
            const uint32_t verts = star ? count * 2 : count;
            for (uint32_t i = 0; i < verts; ++i) {
                const float ang = -kPi / 2 + (star ? kPi / count : 2 * kPi / count) * i;
                const float rr = (star && (i % 2 == 1)) ? inner : 1.f;
                const float x = cx + rx * rr * std::cos(ang);
                const float y = cy + ry * rr * std::sin(ang);
                if (i == 0) n.path.moveTo(x, y); else n.path.lineTo(x, y);
            }
            n.path.close();
            n.geom = Geom::Path;
            break;
        }
        case 5: {
            n.path.moveTo(0, 0);
            n.path.lineTo(w, 0);
            n.geom = Geom::Path;
            n.isLine = true;
            break;
        }
        default: break;
    }
}

bool decodePayload(Reader& r, RenderNode& n) {
    uint32_t shape, pointCount;
    float radii[4], inner, arc[3];
    if (!r.u32(shape)) return false;
    for (float& v : radii) if (!r.f32(v)) return false;
    if (!r.u32(pointCount) || !r.f32(inner)) return false;
    for (float& v : arc) if (!r.f32(v)) return false;

    uint32_t count;
    n.fills.clear();
    if (!r.u32(count) || count > 64) return false;
    for (uint32_t i = 0; i < count; ++i) {
        SkPaint p;
        if (!readPaint(r, p)) return false;
        n.fills.push_back(p);
    }

    n.stroke = StrokeSpec();
    uint32_t hasStroke;
    if (!r.u32(hasStroke)) return false;
    if (hasStroke) {
        StrokeSpec& s = n.stroke;
        s.has = true;
        uint32_t dashCount, paintCount;
        if (!r.f32(s.weight) || !r.u32(s.align) || !r.u32(s.cap) || !r.u32(s.join) || !r.f32(s.miter) ||
            !r.u32(dashCount) || dashCount > 32) return false;
        s.dash.resize(dashCount);
        for (float& d : s.dash) if (!r.f32(d)) return false;
        if (s.dash.size() % 2 == 1) { const auto copy = s.dash; s.dash.insert(s.dash.end(), copy.begin(), copy.end()); }
        if (!r.u32(paintCount) || paintCount > 16) return false;
        for (uint32_t i = 0; i < paintCount; ++i) {
            SkPaint p;
            if (!readPaint(r, p)) return false;
            s.paints.push_back(p);
        }
    }

    n.effects.clear();
    uint32_t effectCount;
    if (!r.u32(effectCount) || effectCount > 16) return false;
    for (uint32_t i = 0; i < effectCount; ++i) {
        EffectSpec e;
        if (!r.u32(e.type) || !r.u32(e.color) || !r.f32(e.dx) || !r.f32(e.dy) || !r.f32(e.blur) || !r.f32(e.spread)) return false;
        n.effects.push_back(e);
    }

    buildGeometry(n, shape, radii, pointCount, inner, arc);

    float pad = n.stroke.has ? n.stroke.weight : 0.f;
    for (const EffectSpec& e : n.effects) {
        if (e.type <= 1) pad = std::max(pad, e.blur * 3 + std::max(std::fabs(e.dx), std::fabs(e.dy)) + std::max(0.f, e.spread));
        else if (e.type == 2) pad = std::max(pad, e.blur * 3);
    }
    n.pad = std::max(pad, 1.f);
    return true;
}

// ── drawing helpers ──

void drawGeom(SkCanvas* c, const RenderNode& n, const SkPaint& p) {
    switch (n.geom) {
        case Geom::Rect:  c->drawRect(n.rect, p); break;
        case Geom::RRect: c->drawRRect(n.rrect, p); break;
        case Geom::Path:  c->drawPath(n.path, p); break;
        default: break;
    }
}

void clipGeom(SkCanvas* c, const RenderNode& n, SkClipOp op) {
    switch (n.geom) {
        case Geom::Rect:  c->clipRect(n.rect, op, true); break;
        case Geom::RRect: c->clipRRect(n.rrect, op, true); break;
        case Geom::Path:  c->clipPath(n.path, op, true); break;
        default: break;
    }
}

SkPath geomPath(const RenderNode& n) {
    switch (n.geom) {
        case Geom::Rect:  return SkPath::Rect(n.rect);
        case Geom::RRect: return SkPath::RRect(n.rrect);
        case Geom::Path:  return n.path;
        default: return SkPath();
    }
}

float sigmaOf(float blurRadius) { return std::max(blurRadius * 0.5f, 0.f); }

void drawDropShadow(SkCanvas* c, const RenderNode& n, const EffectSpec& e) {
    SkPaint sp;
    sp.setAntiAlias(true);
    sp.setColor(static_cast<SkColor>(e.color));
    if (e.blur > 0) sp.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigmaOf(e.blur)));
    if (e.spread > 0) { sp.setStyle(SkPaint::kStrokeAndFill_Style); sp.setStrokeWidth(e.spread * 2); }
    c->save();
    c->translate(e.dx, e.dy);
    drawGeom(c, n, sp);
    c->restore();
}

void drawInnerShadow(SkCanvas* c, const RenderNode& n, const EffectSpec& e) {
    c->save();
    clipGeom(c, n, SkClipOp::kIntersect);
    SkRect big = SkRect::MakeWH(n.w, n.h);
    big.outset(e.blur * 3 + std::fabs(e.dx) + std::fabs(e.dy) + 4.f, e.blur * 3 + std::fabs(e.dx) + std::fabs(e.dy) + 4.f);
    SkPath ring;
    ring.setFillType(SkPathFillType::kEvenOdd);
    ring.addRect(big);
    ring.addPath(geomPath(n), e.dx, e.dy);
    SkPaint sp;
    sp.setAntiAlias(true);
    sp.setColor(static_cast<SkColor>(e.color));
    if (e.blur > 0) sp.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigmaOf(e.blur)));
    c->drawPath(ring, sp);
    c->restore();
}

void drawStroke(SkCanvas* c, const RenderNode& n) {
    const StrokeSpec& s = n.stroke;
    if (!s.has || n.geom == Geom::None) return;
    const uint32_t align = n.isLine ? 1u : s.align;   // open paths only support center
    for (const SkPaint& base : s.paints) {
        SkPaint sp = base;
        sp.setStyle(SkPaint::kStroke_Style);
        sp.setStrokeWidth(align == 1 ? s.weight : s.weight * 2);
        sp.setStrokeMiter(s.miter);
        sp.setStrokeCap(s.cap == 1 ? SkPaint::kRound_Cap : s.cap == 2 ? SkPaint::kSquare_Cap : SkPaint::kButt_Cap);
        sp.setStrokeJoin(s.join == 2 ? SkPaint::kRound_Join : s.join == 1 ? SkPaint::kBevel_Join : SkPaint::kMiter_Join);
        if (s.dash.size() >= 2 && s.dash.size() % 2 == 0) {
            sp.setPathEffect(SkDashPathEffect::Make(s.dash.data(), int(s.dash.size()), 0.f));
        }
        if (align == 1) {
            drawGeom(c, n, sp);
        } else {
            c->save();
            clipGeom(c, n, align == 0 ? SkClipOp::kIntersect : SkClipOp::kDifference);
            drawGeom(c, n, sp);
            c->restore();
        }
    }
}

}  // namespace

bool RenderScene::applyPatch(const uint8_t* data, size_t len) {
    Reader r{data, data + len};
    PatchHeader h;
    if (!r.read(&h, sizeof h) || h.magic != kPatchMagic || h.version != kPatchVersion) return false;
    for (uint32_t i = 0; i < h.opCount; ++i) {
        OpHeader oh;
        if (!r.read(&oh, sizeof oh) || r.left() < oh.payloadBytes) return false;
        Reader body{r.p, r.p + oh.payloadBytes};
        r.p += oh.payloadBytes;   // always skip by declared size => forward compatible
        switch (oh.op) {
            case PatchOp::UpsertNode: {
                NodeRecord rec;
                if (!body.read(&rec, sizeof rec)) return false;
                RenderNode& n = nodes_[rec.id];
                n.id = rec.id; n.kind = rec.kind; n.flags = rec.flags; n.blend = rec.blendMode;
                std::memcpy(n.m, rec.m, sizeof n.m);
                n.w = rec.width; n.h = rec.height; n.opacity = rec.opacity;
                if (!decodePayload(body, n)) return false;
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
                if (!body.read(&parent, 8) || !body.u32(n)) return false;
                if (body.left() < size_t(n) * 8) return false;
                RenderNode& node = nodes_[parent];
                node.children.resize(n);
                if (n) std::memcpy(node.children.data(), body.p, size_t(n) * 8);
                break;
            }
            case PatchOp::SetRoots: {
                uint32_t n;
                if (!body.u32(n)) return false;
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

void RenderScene::draw(SkCanvas* canvas, const SkRect& visibleWorldRect) const {
    for (uint64_t id : roots_) {
        auto it = nodes_.find(id);
        if (it != nodes_.end()) drawNode(canvas, it->second, visibleWorldRect);
    }
}

void RenderScene::drawNode(SkCanvas* c, const RenderNode& n, const SkRect& parentCull) const {
    if (!(n.flags & kFlagVisible)) return;

    // Skia MakeAll(scaleX, skewX, transX, skewY, scaleY, transY, p0, p1, p2):
    // our (a,b,c,d,tx,ty) -> scaleX=a, skewY=b, skewX=c, scaleY=d.
    const SkMatrix m = SkMatrix::MakeAll(n.m[0], n.m[2], n.m[4], n.m[1], n.m[3], n.m[5], 0, 0, 1);
    SkMatrix inv;
    if (!m.invert(&inv)) return;
    SkRect cull;
    inv.mapRect(&cull, parentCull);                  // visible region expressed in this node's local space

    const bool clips = (n.flags & kFlagClips) != 0;
    const bool hasGeom = n.geom != Geom::None;
    if (hasGeom && (clips || n.children.empty())) {
        SkRect bounds = SkRect::MakeWH(n.w, n.h);
        bounds.outset(n.pad, n.pad);
        if (!SkRect::Intersects(bounds, cull)) return;   // fully off-screen: skip node and its subtree
    }

    SkAutoCanvasRestore acr(c, true);
    c->concat(m);

    float layerSigma = 0.f;
    bool layerBlur = false;
    for (const EffectSpec& e : n.effects) {
        if (e.type == 2) { layerBlur = true; layerSigma = sigmaOf(e.blur); }
    }
    const SkBlendMode bm = toBlend(n.blend);
    if (n.opacity < 0.999f || bm != SkBlendMode::kSrcOver || layerBlur) {
        SkPaint lp;
        lp.setAlphaf(n.opacity);
        lp.setBlendMode(bm);
        if (layerBlur && layerSigma > 0.f) lp.setImageFilter(SkImageFilters::Blur(layerSigma, layerSigma, nullptr));
        c->saveLayer(nullptr, &lp);
    }

    if (hasGeom) {
        for (const EffectSpec& e : n.effects) {
            if (e.type != 3 || e.blur <= 0) continue;     // background blur
            c->save();
            clipGeom(c, n, SkClipOp::kIntersect);
            sk_sp<SkImageFilter> f = SkImageFilters::Blur(sigmaOf(e.blur), sigmaOf(e.blur), nullptr);
            SkCanvas::SaveLayerRec rec(nullptr, nullptr, f.get(), 0);
            c->saveLayer(rec);
            c->restore();
            c->restore();
        }
        for (const EffectSpec& e : n.effects) if (e.type == 0) drawDropShadow(c, n, e);
        if (!n.isLine) for (const SkPaint& p : n.fills) drawGeom(c, n, p);
        for (const EffectSpec& e : n.effects) if (e.type == 1) drawInnerShadow(c, n, e);
        drawStroke(c, n);
    }

    if (clips && hasGeom) {
        c->save();
        clipGeom(c, n, SkClipOp::kIntersect);
    }
    for (uint64_t cid : n.children) {
        auto it = nodes_.find(cid);
        if (it != nodes_.end()) drawNode(c, it->second, cull);
    }
    if (clips && hasGeom) c->restore();
}

}  // namespace figtab
