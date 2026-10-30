const std = @import("std");
const Ast = @import("ziglang/Ast.zig");
const tokenizer = @import("ziglang/tokenizer.zig");
const shared = @import("shared.zig");
const export_ast = @import("export_ast.zig");
const export_tokenizer = @import("export_tokenizer.zig");
const utf_conversion = @import("utf_conversion.zig");
const Allocator = std.mem.Allocator;
const SnapshotHeader = export_ast.SnapshotHeader;
const TokenizerHeader = export_tokenizer.TokenizerHeader;
const TokenizerLayout = export_tokenizer.TokenizerLayout;
const ParseResult = shared.ParseResult;
const ScopedState = shared.ScopedState;
const NodePayload = export_ast.NodePayload;
const TokenPositions = export_tokenizer.TokenPositions;
const ErrorView = export_ast.ErrorView;
const Mode = shared.Mode;
const AstLayout = export_ast.AstLayout;
const MAGIC = shared.MAGIC;
const SnapshotError = error{ TooLarge, OutOfMemory, InvalidUtf8, InvalidOffset };
const TokenList = std.MultiArrayList(struct {
    tag: tokenizer.Token.Tag,
    positions: TokenPositions,
});

/// Entrypoint to create single parse scope. Returned state is not thread-safe
pub fn zrStartSession() ?*ScopedState {
    const state = std.heap.page_allocator.create(ScopedState) catch return null;
    state.* = .{
        .arena = .init(std.heap.smp_allocator),
    };
    return state;
}

pub fn zrCloseSession(state: *ScopedState) void {
    // allowing transition from any state, including .closed, to guarantee resources cleanup
    if (state.tag != .closed) {
        state.tag = .closed;
        state.arena.deinit();
        std.heap.page_allocator.destroy(state);
    }
}

/// Resets per-call allocations, retaining capacity for reuse.
/// Invalidates input and snapshots from zrParseAst and zrParseTokens.
pub fn zrFree(state: *ScopedState) void {
    if (state.tag != .parse and state.tag != .init and state.tag != .allocate and state.tag != .free) {
        panicOnInvaliStateTransition(state.tag, .free);
    }
    state.tag = .free;
    state.result = .{};
    _ = state.arena.reset(.retain_capacity);
}

/// Reserves input plus its zero sentinel. Older input allocations remain valid until zrFree.
pub fn zrAllocInput(state: *ScopedState, sourceLen: usize) ?[*:0]u8 {
    if (state.tag != .free and state.tag != .init and state.tag != .allocate) {
        panicOnInvaliStateTransition(state.tag, .allocate);
    }
    state.tag = .allocate;
    if (sourceLen > std.math.maxInt(u32)) return null;
    const size = std.math.cast(u32, sourceLen) orelse return null;
    const input = state.arena.allocator().allocSentinel(u8, size, 0) catch return null;
    return input.ptr;
}

pub fn zrParseAst(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
    mode: Mode,
) *const ParseResult {
    const source = beginParse(state, sourcePtr, sourceLen) orelse return &state.result;
    const parseMode: Ast.Mode = switch (mode) {
        .zig => .zig,
        .zon => .zon,
        else => return &state.result,
    };
    return finishParse(state, parseInto(state.arena.allocator(), source, parseMode));
}

pub fn zrParseTokens(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
) *const ParseResult {
    const source = beginParse(state, sourcePtr, sourceLen) orelse return &state.result;
    return finishParse(state, tokenizeInto(state.arena.allocator(), source));
}

fn beginParse(state: *ScopedState, sourcePtr: ?[*]const u8, sourceLen: usize) ?[:0]const u8 {
    if (state.tag != .allocate) {
        panicOnInvaliStateTransition(state.tag, .parse);
    }
    state.tag = .parse;
    state.result = .{ .status = 1 };
    if (sourceLen > std.math.maxInt(u32)) return null;
    if (sourcePtr == null and sourceLen != 0) return null;
    const ptr = sourcePtr orelse "".ptr;
    if (ptr[sourceLen] != 0) return null;
    return ptr[0..sourceLen :0];
}

