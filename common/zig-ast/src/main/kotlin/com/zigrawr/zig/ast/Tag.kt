package com.zigrawr.zig.ast

// todo - omit documentation here and paste link to Zig sources
enum class Tag {
    /// The root node which is guaranteed to be at `Node.Index.root`.
    /// The meaning of the `data` field depends on whether it is a `.zig` or
    /// `.zon` file.
    ///
    /// The `main_token` field is the first token for the source file.
    root_,

    /// `test {}`_,
    /// `test "name" {}`_,
    /// `test identifier {}`.
    ///
    /// The `data` field is a `.opt_token_and_node`:
    ///   1. a `OptionalTokenIndex` to the test name token (must be string literal or identifier), if any.
    ///   2. a `Node.Index` to the block.
    ///
    /// The `main_token` field is the `test` token.
    test_decl_,

    /// The `data` field is a `.extra_and_opt_node`:
    ///   1. a `ExtraIndex` to `GlobalVarDecl`.
    ///   2. a `Node.OptionalIndex` to the initialization expression.
    ///
    /// The `main_token` field is the `var` or `const` token.
    ///
    /// The initialization expression can't be `.none` unless it is part of
    /// a `assign_destructure` node or a parsing error occured.
    global_var_decl_,

    /// `var a: b align(c) = d`.
    /// `const main_token: type_node align(align_node) = init_expr`.
    ///
    /// The `data` field is a `.extra_and_opt_node`:
    ///   1. a `ExtraIndex` to `LocalVarDecl`.
    ///   2. a `Node.OptionalIndex` to the initialization expression-
    ///
    /// The `main_token` field is the `var` or `const` token.
    ///
    /// The initialization expression can't be `.none` unless it is part of
    /// a `assign_destructure` node or a parsing error occured.
    local_var_decl_,

    /// `var a: b = c`.
    /// `const name_token: type_expr = init_expr`.
    /// Can be local or global.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the type expression, if any.
    ///   2. a `Node.OptionalIndex` to the initialization expression.
    ///
    /// The `main_token` field is the `var` or `const` token.
    ///
    /// The initialization expression can't be `.none` unless it is part of
    /// a `assign_destructure` node or a parsing error occured.
    simple_var_decl_,

    /// `var a align(b) = c`.
    /// `const name_token align(align_expr) = init_expr`.
    /// Can be local or global.
    ///
    /// The `data` field is a `.node_and_opt_node`:
    ///   1. a `Node.Index` to the alignment expression.
    ///   2. a `Node.OptionalIndex` to the initialization expression.
    ///
    /// The `main_token` field is the `var` or `const` token.
    ///
    /// The initialization expression can't be `.none` unless it is part of
    /// a `assign_destructure` node or a parsing error occured.
    aligned_var_decl_,

    /// `errdefer expr`_,
    ///
    /// The `data` field is a `.node` to the deferred expression.
    ///
    /// The `main_token` field is the `errdefer` token.
    errdefer_,

    /// `defer expr`.
    ///
    /// The `data` field is a `.node` to the deferred expression.
    ///
    /// The `main_token` field is the `defer` token.
    defer_,

    /// `lhs catch rhs`_,
    /// `lhs catch |err| rhs`.
    ///
    /// The `main_token` field is the `catch` token.
    ///
    /// The error payload is determined by looking at the next token after
    /// the `catch` token.
    catch_,

    /// `lhs.a`.
    ///
    /// The `data` field is a `.node_and_token`:
    ///   1. a `Node.Index` to the left side of the field access.
    ///   2. a `TokenIndex` to the field name identifier.
    ///
    /// The `main_token` field is the `.` token.
    field_access_,

    /// `lhs.?`.
    ///
    /// The `data` field is a `.node_and_token`:
    ///   1. a `Node.Index` to the left side of the optional unwrap.
    ///   2. a `TokenIndex` to the `?` token.
    ///
    /// The `main_token` field is the `.` token.
    unwrap_optional_,

    /// `lhs == rhs`. The `main_token` field is the `==` token.
    equal_equal_,

    /// `lhs != rhs`. The `main_token` field is the `!=` token.
    bang_equal_,

    /// `lhs < rhs`. The `main_token` field is the `<` token.
    less_than_,

    /// `lhs > rhs`. The `main_token` field is the `>` token.
    greater_than_,

    /// `lhs <= rhs`. The `main_token` field is the `<=` token.
    less_or_equal_,

