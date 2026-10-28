const tokenizer = @import("upstream/tokenizer.zig");
const Parse = @import("upstream/Parse.zig");
const Ast = @import("upstream/Ast.zig");
const std = @import("std");

pub fn main() void {
    std.debug.print("This is not a command line tool", .{});
}

test {
    _ = tokenizer;
    _ = Parse;
    _ = Ast;
}