fn finishParse(state: *ScopedState, output: SnapshotError![]align(8) u8) *const ParseResult {
    const bytes = output catch |err| {
        state.result.status = switch (err) {
            error.TooLarge, error.InvalidUtf8, error.InvalidOffset => 1,
            error.OutOfMemory => 3,
        };
        return &state.result;
    };
    state.result = .{ .data = bytes.ptr, .size = bytes.len };
    return &state.result;
}

fn tokenizeInto(gpa: Allocator, source: [:0]const u8) SnapshotError![]align(8) u8 {
    var tokens = TokenList{};
    defer tokens.deinit(gpa);
    // Same source-size estimate as Ast.parse, without building parser data.
    try tokens.ensureTotalCapacity(gpa, source.len / 8);

    var scanner = tokenizer.Tokenizer.init(source);
    while (true) {
        const token = scanner.next();
        try tokens.append(gpa, .{
            .tag = token.tag,
            .positions = .{
                .start = @intCast(token.loc.start),
                .end = @intCast(token.loc.end),
            },
        });
        if (token.tag == .eof) break;
    }

    const layout = try tokenLayoutFor(@sizeOf(TokenizerHeader), tokens.len);
    const output = try gpa.alignedAlloc(u8, .@"8", layout.totalSize);
    errdefer gpa.free(output);
    const sourceLen = try writeTokenData(source, output, layout, tokens.items(.tag), tokens.items(.positions));
    const header: TokenizerHeader = .{
        .magic = export_tokenizer.MAGIC,
        .abiVersion = export_tokenizer.ABI_VERSION,
        .sourceLen = sourceLen,
        .tokenCount = @intCast(tokens.len),
        .tokenTagsOffset = layout.tokenTags,
        .tokenPositionsOffset = layout.tokenPositions,
        .totalSize = layout.totalSize,
    };
    @memcpy(output[0..@sizeOf(TokenizerHeader)], std.mem.asBytes(&header));
    return output;
}

fn panicOnInvaliStateTransition(
    currentState: ScopedState.Tag,
    newState: ScopedState.Tag,
) noreturn {
    // TODO - we should not panic on failure because it will crash host application; need to integrate return codes instead.
    std.debug.panic(
        "Illegal state transition attempt: {s} -> {s}",
        .{ @tagName(currentState), @tagName(newState) },
    );
}

fn reserve(cursor: *usize, alignment: usize, count: usize, itemSize: usize) error{TooLarge}!u32 {
    const start = std.mem.alignForward(usize, cursor.*, alignment);
    const size = std.math.mul(usize, count, itemSize) catch return error.TooLarge;
    const end = std.math.add(usize, start, size) catch return error.TooLarge;
    if (end > std.math.maxInt(u32)) return error.TooLarge;
    cursor.* = end;
    return @intCast(start);
}

fn tokenLayoutFor(headerSize: usize, tokenCount: usize) error{TooLarge}!TokenizerLayout {
    var cursor = headerSize;
    const tokenTags = try reserve(&cursor, 1, tokenCount, 1);
    const tokenPositions = try reserve(&cursor, @alignOf(TokenPositions), tokenCount, @sizeOf(TokenPositions));
    return .{
        .tokenTags = tokenTags,
        .tokenPositions = tokenPositions,
        .totalSize = @intCast(cursor),
    };
}

// Both snapshot layouts expose the same token sections. Convert directly into
// the final buffer, reusing the converter's SIMD ASCII fast path.
fn writeTokenData(
    source: []const u8,
    output: []align(8) u8,
    layout: anytype,
    tags: []const tokenizer.Token.Tag,
    bytePositions: []const TokenPositions,
) error{ InvalidUtf8, InvalidOffset }!u32 {
    std.debug.assert(tags.len == bytePositions.len and tags.len > 0);
    const tagBytes = std.mem.sliceAsBytes(tags);
    @memcpy(output[layout.tokenTags..][0..tagBytes.len], tagBytes);
    @memset(output[layout.tokenTags + tagBytes.len .. layout.tokenPositions], 0);

    const byteOffsets = std.mem.bytesAsSlice(u32, std.mem.sliceAsBytes(bytePositions));
    const positions: [*]u32 = @ptrCast(@alignCast(output.ptr + layout.tokenPositions));
    const utf16Positions = positions[0..byteOffsets.len];
    try utf_conversion.write_utf16_positions(source, byteOffsets, utf16Positions);
    return utf16Positions[utf16Positions.len - 1];
}

