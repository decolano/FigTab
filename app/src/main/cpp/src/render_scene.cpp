#include "render_scene.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <map>
#include <string>
#include "include/core/SkBlendMode.h"
#include "include/core/SkBlurTypes.h"
#include "include/core/SkColor.h"
#include "include/core/SkFont.h"
#include "include/core/SkFontMetrics.h"
#include "include/core/SkFontMgr.h"
#include "include/core/SkFontStyle.h"
#include "include/core/SkImageFilter.h"
#include "include/core/SkMaskFilter.h"
#include "include/core/SkMatrix.h"
#include "include/core/SkPathEffect.h"
#include "include/core/SkShader.h"
#include "include/core/SkTypeface.h"
#include "include/effects/SkDashPathEffect.h"
#include "include/effects/SkGradientShader.h"
#include "include/effects/SkImageFilters.h"
#if FIGTAB_HAS_FONTMGR
#include "include/ports/SkFontMgr_android.h"
#endif

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


// ───────────────────────── Text ─────────────────────────

sk_sp<SkFontMgr> fontMgr() {
    static sk_sp<SkFontMgr> mgr;
    static bool initialised = false;
    if (!initialised) {
        initialised = true;
#if FIGTAB_HAS_FONTMGR
        mgr = SkFontMgr_New_Android(nullptr);       // reads /system/etc/fonts.xml
#endif
        if (!mgr) mgr = SkFontMgr::RefEmpty();      // text silently disabled rather than crashing
    }
    return mgr;
}

sk_sp<SkTypeface> findTypeface(const std::string& family, uint32_t weight, bool italic) {
    static std::map<std::string, sk_sp<SkTypeface>> cache;
    const std::string key = family + "|" + std::to_string(weight) + "|" + (italic ? "i" : "n");
    auto it = cache.find(key);
    if (it != cache.end()) return it->second;
    sk_sp<SkFontMgr> mgr = fontMgr();
    const SkFontStyle style(int(weight), SkFontStyle::kNormal_Width,
                            italic ? SkFontStyle::kItalic_Slant : SkFontStyle::kUpright_Slant);
    sk_sp<SkTypeface> tf = mgr->matchFamilyStyle(family.c_str(), style);
    if (!tf) tf = mgr->matchFamilyStyle(nullptr, style);   // system default family
    cache[key] = tf;
    return tf;
}

struct TRun {
    SkFont font;
    float lineHeight = 0, letterSpacing = 0;
    bool underline = false, strike = false, ok = false;
    float asc = 0, desc = 0, leading = 0;
    float ulThick = 1, ulPos = 2, stThick = 1, stPos = -4;
    std::vector<SkPaint> fills;
    std::string text;
};

struct Tok {
    int run = 0;
    bool space = false, nl = false;
    float w = 0;
    sk_sp<SkTextBlob> blob;
};

bool readStr(Reader& r, uint32_t len, std::string& out) {
    if (r.left() < len) return false;
    out.assign(reinterpret_cast<const char*>(r.p), len);
    r.p += len;
    return true;
}

// Positioned-glyph blob at baseline y=0. Returns null for empty text; width includes letter spacing.
sk_sp<SkTextBlob> makeBlob(const SkFont& font, const std::string& s, float letterSpacing, float& width) {
    width = 0;
    const int count = font.countText(s.data(), s.size(), SkTextEncoding::kUTF8);
    if (count <= 0) return nullptr;
    std::vector<SkGlyphID> glyphs(static_cast<size_t>(count));
    font.textToGlyphs(s.data(), s.size(), SkTextEncoding::kUTF8, glyphs.data(), count);
    std::vector<SkScalar> widths(static_cast<size_t>(count));
    font.getWidths(glyphs.data(), count, widths.data());
    SkTextBlobBuilder builder;
    const SkTextBlobBuilder::RunBuffer& buf = builder.allocRunPosH(font, count, 0.f);
    float x = 0;
    for (int i = 0; i < count; ++i) {
        buf.glyphs[i] = glyphs[size_t(i)];
        buf.pos[i] = x;
        x += widths[size_t(i)] + letterSpacing;
    }
    width = x;
    return builder.make();
}

