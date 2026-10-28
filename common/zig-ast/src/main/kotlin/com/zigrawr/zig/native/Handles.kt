package com.zigrawr.zig.native

import java.lang.invoke.MethodHandle

class Handles(
    val zrInit: MethodHandle,
    val zrClose: MethodHandle,
    val zrParse: MethodHandle,
)
