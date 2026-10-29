package com.zigrawr.zig.foreign

import java.lang.invoke.MethodHandle

class Handles(
    val zrStartSession: MethodHandle,
    val zrCloseSession: MethodHandle,
    val zrParse: MethodHandle,
    val zrFree: MethodHandle,
    val zrAllocInput: MethodHandle,
)
