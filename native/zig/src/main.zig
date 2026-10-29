const tokenizer = @import("ziglang/tokenizer.zig");
const Parse = @import("ziglang/Parse.zig");
const Ast = @import("ziglang/Ast.zig");
const std = @import("std");

pub fn main() void {
    std.debug.print("This is not a command line tool", .{});
}

test {
    _ = tokenizer;
    _ = Parse;
    _ = Ast;
}
