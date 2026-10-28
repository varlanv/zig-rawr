const std = @import("std");
const Ast = @import("upstream/Ast.zig");
const Token = @import("upstream/tokenizer.zig").Token;

const Allocator = std.mem.Allocator;

comptime {
    std.debug.assert(@sizeOf(Token.Tag) == 1);
    std.debug.assert(@sizeOf(Ast.Node.Tag) == 1);
}

pub const State = struct {
    allocator: Allocator,
};

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
    sourceLen: u32,
    tokenCount: u32,
    nodeCount: u32,
    extraDataCount: u32,
    errorCount: u32,
    tokenTagsOffset: u32,
    tokenStartsOffset: u32,
    nodeTagsOffset: u32,
    nodeMainTokensOffset: u32,
    nodePayloadsOffset: u32,
    extraDataOffset: u32,
    errorsOffset: u32,
    totalSize: u32,
};

const Layout = struct {
    tokenTags: u32,
    tokenStarts: u32,
    nodeTags: u32,
    nodeMainTokens: u32,
    nodePayloads: u32,
    extraData: u32,
    errors: u32,
    totalSize: u32,
};

comptime {
    // sanity check because this assumption needs to hold on Kotlin side as well
    std.debug.assert(@sizeOf(SnapshotHeader) == 64);
    std.debug.assert(@sizeOf(NodePayload) == 8);
    std.debug.assert(@sizeOf(ErrorView) == 24);
}

pub export fn zrInit() ?*State {
    const state = std.heap.page_allocator.create(State) catch return null;
    state.* = .{
        .allocator = std.heap.smp_allocator,
    };
    return state;
}

pub export fn zrClose(state: *State) void {
    std.heap.page_allocator.destroy(state);
}

pub const Mode = enum(u32) {
    zig = 0,
    zon = 1,
    _,
};

pub export fn zrParse(
    state: *State,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
    mode: Mode,
    outputPtr: ?[*]u8,
    outputCapacity: usize,
    requiredSize: *usize,
) u32 {
    requiredSize.* = 0;
    if (sourceLen > std.math.maxInt(u32)) return 1;
    const parseMode: Ast.Mode = switch (mode) {
        .zig => .zig,
        .zon => .zon,
        else => return 1,
    };
    const source: [:0]const u8 = if (sourcePtr) |ptr| blk: {
        if (ptr[sourceLen] != 0) return 1;
        break :blk ptr[0..sourceLen :0];
    } else if (sourceLen == 0) "" else return 1;
    return parseInto(
        state.allocator,
        source,
        parseMode,
        outputPtr,
        outputCapacity,
        requiredSize,
    ) catch |err| switch (err) {
        error.TooLarge => 1,
        error.OutOfMemory => 3,
    };
}

fn reserve(cursor: *usize, alignment: usize, count: usize, itemSize: usize) error{TooLarge}!u32 {
    const start = std.mem.alignForward(usize, cursor.*, alignment);
    const size = std.math.mul(usize, count, itemSize) catch return error.TooLarge;
    const end = std.math.add(usize, start, size) catch return error.TooLarge;
    if (end > std.math.maxInt(u32)) return error.TooLarge;
    cursor.* = end;
    return @intCast(start);
}