    /// `lhs >= rhs`. The `main_token` field is the `>=` token.
    greater_or_equal_,

    /// `lhs *= rhs`. The `main_token` field is the `*=` token.
    assign_mul_,

    /// `lhs /= rhs`. The `main_token` field is the `/=` token.
    assign_div_,

    /// `lhs %= rhs`. The `main_token` field is the `%=` token.
    assign_mod_,

    /// `lhs += rhs`. The `main_token` field is the `+=` token.
    assign_add_,

    /// `lhs -= rhs`. The `main_token` field is the `-=` token.
    assign_sub_,

    /// `lhs <<= rhs`. The `main_token` field is the `<<=` token.
    assign_shl_,

    /// `lhs <<|= rhs`. The `main_token` field is the `<<|=` token.
    assign_shl_sat_,

    /// `lhs >>= rhs`. The `main_token` field is the `>>=` token.
    assign_shr_,

    /// `lhs &= rhs`. The `main_token` field is the `&=` token.
    assign_bit_and_,

    /// `lhs ^= rhs`. The `main_token` field is the `^=` token.
    assign_bit_xor_,

    /// `lhs |= rhs`. The `main_token` field is the `|=` token.
    assign_bit_or_,

    /// `lhs *%= rhs`. The `main_token` field is the `*%=` token.
    assign_mul_wrap_,

    /// `lhs +%= rhs`. The `main_token` field is the `+%=` token.
    assign_add_wrap_,

    /// `lhs -%= rhs`. The `main_token` field is the `-%=` token.
    assign_sub_wrap_,

    /// `lhs *|= rhs`. The `main_token` field is the `*%=` token.
    assign_mul_sat_,

    /// `lhs +|= rhs`. The `main_token` field is the `+|=` token.
    assign_add_sat_,

    /// `lhs -|= rhs`. The `main_token` field is the `-|=` token.
    assign_sub_sat_,

    /// `lhs = rhs`. The `main_token` field is the `=` token.
    assign_,

    /// `a, b, ... = rhs`.
    ///
    /// The `data` field is a `.extra_and_node`:
    ///   1. a `ExtraIndex`. Further explained below.
    ///   2. a `Node.Index` to the initialization expression.
    ///
    /// The `main_token` field is the `=` token.
    ///
    /// The `ExtraIndex` stores the following data:
    /// ```
    /// elem_count: u32_,
    /// variables: [elem_count]Node.Index_,
    /// ```
    ///
    /// Each node in `variables` has one of the following tags:
    ///   - `global_var_decl`
    ///   - `local_var_decl`
    ///   - `simple_var_decl`
    ///   - `aligned_var_decl`
    ///   - Any expression node
    ///
    /// The first 4 tags correspond to a `var` or `const` lhs node (note
    /// that their initialization expression is always `.none`).
    /// An expression node corresponds to a standard assignment LHS (which
    /// must be evaluated as an lvalue). There may be a preceding
    /// `comptime` token, which does not create a corresponding `comptime`
    /// node so must be manually detected.
    assign_destructure_,

    /// `lhs || rhs`. The `main_token` field is the `||` token.
    merge_error_sets_,

    /// `lhs * rhs`. The `main_token` field is the `*` token.
    mul_,

    /// `lhs / rhs`. The `main_token` field is the `/` token.
    div_,

    /// `lhs % rhs`. The `main_token` field is the `%` token.
    mod_,

    /// `lhs *% rhs`. The `main_token` field is the `*%` token.
    mul_wrap_,

    /// `lhs *| rhs`. The `main_token` field is the `*|` token.
    mul_sat_,

    /// `lhs + rhs`. The `main_token` field is the `+` token.
    add_,

    /// `lhs - rhs`. The `main_token` field is the `-` token.
    sub_,

    /// `lhs ++ rhs`. The `main_token` field is the `++` token.
    array_cat_,

    /// `lhs +% rhs`. The `main_token` field is the `+%` token.
    add_wrap_,

    /// `lhs -% rhs`. The `main_token` field is the `-%` token.
    sub_wrap_,

    /// `lhs +| rhs`. The `main_token` field is the `+|` token.
    add_sat_,

    /// `lhs -| rhs`. The `main_token` field is the `-|` token.
    sub_sat_,

    /// `lhs << rhs`. The `main_token` field is the `<<` token.
    shl_,

    /// `lhs <<| rhs`. The `main_token` field is the `<<|` token.
    shl_sat_,

