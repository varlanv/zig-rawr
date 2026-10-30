package com.zigrawr.zig.foreign

import java.lang.invoke.MethodHandle

class Handles(
    val zrStartSession: MethodHandle,
    val zrCloseSession: MethodHandle,
    val zrParseAst: MethodHandle,
    val zrParseTokens: MethodHandle,
    val zrFree: MethodHandle,
    val zrAllocInput: MethodHandle,
)