fn layoutFor(ast: *const Ast) error{TooLarge}!Layout {
    if (ast.tokens.len > std.math.maxInt(u32) or
        ast.nodes.len > std.math.maxInt(u32) or
        ast.extra_data.len > std.math.maxInt(u32) or
        ast.errors.len > std.math.maxInt(u32)) return error.TooLarge;

    var cursor: usize = @sizeOf(SnapshotHeader);
    const tokenTags = try reserve(&cursor, 1, ast.tokens.len, 1);
    const tokenStarts = try reserve(&cursor, 4, ast.tokens.len, 4);
    const nodeTags = try reserve(&cursor, 1, ast.nodes.len, 1);
    const nodeMainTokens = try reserve(&cursor, 4, ast.nodes.len, 4);
    const nodePayloads = try reserve(&cursor, 4, ast.nodes.len, @sizeOf(NodePayload));
    const extraData = try reserve(&cursor, 4, ast.extra_data.len, 4);
    const errors = try reserve(&cursor, 8, ast.errors.len, @sizeOf(ErrorView));
    return .{
        .tokenTags = tokenTags,
        .tokenStarts = tokenStarts,
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
    outputPtr: ?[*]u8,
    outputCapacity: usize,
    requiredSize: *usize,
) (error{ TooLarge, OutOfMemory })!u32 {
    var ast = try Ast.parse(gpa, source, .{ .mode = mode });
    defer ast.deinit(gpa);

    const layout = try layoutFor(&ast);
    requiredSize.* = layout.totalSize;
    if (outputCapacity < layout.totalSize or outputPtr == null) return 2;
    const ptr = outputPtr.?;
    if ((@intFromPtr(ptr) & 7) != 0) return 1;
    const output = ptr[0..layout.totalSize];

    const header: SnapshotHeader = .{
        .magic = 0x5A524153,
        .abiVersion = 2,
        .mode = @backingInt(mode),
        .sourceLen = @intCast(source.len),
        .tokenCount = @intCast(ast.tokens.len),
        .nodeCount = @intCast(ast.nodes.len),
        .extraDataCount = @intCast(ast.extra_data.len),
        .errorCount = @intCast(ast.errors.len),
        .tokenTagsOffset = layout.tokenTags,
        .tokenStartsOffset = layout.tokenStarts,
        .nodeTagsOffset = layout.nodeTags,
        .nodeMainTokensOffset = layout.nodeMainTokens,
        .nodePayloadsOffset = layout.nodePayloads,
        .extraDataOffset = layout.extraData,
        .errorsOffset = layout.errors,
        .totalSize = layout.totalSize,
    };
    @memcpy(output[0..@sizeOf(SnapshotHeader)], std.mem.asBytes(&header));

    const tokenTags: []const u8 = @as([*]const u8, @ptrCast(ast.tokens.items(.tag).ptr))[0..ast.tokens.len];
    @memcpy(output[layout.tokenTags..][0..tokenTags.len], tokenTags);
    const tokenStarts = std.mem.sliceAsBytes(ast.tokens.items(.start));
    @memcpy(output[layout.tokenStarts..][0..tokenStarts.len], tokenStarts);

    const nodeTags: []const u8 = @as([*]const u8, @ptrCast(ast.nodes.items(.tag).ptr))[0..ast.nodes.len];
    @memcpy(output[layout.nodeTags..][0..nodeTags.len], nodeTags);
    const nodeMainTokens = std.mem.sliceAsBytes(ast.nodes.items(.main_token));
    @memcpy(output[layout.nodeMainTokens..][0..nodeMainTokens.len], nodeMainTokens);

    for (0..ast.nodes.len) |index| {
        const payload = nodePayload(&ast, index);
        const start = @as(usize, layout.nodePayloads) + index * @sizeOf(NodePayload);
        @memcpy(output[start..][0..@sizeOf(NodePayload)], std.mem.asBytes(&payload));
    }

    const extraData = std.mem.sliceAsBytes(ast.extra_data);
    @memcpy(output[layout.extraData..][0..extraData.len], extraData);

    for (ast.errors, 0..) |err, index| {
        const view = errorView(err);
        const start = @as(usize, layout.errors) + index * @sizeOf(ErrorView);
        @memcpy(output[start..][0..@sizeOf(ErrorView)], std.mem.asBytes(&view));
    }
    return 0;
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

fn testSnapshot(source: [:0]const u8, mode: Mode, minimumNodes: u32) !void {
    var state: State = .{ .allocator = std.testing.allocator };
    var requiredSize: usize = 0;
    try std.testing.expectEqual(@as(u32, 2), zrParse(
        &state,
        source.ptr,
        source.len,
        mode,
        null,
        0,
        &requiredSize,
    ));
    try std.testing.expect(requiredSize >= @sizeOf(SnapshotHeader));

    const output = try std.testing.allocator.alignedAlloc(u8, .@"8", requiredSize);
    defer std.testing.allocator.free(output);
    try std.testing.expectEqual(@as(u32, 0), zrParse(
        &state,
        source.ptr,
        source.len,
        mode,
        output.ptr,
        output.len,
        &requiredSize,
    ));
    const header: *const SnapshotHeader = @ptrCast(output.ptr);
    try std.testing.expectEqual(@as(u32, 0x5A524153), header.magic);
    try std.testing.expectEqual(@as(u32, 2), header.abiVersion);
    try std.testing.expectEqual(@as(u32, @backingInt(mode)), header.mode);
    try std.testing.expectEqual(@as(u32, @intCast(output.len)), header.totalSize);
    try std.testing.expect(header.nodeCount >= minimumNodes);
    try std.testing.expect(header.tokenCount > 0);
    try std.testing.expectEqual(@as(u8, 0), output[header.nodeTagsOffset]);
}

test "parse into a caller-owned snapshot" {
    try testSnapshot("const answer = 42;", .zig, 3);
    try testSnapshot(".{ .answer = 42 }", .zon, 2);
    try testSnapshot("const = ;", .zig, 1);
}

test "snapshot parses copied parser sources" {
    inline for (.{
        @embedFile("upstream/Ast.zig"),
        @embedFile("upstream/Parse.zig"),
        @embedFile("upstream/tokenizer.zig"),
    }) |source| {
        try testSnapshot(source, .zig, 100);
    }
}