    /// `lhs >> rhs`. The `main_token` field is the `>>` token.
    shr_,

    /// `lhs & rhs`. The `main_token` field is the `&` token.
    bit_and_,

    /// `lhs ^ rhs`. The `main_token` field is the `^` token.
    bit_xor_,

    /// `lhs | rhs`. The `main_token` field is the `|` token.
    bit_or_,

    /// `lhs orelse rhs`. The `main_token` field is the `orelse` token.
    orelse_,

    /// `lhs and rhs`. The `main_token` field is the `and` token.
    bool_and_,

    /// `lhs or rhs`. The `main_token` field is the `or` token.
    bool_or_,

    /// `!expr`. The `main_token` field is the `!` token.
    bool_not_,

    /// `-expr`. The `main_token` field is the `-` token.
    negation_,

    /// `~expr`. The `main_token` field is the `~` token.
    bit_not_,

    /// `-%expr`. The `main_token` field is the `-%` token.
    negation_wrap_,

    /// `&expr`. The `main_token` field is the `&` token.
    address_of_,

    /// `try expr`. The `main_token` field is the `try` token.
    try_,

    /// `?expr`. The `main_token` field is the `?` token.
    optional_type_,

    /// `[lhs]rhs`. The `main_token` field is the `[` token.
    array_type_,

    /// `[lhs:a]b`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the length expression.
    ///   2. a `ExtraIndex` to `ArrayTypeSentinel`.
    ///
    /// The `main_token` field is the `[` token.
    array_type_sentinel_,

    /// `[*]align(lhs) rhs`_,
    /// `*align(lhs) rhs`_,
    /// `[]rhs`.
    ///
    /// The `data` field is a `.opt_node_and_node`:
    ///   1. a `Node.OptionalIndex` to the alignment expression, if any.
    ///   2. a `Node.Index` to the element type expression.
    ///
    /// The `main_token` is the asterisk if a single item pointer or the
    /// lbracket if a slice, many-item pointer, or C-pointer.
    ptr_type_aligned_,

    /// `[*:lhs]rhs`_,
    /// `*rhs`_,
    /// `[:lhs]rhs`.
    ///
    /// The `data` field is a `.opt_node_and_node`:
    ///   1. a `Node.OptionalIndex` to the sentinel expression, if any.
    ///   2. a `Node.Index` to the element type expression.
    ///
    /// The `main_token` is the asterisk if a single item pointer or the
    /// lbracket if a slice, many-item pointer, or C-pointer.
    ptr_type_sentinel_,

    /// The `data` field is a `.extra_and_node`:
    ///   1. a `ExtraIndex` to `PtrType`.
    ///   2. a `Node.Index` to the element type expression.
    ///
    /// The `main_token` is the asterisk if a single item pointer or the
    /// lbracket if a slice, many-item pointer, or C-pointer.
    ptr_type_,

    /// The `data` field is a `.extra_and_node`:
    ///   1. a `ExtraIndex` to `PtrTypeBitRange`.
    ///   2. a `Node.Index` to the element type expression.
    ///
    /// The `main_token` is the asterisk if a single item pointer or the
    /// lbracket if a slice, many-item pointer, or C-pointer.
    ptr_type_bit_range_,

    /// `lhs[rhs..]`
    ///
    /// The `main_token` field is the `[` token.
    slice_open_,

    /// `sliced[start..end]`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the sliced expression.
    ///   2. a `ExtraIndex` to `Slice`.
    ///
    /// The `main_token` field is the `[` token.
    slice_,

    /// `sliced[start..end :sentinel]`_,
    /// `sliced[start.. :sentinel]`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the sliced expression.
    ///   2. a `ExtraIndex` to `SliceSentinel`.
    ///
    /// The `main_token` field is the `[` token.
    slice_sentinel_,

    /// `expr.*`.
    ///
    /// The `data` field is a `.node` to expr.
    ///
    /// The `main_token` field is the `*` token.
    deref_,

    /// `lhs[rhs]`.
    ///
    /// The `main_token` field is the `[` token.
    array_access_,

    /// `lhs{rhs}`.
    ///
    /// The `main_token` field is the `{` token.
    array_init_one_,

    /// Same as `array_init_one` except there is known to be a trailing
    /// comma before the final rbrace.
    array_init_one_comma_,