fn layoutFor(ast: *const Ast) error{TooLarge}!AstLayout {
    if (ast.tokens.len > std.math.maxInt(u32) or
        ast.nodes.len > std.math.maxInt(u32) or
        ast.extra_data.len > std.math.maxInt(u32) or
        ast.errors.len > std.math.maxInt(u32)) return error.TooLarge;

    const tokens = try tokenLayoutFor(@sizeOf(SnapshotHeader), ast.tokens.len);
    var cursor: usize = tokens.totalSize;
    const nodeTags = try reserve(&cursor, 1, ast.nodes.len, 1);
    const nodeMainTokens = try reserve(&cursor, 4, ast.nodes.len, 4);
    const nodePayloads = try reserve(&cursor, 4, ast.nodes.len, @sizeOf(NodePayload));
    const extraData = try reserve(&cursor, 4, ast.extra_data.len, 4);
    const errors = try reserve(&cursor, 8, ast.errors.len, @sizeOf(ErrorView));
    return .{
        .tokenTags = tokens.tokenTags,
        .tokenPositions = tokens.tokenPositions,
        .nodeTags = nodeTags,
        .nodeMainTokens = nodeMainTokens,
        .nodePayloads = nodePayloads,
        .extraData = extraData,
        .errors = errors,
        .totalSize = @intCast(cursor),
    };
}

fn parseInto(
    gpa: Allocator,
    source: [:0]const u8,
    mode: Ast.Mode,
) SnapshotError![]align(8) u8 {
    var ast = try Ast.parse(gpa, source, .{ .mode = mode });
    defer ast.deinit(gpa);
    const layout = try layoutFor(&ast);
    const bytePositions = try gpa.alloc(TokenPositions, ast.tokens.len);
    defer gpa.free(bytePositions);
    for (ast.tokens.items(.start), 0..) |start, index| {
        bytePositions[index] = .{
            .start = start,
            .end = @intCast(@as(usize, start) + ast.tokenSlice(@intCast(index)).len),
        };
    }
    const output = try gpa.alignedAlloc(u8, .@"8", layout.totalSize);
    errdefer gpa.free(output);
    const sourceLen = try writeTokenData(source, output, layout, ast.tokens.items(.tag), bytePositions);
    const header: SnapshotHeader = .{
        .magic = MAGIC,
        .abiVersion = export_ast.ABI_VERSION,
        .mode = @backingInt(mode),
        .sourceLen = sourceLen,
        .tokenCount = @intCast(ast.tokens.len),
        .nodeCount = @intCast(ast.nodes.len),
        .extraDataCount = @intCast(ast.extra_data.len),
        .errorCount = @intCast(ast.errors.len),
        .tokenTagsOffset = layout.tokenTags,
        .tokenPositionsOffset = layout.tokenPositions,
        .nodeTagsOffset = layout.nodeTags,
        .nodeMainTokensOffset = layout.nodeMainTokens,
        .nodePayloadsOffset = layout.nodePayloads,
        .extraDataOffset = layout.extraData,
        .errorsOffset = layout.errors,
        .totalSize = layout.totalSize,
    };
    @memcpy(output[0..@sizeOf(SnapshotHeader)], std.mem.asBytes(&header));

    const nodeTags: []const u8 = @as([*]const u8, @ptrCast(ast.nodes.items(.tag).ptr))[0..ast.nodes.len];
    @memcpy(output[layout.nodeTags..][0..nodeTags.len], nodeTags);
    @memset(output[layout.nodeTags + nodeTags.len .. layout.nodeMainTokens], 0);
    const nodeMainTokens = std.mem.sliceAsBytes(ast.nodes.items(.main_token));
    @memcpy(output[layout.nodeMainTokens..][0..nodeMainTokens.len], nodeMainTokens);

    for (0..ast.nodes.len) |index| {
        const payload = nodePayload(&ast, index);
        const start = @as(usize, layout.nodePayloads) + index * @sizeOf(NodePayload);
        @memcpy(output[start..][0..@sizeOf(NodePayload)], std.mem.asBytes(&payload));
    }

    const extraData = std.mem.sliceAsBytes(ast.extra_data);
    @memcpy(output[layout.extraData..][0..extraData.len], extraData);
    @memset(output[layout.extraData + extraData.len .. layout.errors], 0);

    for (ast.errors, 0..) |err, index| {
        const view = errorView(err);
        const start = @as(usize, layout.errors) + index * @sizeOf(ErrorView);
        @memcpy(output[start..][0..@sizeOf(ErrorView)], std.mem.asBytes(&view));
    }
    return output;
}

