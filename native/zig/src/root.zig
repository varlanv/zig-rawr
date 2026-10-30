const std = @import("std");
const Ast = @import("ziglang/Ast.zig");
const tokenizer = @import("ziglang/tokenizer.zig");
const handles = @import("handles.zig");
const export_ast = @import("export_ast.zig");
const export_tokenizer = @import("export_tokenizer.zig");
const shared = @import("shared.zig");
const utf_conversion = @import("utf_conversion.zig");
const SnapshotHeader = export_ast.SnapshotHeader;
const TokenizerHeader = export_tokenizer.TokenizerHeader;
const NodePayload = export_ast.NodePayload;
const TokenPositions = export_tokenizer.TokenPositions;
const ErrorView = export_ast.ErrorView;
const ScopedState = shared.ScopedState;
const Mode = shared.Mode;
const ParseResult = shared.ParseResult;

comptime {

    // sanity check because this assumptions need to hold on Kotlin side as well
    std.debug.assert(@sizeOf(SnapshotHeader) == 64);
    std.debug.assert(@sizeOf(TokenizerHeader) == 28);
    std.debug.assert(@sizeOf(NodePayload) == 8);
    std.debug.assert(@sizeOf(TokenPositions) == 8);
    std.debug.assert(@offsetOf(TokenPositions, "start") == 0);
    std.debug.assert(@offsetOf(TokenPositions, "end") == @sizeOf(u32));
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

/// Parses UTF-8 bytes into a snapshot with UTF-16 token ranges and source length.
pub export fn zrParseAst(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
    mode: Mode,
) *const ParseResult {
    return handles.zrParseAst(state, sourcePtr, sourceLen, mode);
}

/// Tokenizes zero-terminated UTF-8 input without parsing an AST.
/// Returns input-relative UTF-16 ranges, including EOF. Slices must start
/// at a lexical boundary and are treated as standalone input.
pub export fn zrParseTokens(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
) *const ParseResult {
    return handles.zrParseTokens(state, sourcePtr, sourceLen);
}

test {
    _ = handles;
    _ = utf_conversion;
}
