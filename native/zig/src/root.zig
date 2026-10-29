const std = @import("std");
const Ast = @import("ziglang/Ast.zig");
const tokenizer = @import("ziglang/tokenizer.zig");
const handles = @import("handles.zig");
const export_ast = @import("export_ast.zig");
const shared = @import("shared.zig");
const utf_conversion = @import("utf_conversion.zig");
const SnapshotHeader = export_ast.SnapshotHeader;
const NodePayload = export_ast.NodePayload;
const ErrorView = export_ast.ErrorView;
const ScopedState = shared.ScopedState;
const Mode = shared.Mode;
const ParseResult = export_ast.ParseResult;

comptime {
    // sanity check because this assumptions need to hold on Kotlin side as well
    std.debug.assert(@sizeOf(SnapshotHeader) == 64);
    std.debug.assert(@sizeOf(NodePayload) == 8);
    std.debug.assert(@sizeOf(ErrorView) == 24);
    std.debug.assert(@sizeOf(ParseResult) == 24);
    std.debug.assert(@sizeOf(tokenizer.Token.Tag) == 1);
    std.debug.assert(@sizeOf(Ast.Node.Tag) == 1);
}

/// Entrypoint to create single parse scope. Returned state is not thread-safe.
pub export fn zrStartSession() ?*ScopedState {
    return handles.zrStartSession();
}

/// Releases the state.
pub export fn zrCloseSession(state: *ScopedState) void {
    handles.zrCloseSession(state);
}

/// Frees results of previous parsings in shared Arena. Does not dealloc memory.
pub export fn zrFree(state: *ScopedState) void {
    handles.zrFree(state);
}

pub export fn zrAllocInput(state: *ScopedState, sourceLen: usize) ?[*:0]u8 {
    return handles.zrAllocInput(state, sourceLen);
}

/// Parses UTF-8 bytes into a snapshot with UTF-16 token starts and source length.
pub export fn zrParse(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
    mode: Mode,
) *const ParseResult {
    return handles.zrParse(state, sourcePtr, sourceLen, mode);
}

test {
    _ = handles;
    _ = utf_conversion;
}
