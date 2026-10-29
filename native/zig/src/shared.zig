const std = @import("std");
const export_ast = @import("export_ast.zig");
const ParseResult = export_ast.ParseResult;
const ArenaAllocator = std.heap.ArenaAllocator;

/// *handshake* sanity check value so that consumer can check correctness against native binary.
pub const MAGIC = 0x5A524153;

pub const Mode = enum(u32) {
    zig = 0,
    zon = 1,
    _,
};

// State of single scope, returned by `zrStartSession`.
// Not threadsafe, consumers should guarantee synchronization.
// Valid until `zrCloseSession` call.
pub const ScopedState = struct {
    arena: ArenaAllocator,
    result: ParseResult = .{},
    tag: Tag = .init,

    /// Small state-tracking to prevent invalid state transitions.
    /// Expected normal state flow should be like this:
    /// init -> allocate + (optionally) parse -> free -> allocate + (optionally) parse -> free ... close
    pub const Tag = enum {
        init,
        allocate,
        parse,
        free,
        closed,
    };
};