fn errorView(err: Ast.Error) ErrorView {
    const extraKind: u32, const extraValue: u64 = switch (err.tag) {
        .expected_token => .{ 1, @backingInt(err.extra.expected_tag) },
        .invalid_byte => .{ 2, err.extra.offset },
        else => .{ 0, 0 },
    };
    return .{
        .tag = @backingInt(err.tag),
        .token = err.token,
        .flags = @as(u32, @intFromBool(err.is_note)) | (@as(u32, @intFromBool(err.token_is_prev)) << 1),
        .extraKind = extraKind,
        .extraValue = extraValue,
    };
}

fn word(value: anytype) u32 {
    return switch (@typeInfo(@TypeOf(value))) {
        .int => @intCast(value),
        .@"enum" => @backingInt(value),
        .@"struct" => @bitCast(value),
        else => @compileError("unsupported node data word"),
    };
}

fn one(value: anytype) NodePayload {
    return .{ .first = word(value), .second = 0 };
}

fn two(value: anytype) NodePayload {
    return .{ .first = word(value[0]), .second = word(value[1]) };
}

fn nodePayload(ast: *const Ast, index: usize) NodePayload {
    const tag = ast.nodes.items(.tag)[index];
    const data = ast.nodes.items(.data)[index];
    return switch (tag) {
        .root => if (ast.mode == .zig)
            .{ .first = word(data.extra_range.start), .second = word(data.extra_range.end) }
        else
            one(data.node),

        .anyframe_literal,
        .char_literal,
        .number_literal,
        .unreachable_literal,
        .identifier,
        .enum_literal,
        .string_literal,
        .error_value,
        => .{ .first = 0, .second = 0 },

        .@"errdefer",
        .@"defer",
        .bool_not,
        .negation,
        .bit_not,
        .negation_wrap,
        .address_of,
        .@"try",
        .optional_type,
        .deref,
        .@"suspend",
        .@"resume",
        .@"comptime",
        .@"nosuspend",
        => one(data.node),

        .@"return" => one(data.opt_node),

        .test_decl => two(data.opt_token_and_node),
        .global_var_decl,
        .local_var_decl,
        .fn_proto_multi,
        .fn_proto_one,
        .fn_proto,
        => two(data.extra_and_opt_node),
        .simple_var_decl,
        .fn_proto_simple,
        .array_init_dot_two,
        .array_init_dot_two_comma,
        .struct_init_dot_two,
        .struct_init_dot_two_comma,
        .builtin_call_two,
        .builtin_call_two_comma,
        .container_decl_two,
        .container_decl_two_trailing,
        .tagged_union_two,
        .tagged_union_two_trailing,
        .block_two,
        .block_two_semicolon,
        => two(data.opt_node_and_opt_node),

        .aligned_var_decl,
        .for_range,
        .call_one,
        .call_one_comma,
        .struct_init_one,
        .struct_init_one_comma,
        .container_field_init,
        => two(data.node_and_opt_node),

        .@"continue", .@"break" => two(data.opt_token_and_opt_node),
        .anyframe_type => two(data.token_and_node),
        .multiline_string_literal, .error_set_decl => two(data.token_and_token),
        .field_access,
        .unwrap_optional,
        .grouped_expression,
        .asm_simple,
        .asm_input,
        => two(data.node_and_token),
        .asm_output => two(data.opt_node_and_token),

        .ptr_type_aligned,
        .ptr_type_sentinel,
        .switch_case_one,
        .switch_case_inline_one,
        => two(data.opt_node_and_node),

        .assign_destructure,
        .ptr_type,
        .ptr_type_bit_range,
        .switch_case,
        .switch_case_inline,
        => two(data.extra_and_node),

        .array_type_sentinel,
        .slice,
        .slice_sentinel,
        .array_init,
        .array_init_comma,
        .struct_init,
        .struct_init_comma,
        .call,
        .call_comma,
        .@"switch",
        .switch_comma,
        .while_cont,
        .@"while",
        .@"if",
        .container_decl_arg,
        .container_decl_arg_trailing,
        .tagged_union_enum_tag,
        .tagged_union_enum_tag_trailing,
        .container_field,
        .@"asm",
        => two(data.node_and_extra),

        .array_init_dot,
        .array_init_dot_comma,
        .struct_init_dot,
        .struct_init_dot_comma,
        .builtin_call,
        .builtin_call_comma,
        .container_decl,
        .container_decl_trailing,
        .tagged_union,
        .tagged_union_trailing,
        .block,
        .block_semicolon,
        => .{ .first = word(data.extra_range.start), .second = word(data.extra_range.end) },

        .@"for" => two(data.@"for"),

        .@"catch",
        .equal_equal,
        .bang_equal,
        .less_than,
        .greater_than,
        .less_or_equal,
        .greater_or_equal,
        .assign_mul,
        .assign_div,
        .assign_mod,
        .assign_add,
        .assign_sub,
        .assign_shl,
        .assign_shl_sat,
        .assign_shr,
        .assign_bit_and,
        .assign_bit_xor,
        .assign_bit_or,
        .assign_mul_wrap,
        .assign_add_wrap,
        .assign_sub_wrap,
        .assign_mul_sat,
        .assign_add_sat,
        .assign_sub_sat,
        .assign,
        .merge_error_sets,
        .mul,
        .div,
        .mod,
        .mul_wrap,
        .mul_sat,
        .add,
        .sub,
        .array_cat,
        .add_wrap,
        .sub_wrap,
        .add_sat,
        .sub_sat,
        .shl,
        .shl_sat,
        .shr,
        .bit_and,
        .bit_xor,
        .bit_or,
        .@"orelse",
        .bool_and,
        .bool_or,
        .array_type,
        .slice_open,
        .array_access,
        .array_init_one,
        .array_init_one_comma,
        .switch_range,
        .error_union,
        .if_simple,
        .while_simple,
        .for_simple,
        .fn_decl,
        .container_field_align,
        => two(data.node_and_node),
    };
}