    /// `.{a}`_,
    /// `.{a, b}`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first element. Never `.none`
    ///   2. a `Node.OptionalIndex` to the second element, if any.
    ///
    /// The `main_token` field is the `{` token.
    array_init_dot_two_,

    /// Same as `array_init_dot_two` except there is known to be a trailing
    /// comma before the final rbrace.
    array_init_dot_two_comma_,

    /// `.{a, b, c}`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each element.
    ///
    /// The `main_token` field is the `{` token.
    array_init_dot_,

    /// Same as `array_init_dot` except there is known to be a trailing
    /// comma before the final rbrace.
    array_init_dot_comma_,

    /// `a{b, c}`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the type expression.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each element.
    ///
    /// The `main_token` field is the `{` token.
    array_init_,

    /// Same as `array_init` except there is known to be a trailing comma
    /// before the final rbrace.
    array_init_comma_,

    /// `a{.x = b}`, `a{}`.
    ///
    /// The `data` field is a `.node_and_opt_node`:
    ///   1. a `Node.Index` to the type expression.
    ///   2. a `Node.OptionalIndex` to the first field initialization, if any.
    ///
    /// The `main_token` field is the `{` token.
    ///
    /// The field name is determined by looking at the tokens preceding the
    /// field initialization.
    struct_init_one_,

    /// Same as `struct_init_one` except there is known to be a trailing comma
    /// before the final rbrace.
    struct_init_one_comma_,

    /// `.{.x = a, .y = b}`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first field initialization. Never `.none`
    ///   2. a `Node.OptionalIndex` to the second field initialization, if any.
    ///
    /// The `main_token` field is the '{' token.
    ///
    /// The field name is determined by looking at the tokens preceding the
    /// field initialization.
    struct_init_dot_two_,

    /// Same as `struct_init_dot_two` except there is known to be a trailing
    /// comma before the final rbrace.
    struct_init_dot_two_comma_,

    /// `.{.x = a, .y = b, .z = c}`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each field initialization.
    ///
    /// The `main_token` field is the `{` token.
    ///
    /// The field name is determined by looking at the tokens preceding the
    /// field initialization.
    struct_init_dot_,

    /// Same as `struct_init_dot` except there is known to be a trailing
    /// comma before the final rbrace.
    struct_init_dot_comma_,

    /// `a{.x = b, .y = c}`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the type expression.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each field initialization.
    ///
    /// The `main_token` field is the `{` token.
    ///
    /// The field name is determined by looking at the tokens preceding the
    /// field initialization.
    struct_init_,

    /// Same as `struct_init` except there is known to be a trailing comma
    /// before the final rbrace.
    struct_init_comma_,

    /// `a(b)`, `a()`.
    ///
    /// The `data` field is a `.node_and_opt_node`:
    ///   1. a `Node.Index` to the function expression.
    ///   2. a `Node.OptionalIndex` to the first argument, if any.
    ///
    /// The `main_token` field is the `(` token.
    call_one_,

    /// Same as `call_one` except there is known to be a trailing comma
    /// before the final rparen.
    call_one_comma_,

    /// `a(b, c, d)`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the function expression.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each argument.
    ///
    /// The `main_token` field is the `(` token.
    call_,

    /// Same as `call` except there is known to be a trailing comma before
    /// the final rparen.
    call_comma_,

    /// `switch(a) {}`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the switch operand.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each switch case.
    ///
    /// `The `main_token` field` is the identifier of a preceding label, if any; otherwise `switch`.
    switch_,

    /// Same as `switch` except there is known to be a trailing comma before
    /// the final rbrace.
    switch_comma_,

    /// `a => b`_,
    /// `else => b`.
    ///
    /// The `data` field is a `.opt_node_and_node`:
    ///   1. a `Node.OptionalIndex` where `.none` means `else`.
    ///   2. a `Node.Index` to the target expression.
    ///
    /// The `main_token` field is the `=>` token.
    switch_case_one_,

    /// Same as `switch_case_one` but the case is inline.
    switch_case_inline_one_,

    /// `a, b, c => d`.
    ///
    /// The `data` field is a `.extra_and_node`:
    ///   1. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each switch item.
    ///   2. a `Node.Index` to the target expression.
    ///
    /// The `main_token` field is the `=>` token.
    switch_case_,

    /// Same as `switch_case` but the case is inline.
    switch_case_inline_,

    /// `lhs...rhs`.
    ///
    /// The `main_token` field is the `...` token.
    switch_range_,

