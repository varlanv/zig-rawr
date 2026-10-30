const std = @import("std");
const Allocator = std.mem.Allocator;

const LANE_LENGTH = std.simd.suggestVectorLength(u8);

/// Converts sorted UTF-8 byte offsets at code point boundaries to UTF-16 code unit offsets.
/// Validates the source prefix through the last requested offset. The caller owns the result.
pub fn calculate_utf16_positions(
    gpa: Allocator,
    source: []const u8,
    byteOffsets: []const u32,
) error{ OutOfMemory, InvalidUtf8, InvalidOffset }![]const u32 {
    const output = try gpa.alloc(u32, byteOffsets.len);
    errdefer gpa.free(output);
    try write_utf16_positions(source, byteOffsets, output);
    return output;
}

/// Allocation-free conversion into an equally sized destination. Offsets must be
/// sorted and at code point boundaries. Input and output offset slices may be identical.
pub fn write_utf16_positions(
    source: []const u8,
    byteOffsets: []const u32,
    output: []u32,
) error{ InvalidUtf8, InvalidOffset }!void {
    std.debug.assert(byteOffsets.len == output.len);
    const sourceEnd: usize = if (byteOffsets.len == 0) 0 else byteOffsets[byteOffsets.len - 1];
    if (sourceEnd > source.len) return error.InvalidOffset;
    var bytePosition: usize = 0;
    var utf16Position: u32 = 0;
    var asciiEnd = ascii_prefix_length(source[0..sourceEnd]);
    if (asciiEnd == sourceEnd) {
        var previous: u32 = 0;
        for (byteOffsets, 0..) |offset, idx| {
            if (offset < previous or offset > sourceEnd) return error.InvalidOffset;
            output[idx] = offset;
            previous = offset;
        }
        return;
    }

    for (byteOffsets, 0..) |offset, idx| {
        if (offset < bytePosition or offset > sourceEnd) return error.InvalidOffset;
        while (bytePosition < offset) {
            if (source[bytePosition] < 0x80) {
                if (bytePosition >= asciiEnd) {
                    asciiEnd = bytePosition + ascii_prefix_length(source[bytePosition..sourceEnd]);
                }
                const end = @min(@as(usize, offset), asciiEnd);
                utf16Position += @intCast(end - bytePosition);
                bytePosition = end;
                continue;
            }
            const byteCount = std.unicode.utf8ByteSequenceLength(source[bytePosition]) catch
                return error.InvalidUtf8;
            if (byteCount > source.len - bytePosition) return error.InvalidUtf8;
            const bytes = source[bytePosition..][0..byteCount];
            const codepoint = switch (byteCount) {
                1 => @as(u21, bytes[0]),
                2 => std.unicode.utf8Decode2(bytes[0..2].*) catch return error.InvalidUtf8,
                3 => std.unicode.utf8Decode3(bytes[0..3].*) catch return error.InvalidUtf8,
                4 => std.unicode.utf8Decode4(bytes[0..4].*) catch return error.InvalidUtf8,
                else => unreachable,
            };
            if (byteCount > offset - bytePosition) return error.InvalidOffset;
            bytePosition += byteCount;
            utf16Position += if (codepoint > 0xffff) @as(u32, 2) else 1;
        }
        output[idx] = utf16Position;
    }
}

fn ascii_prefix_length(source: []const u8) usize {
    var index: usize = 0;
    if (LANE_LENGTH) |width| {
        while (source.len - index >= width) {
            const chunk: @Vector(width, u8) = source[index..][0..width].*;
            const mask: @Int(.unsigned, width) = @bitCast(chunk >= @as(@Vector(width, u8), @splat(0x80)));
            if (mask != 0) return index + @ctz(mask);
            index += width;
        }
    }
    while (index < source.len and source[index] < 0x80) : (index += 1) {}
    return index;
}

test "convert ASCII offsets including repeated boundaries and EOF" {
    const output = try calculate_utf16_positions(std.testing.allocator, "abc", &.{ 0, 1, 1, 3 });
    defer std.testing.allocator.free(output);

    try std.testing.expectEqualSlices(u32, &.{ 0, 1, 1, 3 }, output);
}