struct Item { int tok; float x; };
struct Line { std::vector<Item> items; float w = 0, h = 0, base = 0, after = 0; };

void layoutText(RenderNode& n, std::vector<TRun>& runs, uint32_t autoSize, uint32_t alignH, uint32_t alignV,
                float paragraphSpacing, uint32_t maxLines) {
    std::vector<Tok> toks;
    for (int ri = 0; ri < int(runs.size()); ++ri) {
        TRun& R = runs[size_t(ri)];
        if (!R.ok) continue;
        const std::string& s = R.text;
        size_t i = 0;
        while (i < s.size()) {
            if (s[i] == '\n') { Tok t; t.run = ri; t.nl = true; toks.push_back(t); ++i; continue; }
            const bool sp = (s[i] == ' ' || s[i] == '\t');
            size_t j = i;
            while (j < s.size() && s[j] != '\n' && ((s[j] == ' ' || s[j] == '\t') == sp)) ++j;
            std::string piece = s.substr(i, j - i);
            if (sp) for (char& ch : piece) if (ch == '\t') ch = ' ';
            Tok t; t.run = ri; t.space = sp;
            t.blob = makeBlob(R.font, piece, R.letterSpacing, t.w);
            toks.push_back(std::move(t));
            i = j;
        }
    }

    const bool wrap = (autoSize != 0) && n.w > 0;
    const float maxW = n.w;
    std::vector<Line> lines;
    Line cur;
    bool softStart = false;

    auto lineMetrics = [&](int ri, float& lh, float& baseOff) {
        const TRun& R = runs[size_t(ri)];
        lh = R.lineHeight > 0 ? R.lineHeight : (R.asc + R.desc + R.leading);
        baseOff = (lh - (R.asc + R.desc)) * 0.5f + R.asc;
    };
    auto finish = [&](bool hard, int fallbackRun) {
        while (!cur.items.empty() && toks[size_t(cur.items.back().tok)].space) {
            cur.w = cur.items.back().x;
            cur.items.pop_back();
        }
        float lh = 0, base = 0;
        if (cur.items.empty()) {
            if (fallbackRun >= 0) lineMetrics(fallbackRun, lh, base);
        } else {
            for (const Item& it : cur.items) {
                float l, b;
                lineMetrics(toks[size_t(it.tok)].run, l, b);
                lh = std::max(lh, l);
                base = std::max(base, b);
            }
        }
        cur.h = lh; cur.base = base; cur.after = hard ? paragraphSpacing : 0.f;
        lines.push_back(cur);
        cur = Line();
    };

    int lastRun = -1;
    for (size_t ti = 0; ti < toks.size(); ++ti) {
        const Tok& t = toks[ti];
        lastRun = t.run;
        if (t.nl) { finish(true, t.run); softStart = false; continue; }
        if (t.space) {
            if (cur.items.empty() && softStart) continue;        // no leading spaces after a soft wrap
            cur.items.push_back({int(ti), cur.w});
            cur.w += t.w;
            continue;
        }
        bool hasWord = false;
        for (const Item& it : cur.items) if (!toks[size_t(it.tok)].space) { hasWord = true; break; }
        if (wrap && hasWord && cur.w + t.w > maxW + 0.01f) { finish(false, t.run); softStart = true; }
        cur.items.push_back({int(ti), cur.w});
        cur.w += t.w;
        softStart = false;
    }
    if (!cur.items.empty()) finish(false, lastRun);
    if (maxLines > 0 && lines.size() > maxLines) lines.resize(maxLines);

    float totalH = 0, maxLineW = 0;
    for (size_t i = 0; i < lines.size(); ++i) {
        totalH += lines[i].h;
        if (i + 1 < lines.size()) totalH += lines[i].after;
        maxLineW = std::max(maxLineW, lines[i].w);
    }
    const float boxW = (autoSize == 0) ? maxLineW : n.w;
    float y = 0;
    if (autoSize == 2) {
        if (alignV == 1) y = (n.h - totalH) * 0.5f;
        else if (alignV == 2) y = n.h - totalH;
    }

    for (const Line& line : lines) {
        float off = 0;
        if (alignH == 1) off = (boxW - line.w) * 0.5f;
        else if (alignH == 2) off = boxW - line.w;
        const float baseline = y + line.base;
        for (const Item& it : line.items) {
            const Tok& t = toks[size_t(it.tok)];
            const TRun& R = runs[size_t(t.run)];
            const float x = off + it.x;
            if (!t.space && t.blob) {
                TextPiece p; p.blob = t.blob; p.x = x; p.y = baseline; p.run = t.run;
                n.textPieces.push_back(std::move(p));
            }
            if (R.underline) n.textDecos.push_back({x, baseline + R.ulPos, t.w, R.ulThick, t.run});
            if (R.strike)    n.textDecos.push_back({x, baseline + R.stPos, t.w, R.stThick, t.run});
        }
        y += line.h + line.after;
    }
}