    /// `while (a) b`_,
    /// `while (a) |x| b`.
    while_simple_,

    /// `while (a) : (b) c`_,
    /// `while (a) |x| : (b) c`.
    while_cont_,

    /// `while (a) : (b) c else d`_,
    /// `while (a) |x| : (b) c else d`_,
    /// `while (a) |x| : (b) c else |y| d`.
    /// The continue expression part `: (b)` may be omitted.
    while_,

    /// `for (a) b`.
    for_simple_,

    /// `for (lhs[0..inputs]) lhs[inputs + 1] else lhs[inputs + 2]`. `For[rhs]`.
    for_,

    /// `lhs..rhs`, `lhs..`.
    for_range_,

    /// `if (a) b`.
    /// `if (b) |x| b`.
    if_simple_,

    /// `if (a) b else c`.
    /// `if (a) |x| b else c`.
    /// `if (a) |x| b else |y| d`.
    if_,

    /// `suspend expr`.
    ///
    /// The `data` field is a `.node` to expr.
    ///
    /// The `main_token` field is the `suspend` token.
    suspend_,

    /// `resume expr`.
    ///
    /// The `data` field is a `.node` to expr.
    ///
    /// The `main_token` field is the `resume` token.
    resume_,

    /// `continue :label expr`_,
    /// `continue expr`_,
    /// `continue :label`_,
    /// `continue`.
    ///
    /// The `data` field is a `.opt_token_and_opt_node`:
    ///   1. a `OptionalTokenIndex` to the label identifier, if any.
    ///   2. a `Node.OptionalIndex` to the target expression, if any.
    ///
    /// The `main_token` field is the `continue` token.
    continue_,

    /// `break :label expr`_,
    /// `break expr`_,
    /// `break :label`_,
    /// `break`.
    ///
    /// The `data` field is a `.opt_token_and_opt_node`:
    ///   1. a `OptionalTokenIndex` to the label identifier, if any.
    ///   2. a `Node.OptionalIndex` to the target expression, if any.
    ///
    /// The `main_token` field is the `break` token.
    break_,

    /// `return expr`, `return`.
    ///
    /// The `data` field is a `.opt_node` to the return value, if any.
    ///
    /// The `main_token` field is the `return` token.
    return_,

    /// `fn (a: type_expr) return_type`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first parameter type expression, if any.
    ///   2. a `Node.OptionalIndex` to the return type expression. Can't be
    ///      `.none` unless a parsing error occured.
    ///
    /// The `main_token` field is the `fn` token.
    ///
    /// `anytype` and `...` parameters are omitted from the AST tree.
    /// Extern function declarations use this tag.
    fn_proto_simple_,

    /// `fn (a: b, c: d) return_type`.
    ///
    /// The `data` field is a `.extra_and_opt_node`:
    ///   1. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each parameter type expression.
    ///   2. a `Node.OptionalIndex` to the return type expression. Can't be
    ///      `.none` unless a parsing error occured.
    ///
    /// The `main_token` field is the `fn` token.
    ///
    /// `anytype` and `...` parameters are omitted from the AST tree.
    /// Extern function declarations use this tag.
    fn_proto_multi_,

    /// `fn (a: b) addrspace(e) linksection(f) callconv(g) return_type`.
    /// zero or one parameters.
    ///
    /// The `data` field is a `.extra_and_opt_node`:
    ///   1. a `Node.ExtraIndex` to `FnProtoOne`.
    ///   2. a `Node.OptionalIndex` to the return type expression. Can't be
    ///      `.none` unless a parsing error occured.
    ///
    /// The `main_token` field is the `fn` token.
    ///
    /// `anytype` and `...` parameters are omitted from the AST tree.
    /// Extern function declarations use this tag.
    fn_proto_one_,

    /// `fn (a: b, c: d) addrspace(e) linksection(f) callconv(g) return_type`.
    ///
    /// The `data` field is a `.extra_and_opt_node`:
    ///   1. a `Node.ExtraIndex` to `FnProto`.
    ///   2. a `Node.OptionalIndex` to the return type expression. Can't be
    ///      `.none` unless a parsing error occured.
    ///
    /// The `main_token` field is the `fn` token.
    ///
    /// `anytype` and `...` parameters are omitted from the AST tree.
    /// Extern function declarations use this tag.
    fn_proto_,