fn testSnapshot(state: *ScopedState, source: [:0]const u8, mode: Mode, minimumNodes: u32) !void {
    defer zrFree(state);
    const input = zrAllocInput(state, source.len) orelse return error.OutOfMemory;
    @memcpy(input[0..source.len], source);

    const result = zrParseAst(state, input, source.len, mode);

    try std.testing.expectEqual(@as(u32, 0), result.status);
    try std.testing.expect(result.size >= @sizeOf(SnapshotHeader));
    const output = result.data.?[0..result.size];
    const header: *const SnapshotHeader = @ptrCast(@alignCast(output.ptr));

    try std.testing.expectEqual(@as(u32, MAGIC), header.magic);
    try std.testing.expectEqual(export_ast.ABI_VERSION, header.abiVersion);
    try std.testing.expectEqual(@as(u32, @backingInt(mode)), header.mode);
    try std.testing.expectEqual(@as(u32, @intCast(output.len)), header.totalSize);
    try std.testing.expect(header.nodeCount >= minimumNodes);
    try std.testing.expect(header.tokenCount > 0);
    try std.testing.expectEqual(@as(u8, 0), output[header.nodeTagsOffset]);
}

test "tokenizer snapshot matches AST token ranges and supports session reuse" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();
    const sources = [_][:0]const u8{
        "",
        " \r\n\t// π€😀\n",
        "\xef\xbb\xbfconst x = \"π€😀\"; // trailing π😀",
        "/// docs\r\nconst text = \\\\π😀\n    \\\\more\n;",
        "const x = \"π😀",
        ".{ .answer = 42 }",
    };
    for (sources) |source| {
        defer zrFree(&state);
        const input = zrAllocInput(&state, source.len) orelse return error.OutOfMemory;
        @memcpy(input[0..source.len], source);
        const result = zrParseTokens(&state, input, source.len);
        try std.testing.expectEqual(@as(u32, 0), result.status);
        const output = result.data.?[0..result.size];
        const header: *const TokenizerHeader = @ptrCast(@alignCast(output.ptr));
        try std.testing.expectEqual(export_tokenizer.MAGIC, header.magic);
        try std.testing.expectEqual(export_tokenizer.ABI_VERSION, header.abiVersion);
        try std.testing.expectEqual(result.size, @as(usize, header.totalSize));

        const astOutput = try parseInto(std.testing.allocator, source, .zig);
        defer std.testing.allocator.free(astOutput);
        const astHeader: *const SnapshotHeader = @ptrCast(@alignCast(astOutput.ptr));
        try std.testing.expectEqual(astHeader.sourceLen, header.sourceLen);
        try std.testing.expectEqual(astHeader.tokenCount, header.tokenCount);
        try std.testing.expectEqualSlices(
            u8,
            astOutput[astHeader.tokenTagsOffset..][0..header.tokenCount],
            output[header.tokenTagsOffset..][0..header.tokenCount],
        );
        const positionsSize = @as(usize, header.tokenCount) * @sizeOf(TokenPositions);
        try std.testing.expectEqualSlices(
            u8,
            astOutput[astHeader.tokenPositionsOffset..][0..positionsSize],
            output[header.tokenPositionsOffset..][0..positionsSize],
        );
    }

    const invalid = "//\x80";
    const input = zrAllocInput(&state, invalid.len) orelse return error.OutOfMemory;
    defer zrFree(&state);
    @memcpy(input[0..invalid.len], invalid);
    const result = zrParseTokens(&state, input, invalid.len);
    try std.testing.expectEqual(@as(u32, 1), result.status);
    try std.testing.expect(result.data == null and result.size == 0);
}