test "convert multibyte offsets and supplementary characters" {
    const output = try calculate_utf16_positions(
        std.testing.allocator,
        "aπ€😀b",
        &.{ 0, 1, 3, 6, 10, 11 },
    );
    defer std.testing.allocator.free(output);

    try std.testing.expectEqualSlices(u32, &.{ 0, 1, 2, 3, 5, 6 }, output);
}

test "convert empty input and an empty offset list" {
    const empty = try calculate_utf16_positions(std.testing.allocator, "", &.{});
    defer std.testing.allocator.free(empty);
    try std.testing.expectEqual(@as(usize, 0), empty.len);

    const eof = try calculate_utf16_positions(std.testing.allocator, "", &.{0});
    defer std.testing.allocator.free(eof);
    try std.testing.expectEqualSlices(u32, &.{0}, eof);
}

test "reject decreasing offsets, out of bounds offsets and split code points" {
    try std.testing.expectError(error.InvalidOffset, calculate_utf16_positions(std.testing.allocator, "abc", &.{ 2, 1 }));
    try std.testing.expectError(error.InvalidOffset, calculate_utf16_positions(std.testing.allocator, "abc", &.{4}));
    try std.testing.expectError(error.InvalidOffset, calculate_utf16_positions(std.testing.allocator, "π", &.{1}));
    try std.testing.expectError(error.InvalidOffset, calculate_utf16_positions(std.testing.allocator, "😀", &.{2}));
}

test "reject malformed UTF-8 and release the output allocation" {
    const sources = [_][]const u8{
        "\x80",
        "\xc0\x80",
        "\xe2\x82",
        "\xe2AB",
        "\xed\xa0\x80",
        "\xf4\x90\x80\x80",
    };
    for (sources) |source| {
        const offsets = [_]u32{@intCast(source.len)};
        try std.testing.expectError(error.InvalidUtf8, calculate_utf16_positions(std.testing.allocator, source, &offsets));
    }
}

test "propagate allocation failure" {
    var failing = std.testing.FailingAllocator.init(std.testing.allocator, .{ .fail_index = 0 });
    try std.testing.expectError(error.OutOfMemory, calculate_utf16_positions(failing.allocator(), "a", &.{1}));
}

test "convert dense boundaries across ASCII vector blocks" {
    const source: [257]u8 = @splat('a');
    var offsets: [258]u32 = undefined;
    for (&offsets, 0..) |*offset, index| offset.* = @intCast(index);

    const output = try calculate_utf16_positions(std.testing.allocator, &source, &offsets);
    defer std.testing.allocator.free(output);
    try std.testing.expectEqualSlices(u32, &offsets, output);
}

test "convert Unicode boundaries at every ASCII vector alignment" {
    const suffix = "π😀€b";
    var source: [138]u8 = @splat('a');
    for (0..129) |padding| {
        @memcpy(source[padding..][0..suffix.len], suffix);
        const start: u32 = @intCast(padding);
        const offsets = [_]u32{ 0, start, start + 2, start + 6, start + 9, start + 10 };
        const expected = [_]u32{ 0, start, start + 1, start + 3, start + 4, start + 5 };
        const output = try calculate_utf16_positions(std.testing.allocator, source[0 .. padding + suffix.len], &offsets);
        defer std.testing.allocator.free(output);
        try std.testing.expectEqualSlices(u32, &expected, output);

        const split = [_]u32{start + 1};
        try std.testing.expectError(error.InvalidOffset, calculate_utf16_positions(std.testing.allocator, source[0 .. padding + suffix.len], &split));
        @memset(source[padding..][0..suffix.len], 'a');
    }
}

test "validate only the requested prefix and reject invalid bytes after ASCII runs" {
    var source: [129]u8 = @splat('a');
    source[128] = 0x80;
    const output = try calculate_utf16_positions(std.testing.allocator, &source, &.{ 0, 32, 128 });
    defer std.testing.allocator.free(output);
    try std.testing.expectEqualSlices(u32, &.{ 0, 32, 128 }, output);
    try std.testing.expectError(error.InvalidUtf8, calculate_utf16_positions(std.testing.allocator, &source, &.{129}));
}
