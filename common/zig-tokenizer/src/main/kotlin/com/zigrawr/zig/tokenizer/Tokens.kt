package com.zigrawr.zig.tokenizer

class Tokens @OptIn(ExperimentalUnsignedTypes::class) constructor(
    private val tokens: ByteArray,
    private val positions: UIntArray,
    private val tags: Array<Tag>,
)