test "parse into a state-owned snapshot and retain capacity after free" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();

    try testSnapshot(&state, "const answer = 42;", .zig, 3);
    try std.testing.expect(state.arena.queryCapacity() > 0);
    try testSnapshot(&state, ".{ .answer = 42 }", .zon, 2);
    try testSnapshot(&state, "const = ;", .zig, 1);
    try testSnapshot(&state, "", .zig, 1);
}

fn testTokenPositions(state: *ScopedState, source: [:0]const u8, mode: Mode, expected: []const u32) !void {
    defer zrFree(state);
    const input = zrAllocInput(state, source.len) orelse return error.OutOfMemory;
    @memcpy(input[0..source.len], source);
    const result = zrParseAst(state, input, source.len, mode);
    try std.testing.expectEqual(@as(u32, 0), result.status);

    const output = result.data.?[0..result.size];
    const header: *const SnapshotHeader = @ptrCast(@alignCast(output.ptr));
    try std.testing.expectEqual(export_ast.ABI_VERSION, header.abiVersion);
    const offsetsPerToken = @sizeOf(TokenPositions) / @sizeOf(u32);
    try std.testing.expectEqual(@as(u32, @intCast(expected.len / offsetsPerToken)), header.tokenCount);
    try std.testing.expectEqual(expected[expected.len - 1], header.sourceLen);
    const positions: [*]const u32 = @ptrCast(@alignCast(output.ptr + header.tokenPositionsOffset));
    try std.testing.expectEqualSlices(u32, expected, positions[0 .. header.tokenCount * offsetsPerToken]);
    const eofTag = output[header.tokenTagsOffset + header.tokenCount - 1];
    try std.testing.expectEqual(@as(u8, @backingInt(@import("ziglang/tokenizer.zig").Token.Tag.eof)), eofTag);
}

test "snapshot exports interleaved UTF-16 token ranges and source length" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();

    try testTokenPositions(&state, "const x = \"π€😀\";", .zig, &.{ 0, 5, 6, 7, 8, 9, 10, 16, 16, 17, 17, 17 });
    try testTokenPositions(&state, "const x = \"π€😀\"; \n// π€😀\n", .zig, &.{ 0, 5, 6, 7, 8, 9, 10, 16, 16, 17, 27, 27 });
    try testTokenPositions(&state, ".{ .text = \"π€😀\" }", .zon, &.{ 0, 1, 1, 2, 3, 4, 4, 8, 9, 10, 11, 17, 18, 19, 19, 19 });
}

