// keep 1 until release
pub const ABI_VERSION: u32 = 1;

pub const ErrorView = extern struct {
    tag: u32,
    token: u32,
    flags: u32,
    extraKind: u32,
    extraValue: u64,
};

pub const NodePayload = extern struct {
    first: u32,
    second: u32,
};

pub const SnapshotHeader = extern struct {
    magic: u32,
    abiVersion: u32,
    mode: u32,
    /// Source length in UTF-16 code units.
    sourceLen: u32,
    tokenCount: u32,
    nodeCount: u32,
    extraDataCount: u32,
    errorCount: u32,
    tokenTagsOffset: u32,
    /// Byte offset of the u32 array of UTF-16 token starts.
    tokenStartsOffset: u32,
    nodeTagsOffset: u32,
    nodeMainTokensOffset: u32,
    nodePayloadsOffset: u32,
    extraDataOffset: u32,
    errorsOffset: u32,
    totalSize: u32,
};

pub const Layout = struct {
    tokenTags: u32,
    tokenStarts: u32,
    nodeTags: u32,
    nodeMainTokens: u32,
    nodePayloads: u32,
    extraData: u32,
    errors: u32,
    totalSize: u32,
};

/// Snapshot storage is owned by State and remains valid until zrFree or zrCloseSession.
pub const ParseResult = extern struct {
    data: ?[*]const u8 = null,
    size: usize = 0,
    /// 0: success; 1: invalid input or size; 3: out of memory.
    status: u32 = 0,
};