    /// Extern function declarations use the fn_proto tags rather than this one.
    ///
    /// The `data` field is a `.node_and_node`:
    ///   1. a `Node.Index` to `fn_proto_*`.
    ///   2. a `Node.Index` to function body block.
    ///
    /// The `main_token` field is the `fn` token.
    fn_decl_,

    /// `anyframe->return_type`.
    ///
    /// The `data` field is a `.token_and_node`:
    ///   1. a `TokenIndex` to the `->` token.
    ///   2. a `Node.Index` to the function frame return type expression.
    ///
    /// The `main_token` field is the `anyframe` token.
    anyframe_type_,

    /// The `data` field is unused.
    anyframe_literal_,

    /// The `data` field is unused.
    char_literal_,

    /// The `data` field is unused.
    number_literal_,

    /// The `data` field is unused.
    unreachable_literal_,

    /// The `data` field is unused.
    ///
    /// Most identifiers will not have explicit AST nodes, however for
    /// expressions which could be one of many different kinds of AST nodes_,
    /// there will be an identifier AST node for it.
    identifier_,

    /// `.foo`.
    ///
    /// The `data` field is unused.
    ///
    /// The `main_token` field is the identifier.
    enum_literal_,

    /// The `data` field is unused.
    ///
    /// The `main_token` field is the string literal token.
    string_literal_,

    /// The `data` field is a `.token_and_token`:
    ///   1. a `TokenIndex` to the first `.multiline_string_literal_line` token.
    ///   2. a `TokenIndex` to the last `.multiline_string_literal_line` token.
    ///
    /// The `main_token` field is the first token index (redundant with `data`).
    multiline_string_literal_,

    /// `(expr)`.
    ///
    /// The `data` field is a `.node_and_token`:
    ///   1. a `Node.Index` to the sub-expression
    ///   2. a `TokenIndex` to the `)` token.
    ///
    /// The `main_token` field is the `(` token.
    grouped_expression_,

    /// `@a(b, c)`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first argument, if any.
    ///   2. a `Node.OptionalIndex` to the second argument, if any.
    ///
    /// The `main_token` field is the builtin token.
    builtin_call_two_,

    /// Same as `builtin_call_two` except there is known to be a trailing comma
    /// before the final rparen.
    builtin_call_two_comma_,

    /// `@a(b, c, d)`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each argument.
    ///
    /// The `main_token` field is the builtin token.
    builtin_call_,

    /// Same as `builtin_call` except there is known to be a trailing comma
    /// before the final rparen.
    builtin_call_comma_,

    /// `error{a, b}`.
    ///
    /// The `data` field is a `.token_and_token`:
    ///   1. a `TokenIndex` to the `{` token.
    ///   2. a `TokenIndex` to the `}` token.
    ///
    /// The `main_token` field is the `error`.
    error_set_decl_,

    /// `struct {}`, `union {}`, `opaque {}`, `enum {}`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each container member.
    ///
    /// The `main_token` field is the `struct`, `union`, `opaque` or `enum` token.
    container_decl_,

    /// Same as `container_decl` except there is known to be a trailing
    /// comma before the final rbrace.
    container_decl_trailing_,

    /// `struct {lhs, rhs}`, `union {lhs, rhs}`, `opaque {lhs, rhs}`, `enum {lhs, rhs}`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first container member, if any.
    ///   2. a `Node.OptionalIndex` to the second container member, if any.
    ///
    /// The `main_token` field is the `struct`, `union`, `opaque` or `enum` token.
    container_decl_two_,

    /// Same as `container_decl_two` except there is known to be a trailing
    /// comma before the final rbrace.
    container_decl_two_trailing_,

    /// `struct(arg)`, `union(arg)`, `enum(arg)`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to arg.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each container member.
    ///
    /// The `main_token` field is the `struct`, `union` or `enum` token.
    container_decl_arg_,

    /// Same as `container_decl_arg` except there is known to be a trailing
    /// comma before the final rbrace.
    container_decl_arg_trailing_,

    /// `union(enum) {}`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each container member.
    ///
    /// The `main_token` field is the `union` token.
    ///
    /// A tagged union with explicitly provided enums will instead be
    /// represented by `container_decl_arg`.
    tagged_union_,

    /// Same as `tagged_union` except there is known to be a trailing comma
    /// before the final rbrace.
    tagged_union_trailing_,

