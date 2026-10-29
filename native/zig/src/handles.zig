const std = @import("std");
const Ast = @import("ziglang/Ast.zig");
const shared = @import("shared.zig");
const export_ast = @import("export_ast.zig");
const Allocator = std.mem.Allocator;
const ParseResult = export_ast.ParseResult;
const SnapshotHeader = export_ast.SnapshotHeader;
const ScopedState = shared.ScopedState;
const NodePayload = export_ast.NodePayload;
const ErrorView = export_ast.ErrorView;
const Mode = shared.Mode;
const Layout = export_ast.Layout;
const MAGIC = shared.MAGIC;

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
/// Failing to call this after calling `zrParse` will result in undefined behavior on in consumer library
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

pub fn zrParse(
    state: *ScopedState,
    sourcePtr: ?[*]const u8,
    sourceLen: usize,
    mode: Mode,
) *const ParseResult {
    if (state.tag != .allocate) {
        panicOnInvaliStateTransition(state.tag, .parse);
    }
    state.tag = .parse;
    const result = &state.result;
    result.* = .{ .status = 1 };
    if (sourceLen > std.math.maxInt(u32)) return result;
    const parseMode: Ast.Mode = switch (mode) {
        .zig => .zig,
        .zon => .zon,
        else => return result,
    };
    if (sourcePtr == null and sourceLen != 0) return result;
    const ptr = sourcePtr orelse "".ptr;
    if (ptr[sourceLen] != 0) return result;
    const source = ptr[0..sourceLen :0];
    const output = parseInto(
        state.arena.allocator(),
        source,
        parseMode,
    ) catch |err| {
        result.status = switch (err) {
            error.TooLarge => 1,
            error.OutOfMemory => 3,
        };
        return result;
    };
    result.* = .{ .data = output.ptr, .size = output.len };
    return result;
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
) (error{ TooLarge, OutOfMemory })![]align(8) u8 {
    const ast = try Ast.parse(gpa, source, .{ .mode = mode });
    const layout = try layoutFor(&ast);
    const output = try gpa.alignedAlloc(u8, .@"8", layout.totalSize);
    const header: SnapshotHeader = .{
        .magic = MAGIC,
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
    @memset(output[layout.tokenTags + tokenTags.len .. layout.tokenStarts], 0);
    const tokenStarts = std.mem.sliceAsBytes(ast.tokens.items(.start));
    @memcpy(output[layout.tokenStarts..][0..tokenStarts.len], tokenStarts);

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

    const result = zrParse(state, input, source.len, mode);

    try std.testing.expectEqual(@as(u32, 0), result.status);
    try std.testing.expect(result.size >= @sizeOf(SnapshotHeader));
    const output = result.data.?[0..result.size];
    const header: *const SnapshotHeader = @ptrCast(@alignCast(output.ptr));

    try std.testing.expectEqual(@as(u32, MAGIC), header.magic);
    try std.testing.expectEqual(@as(u32, 2), header.abiVersion);
    try std.testing.expectEqual(@as(u32, @backingInt(mode)), header.mode);
    try std.testing.expectEqual(@as(u32, @intCast(output.len)), header.totalSize);
    try std.testing.expect(header.nodeCount >= minimumNodes);
    try std.testing.expect(header.tokenCount > 0);
    try std.testing.expectEqual(@as(u8, 0), output[header.nodeTagsOffset]);
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

test "failed parse clears the result and state can be reused after free" {
    var state: ScopedState = .{ .arena = .init(std.testing.allocator) };
    defer state.arena.deinit();
    _ = zrAllocInput(&state, 1) orelse return error.OutOfMemory;
    state.result = .{ .data = "stale".ptr, .size = 5 };
    const result = zrParse(&state, null, 1, .zig);

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
    const result = zrParse(&state, input, source.len, .zig);

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
