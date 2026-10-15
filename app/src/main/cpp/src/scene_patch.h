// Wire format for domain → render scene updates. Little-endian. Mirrored by ScenePatchEncoder.kt.
// Bump kPatchVersion on any layout change.
#pragma once
#include <cstddef>
#include <cstdint>

namespace figtab {

constexpr uint32_t kPatchMagic   = 0x46545031;  // 'FTP1'
constexpr uint32_t kPatchVersion = 1;

struct PatchHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t opCount;
    uint32_t reserved;
};

enum class PatchOp : uint32_t {
    UpsertNode  = 1,  // NodeRecord (+ variable payload in later phases)
    RemoveNode  = 2,  // u64 nodeId
    SetChildren = 3,  // u64 parentId, u32 n, u64 ids[n]   (back → front)
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