    /// `union(enum) {lhs, rhs}`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first container member, if any.
    ///   2. a `Node.OptionalIndex` to the second container member, if any.
    ///
    /// The `main_token` field is the `union` token.
    ///
    /// A tagged union with explicitly provided enums will instead be
    /// represented by `container_decl_arg`.
    tagged_union_two_,

    /// Same as `tagged_union_two` except there is known to be a trailing
    /// comma before the final rbrace.
    tagged_union_two_trailing_,

    /// `union(enum(arg)) {}`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to arg.
    ///   2. a `ExtraIndex` to a `SubRange` that stores a `Node.Index` for
    ///      each container member.
    ///
    /// The `main_token` field is the `union` token.
    tagged_union_enum_tag_,

    /// Same as `tagged_union_enum_tag` except there is known to be a
    /// trailing comma before the final rbrace.
    tagged_union_enum_tag_trailing_,

    /// `a: lhs = rhs,`_,
    /// `a: lhs,`.
    ///
    /// The `data` field is a `.node_and_opt_node`:
    ///   1. a `Node.Index` to the field type expression.
    ///   2. a `Node.OptionalIndex` to the default value expression, if any.
    ///
    /// The `main_token` field is the field name identifier.
    ///
    /// `lastToken()` does not include the possible trailing comma.
    container_field_init_,

    /// `a: lhs align(rhs),`.
    ///
    /// The `data` field is a `.node_and_node`:
    ///   1. a `Node.Index` to the field type expression.
    ///   2. a `Node.Index` to the alignment expression.
    ///
    /// The `main_token` field is the field name identifier.
    ///
    /// `lastToken()` does not include the possible trailing comma.
    container_field_align_,

    /// `a: lhs align(c) = d,`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to the field type expression.
    ///   2. a `ExtraIndex` to `ContainerField`.
    ///
    /// The `main_token` field is the field name identifier.
    ///
    /// `lastToken()` does not include the possible trailing comma.
    container_field_,

    /// `comptime expr`.
    ///
    /// The `data` field is a `.node` to expr.
    ///
    /// The `main_token` field is the `comptime` token.
    comptime_,

    /// `nosuspend expr`.
    ///
    /// The `data` field is a `.node` to expr.
    ///
    /// The `main_token` field is the `nosuspend` token.
    nosuspend_,

    /// `{lhs rhs}`.
    ///
    /// The `data` field is a `.opt_node_and_opt_node`:
    ///   1. a `Node.OptionalIndex` to the first statement, if any.
    ///   2. a `Node.OptionalIndex` to the second statement, if any.
    ///
    /// The `main_token` field is the `{` token.
    block_two_,

    /// Same as `block_two` except there is known to be a trailing
    /// comma before the final rbrace.
    block_two_semicolon_,

    /// `{a b}`.
    ///
    /// The `data` field is a `.extra_range` that stores a `Node.Index` for
    /// each statement.
    ///
    /// The `main_token` field is the `{` token.
    block_,

    /// Same as `block` except there is known to be a trailing comma before
    /// the final rbrace.
    block_semicolon_,

    /// `asm(a)`.
    ///
    /// The `main_token` field is the `asm` token.
    asm_simple_,

    /// `asm(a, b)`.
    ///
    /// The `data` field is a `.node_and_extra`:
    ///   1. a `Node.Index` to a.
    ///   2. a `ExtraIndex` to `Asm`.
    ///
    /// The `main_token` field is the `asm` token.
    asm_,

    /// `[a] "b" (c)`.
    /// `[a] "b" (-> lhs)`.
    ///
    /// The `data` field is a `.opt_node_and_token`:
    ///   1. a `Node.OptionalIndex` to lhs, if any.
    ///   2. a `TokenIndex` to the `)` token.
    ///
    /// The `main_token` field is `a`.
    asm_output_,

    /// `[a] "b" (lhs)`.
    ///
    /// The `data` field is a `.node_and_token`:
    ///   1. a `Node.Index` to lhs.
    ///   2. a `TokenIndex` to the `)` token.
    ///
    /// The `main_token` field is `a`.
    asm_input_,

    /// `error.a`.
    ///
    /// The `data` field is unused.
    ///
    /// The `main_token` field is `error` token.
    error_value_,

    /// `lhs!rhs`.
    ///
    /// The `main_token` field is the `!` token.
    error_union_;

    init {
        require(this.name.endsWith("_"))
    }

    fun isContainerField(): Boolean {
        return when (this) {
            container_field_init_,
            container_field_align_,
            container_field_ -> true

            else -> false
        }
    }
}
