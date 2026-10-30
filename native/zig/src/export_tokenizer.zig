pub const MAGIC: u32 = 0x5A52544B;
// keep 1 until release
pub const ABI_VERSION: u32 = 1;
pub const ParseResult = @import("shared.zig").ParseResult;

/// Inclusive start and exclusive end of a token, relative to the supplied input.
pub const TokenPositions = extern struct {
    start: u32, // Inclusive, UTF-16
    end: u32, // Exclusive, UTF-16
};

pub const TokenizerHeader = extern struct {
    magic: u32,
    abiVersion: u32,
    sourceLen: u32, // UTF-16 code units in supplied input
    tokenCount: u32, // Includes EOF
    tokenTagsOffset: u32, // Byte offset within snapshot
    tokenPositionsOffset: u32, // Byte offset within snapshot
    totalSize: u32, // Snapshot size in bytes
};

pub const TokenizerLayout = struct {
    tokenTags: u32,
    tokenPositions: u32,
    totalSize: u32,
};