test "snapshot source length includes whitespace and comments without tokens" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();

    try testTokenPositions(&state, "", .zig, &.{ 0, 0 });
    try testTokenPositions(&state, " \r\n\t", .zig, &.{ 4, 4 });
    try testTokenPositions(&state, "// π€😀\n", .zig, &.{ 8, 8 });
}

test "token ranges exclude gaps and preserve unterminated string boundaries" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();

    try testTokenPositions(&state, "  const x = 1; // π😀\n", .zig, &.{ 2, 7, 8, 9, 10, 11, 12, 13, 13, 14, 22, 22 });
    try testTokenPositions(&state, "const x = \"π😀", .zig, &.{ 0, 5, 6, 7, 8, 9, 10, 14, 14, 14 });
}

test "malformed UTF-8 returns no snapshot and the session remains reusable" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();
    const sources = [_][:0]const u8{ "const x = \"\xc0\x80\";", "//\x80" };

    for (sources) |source| {
        const input = zrAllocInput(&state, source.len) orelse return error.OutOfMemory;
        @memcpy(input[0..source.len], source);
        const result = zrParseAst(&state, input, source.len, .zig);
        try std.testing.expectEqual(@as(u32, 1), result.status);
        try std.testing.expectEqual(@as(?[*]const u8, null), result.data);
        try std.testing.expectEqual(@as(usize, 0), result.size);
        zrFree(&state);

        try testTokenPositions(&state, "const x = \"π€😀\";", .zig, &.{ 0, 5, 6, 7, 8, 9, 10, 16, 16, 17, 17, 17 });
    }
}

test "failed parse clears the result and state can be reused after free" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();
    _ = zrAllocInput(&state, 1) orelse return error.OutOfMemory;
    state.result = .{ .data = "stale".ptr, .size = 5 };
    const result = zrParseAst(&state, null, 1, .zig);

    try std.testing.expectEqual(@as(u32, 1), result.status);
    try std.testing.expectEqual(@as(?[*]const u8, null), result.data);
    try std.testing.expectEqual(@as(usize, 0), result.size);

    zrFree(&state);
    try testSnapshot(&state, "const answer = 42;", .zig, 3);
}

test "out of memory returns no snapshot and permits reuse after free" {
    var backing = std.testing.FailingAllocator.init(std.testing.allocator, .{});
    var state: ScopedState = .{ .arena = .init(backing.allocator()) };
    defer state.arena.deinit();
    const source = "const answer = 42;";
    const input = zrAllocInput(&state, source.len) orelse return error.OutOfMemory;
    @memcpy(input[0..source.len], source);
    backing.fail_index = backing.alloc_index;
    backing.resize_fail_index = backing.resize_index;
    const result = zrParseAst(&state, input, source.len, .zig);

    try std.testing.expect(backing.has_induced_failure);
    try std.testing.expectEqual(@as(u32, 3), result.status);
    try std.testing.expectEqual(@as(?[*]const u8, null), result.data);
    try std.testing.expectEqual(@as(usize, 0), result.size);
    zrFree(&state);

    // Exceed retained capacity so input allocation also reaches the failing backing allocator.
    try std.testing.expectEqual(@as(?[*]u8, null), zrAllocInput(&state, state.arena.queryCapacity()));
    zrFree(&state);
    backing.fail_index = std.math.maxInt(usize);
    backing.resize_fail_index = std.math.maxInt(usize);

    try testSnapshot(&state, source, .zig, 3);
}

test "growing input preserves previous allocations until free" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();
    const input = zrAllocInput(&state, 3) orelse return error.OutOfMemory;
    @memcpy(input[0..3], "abc");
    const expanded = zrAllocInput(&state, 1000) orelse return error.OutOfMemory;
    @memcpy(expanded[0..3], input[0..3]);

    try std.testing.expectEqualStrings("abc", expanded[0..3]);
    try std.testing.expectEqual(@as(u8, 0), expanded[1000]);

    zrFree(&state);
}