bool decodeText(Reader& r, RenderNode& n) {
    uint32_t autoSize, alignH, alignV, maxLines, runCount;
    float paragraphSpacing;
    if (!r.u32(autoSize) || !r.u32(alignH) || !r.u32(alignV) || !r.f32(paragraphSpacing) ||
        !r.u32(maxLines) || !r.u32(runCount) || runCount > 256) return false;

    std::vector<TRun> runs(runCount);
    for (uint32_t i = 0; i < runCount; ++i) {
        TRun& R = runs[i];
        float size;
        uint32_t weight, italic, deco, famLen, textLen, fillCount;
        std::string family;
        if (!r.f32(size) || !r.f32(R.lineHeight) || !r.f32(R.letterSpacing) || !r.u32(weight) ||
            !r.u32(italic) || !r.u32(deco) || !r.u32(famLen) || famLen > 256 || !readStr(r, famLen, family) ||
            !r.u32(textLen) || textLen > (1u << 22) || !readStr(r, textLen, R.text) ||
            !r.u32(fillCount) || fillCount > 16) return false;
        for (uint32_t k = 0; k < fillCount; ++k) {
            SkPaint p;
            if (!readPaint(r, p)) return false;
            R.fills.push_back(p);
        }
        R.underline = (deco & 1) != 0;
        R.strike = (deco & 2) != 0;

        sk_sp<SkTypeface> tf = findTypeface(family, weight, italic != 0);
        if (!tf || size <= 0) continue;                     // run stays !ok and is skipped
        R.font = SkFont(tf, size);
        R.font.setSubpixel(true);
        R.font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
        R.font.setHinting(SkFontHinting::kNone);
        SkFontMetrics m;
        R.font.getMetrics(&m);
        R.asc = -m.fAscent; R.desc = m.fDescent; R.leading = m.fLeading;
        SkScalar v;
        R.ulThick = m.hasUnderlineThickness(&v) ? v : size / 14.f;
        R.ulPos   = m.hasUnderlinePosition(&v)  ? v : size / 9.f;
        R.stThick = m.hasStrikeoutThickness(&v) ? v : size / 14.f;
        R.stPos   = m.hasStrikeoutPosition(&v)  ? v : -size * 0.3f;
        R.ok = true;
    }

    n.textFills.clear();
    for (const TRun& R : runs) n.textFills.push_back(R.fills);
    layoutText(n, runs, autoSize, alignH, alignV, paragraphSpacing, maxLines);
    n.hasText = true;
    return true;
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

    uint32_t hasText;
    if (!r.u32(hasText)) return false;
    n.hasText = false;
    n.textPieces.clear(); n.textDecos.clear(); n.textFills.clear();
    if (hasText && !decodeText(r, n)) return false;

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

    if (n.hasText) {
        for (const TextPiece& p : n.textPieces) {
            for (const SkPaint& paint : n.textFills[size_t(p.run)]) c->drawTextBlob(p.blob.get(), p.x, p.y, paint);
        }
        for (const TextDeco& d : n.textDecos) {
            for (const SkPaint& paint : n.textFills[size_t(d.run)]) c->drawRect(SkRect::MakeXYWH(d.x, d.y, d.w, d.thick), paint);
        }
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
