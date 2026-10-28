package com.zigrawr.zig.tokenizer

import com.zigrawr.lang.bin.Bytes

class Tokens @OptIn(ExperimentalUnsignedTypes::class) constructor(
    private val tokens: Bytes,
    private val positions: UIntArray,
    private val tags: Array<Tag>,
)
