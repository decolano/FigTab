// Wire format for domain -> render scene updates. Little-endian. Mirrored by ScenePatchEncoder.kt.
// Bump kPatchVersion on any layout change.
//
// v2 UpsertNode payload = NodeRecord (56 bytes) followed by:
//   u32 shapeKind      0 none, 1 rect/rounded rect, 2 ellipse/arc, 3 star, 4 polygon, 5 line
//   f32 radii[4]       corner radii TL, TR, BR, BL
//   u32 pointCount     star / polygon
//   f32 innerRatio     star
//   f32 arc[3]         startDeg, sweepDeg, innerRatio (ellipse)
//   u32 fillCount, Paint[fillCount]
//   u32 hasStroke; if set:
//     f32 weight; u32 align (0 inside,1 center,2 outside); u32 cap (0 butt,1 round,2 square);
//     u32 join (0 miter,1 bevel,2 round); f32 miter; u32 dashCount, f32 dash[]; u32 paintCount, Paint[]
//   u32 effectCount, Effect[]: u32 type (0 drop,1 inner,2 layer blur,3 bg blur), u32 argb,
//                              f32 dx, f32 dy, f32 blurRadius, f32 spread
// Paint: u32 type (0 solid,1 linear,2 radial), f32 opacity, u32 argb, u32 stopCount,
//        {f32 pos, u32 argb}[stopCount], and for gradients 6 floats (a b c d tx ty) mapping the
//        unit gradient space to node-local space. Linear: (0,0.5)->(1,0.5). Radial: center (0.5,0.5), radius 0.5.
#pragma once
#include <cstddef>
#include <cstdint>

namespace figtab {

constexpr uint32_t kPatchMagic   = 0x46545031;  // 'FTP1'
constexpr uint32_t kPatchVersion = 2;

struct PatchHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t opCount;
    uint32_t reserved;
};

enum class PatchOp : uint32_t {
    UpsertNode  = 1,
    RemoveNode  = 2,  // u64 nodeId
    SetChildren = 3,  // u64 parentId, u32 n, u64 ids[n]   (back -> front)
    SetRoots    = 4,  // u32 n, u64 ids[n]
    ClearAll    = 5,
};

struct OpHeader {
    PatchOp  op;
    uint32_t payloadBytes;  // excludes this header; lets the decoder skip unknown ops
};

enum class NodeKind : uint32_t {
    Section = 1, Frame = 2, Group = 3, Shape = 4, VectorNetwork = 5, Text = 6, Component = 7, Instance = 8,
};

constexpr uint32_t kFlagVisible = 1, kFlagClips = 2, kFlagEffects = 4;

struct NodeRecord {
    uint64_t id;
    uint32_t kind;          // NodeKind
    uint32_t flags;         // kFlag*
    float    m[6];          // relative Affine2D: a b c d tx ty
    float    width, height;
    float    opacity;
    uint32_t blendMode;
};

static_assert(sizeof(PatchHeader) == 16, "PatchHeader layout");
static_assert(sizeof(OpHeader) == 8, "OpHeader layout");
static_assert(sizeof(NodeRecord) == 56, "NodeRecord layout must match ScenePatchEncoder.kt");

}  // namespace figtab
