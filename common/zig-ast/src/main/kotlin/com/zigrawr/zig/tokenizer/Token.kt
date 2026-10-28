package com.zigrawr.zig.tokenizer

/**
 * Represents a position in Zig sources.
 * It intentionally violates Zig spec which says Zig source file max size is effectively u32 - roughly, 4GB.
 * Java arrays are i32-indexed, and working on such big files is not in the scope anyway, intentional tradeoff
 * is to have max size that fits into 64 bits: first 28 bits for "start", next 28 bits for "end" last 8 bits for "tag".
 * As a result, maximum allowed size of Zig sources for this library is roughly 268MB.
 */
@JvmInline
value class ZigSourcePosition(val value: Int) {

    init {
        assert(value <= MAX)
        assert(value >= 0)
    }

    operator fun plus(other: Int): ZigSourcePosition {
        return ZigSourcePosition(value + other)
    }

    companion object {
        private const val MAX = 268_435_455
        val MAX_VALUE = ZigSourcePosition(MAX)
        val MIN_VALUE = ZigSourcePosition(0)
    }
}

/**
 * Packs start into bits 0..27, end into bits 28..55, and tag into bits 56..63.
 */
@JvmInline
value class Token internal constructor(val packed: Long) {

    /**
     * Token Tag.
     */
    val tag: Tag
        get() {
            val idx = (packed ushr 56).toInt()
            return Tag.entries[idx]
        }


    /**
     * Position where token starts.
     */
    val start: ZigSourcePosition
        get() {
            return ZigSourcePosition((packed and POSITION_MASK).toInt())
        }

    /**
     * Position where token ends.
     */
    val end: ZigSourcePosition
        get() {
            return ZigSourcePosition(((packed ushr 28) and POSITION_MASK).toInt())
        }


    companion object {

        const val POSITION_MASK = 0x0FFF_FFFFL

        fun pack(start: ZigSourcePosition, end: ZigSourcePosition, tag: Tag): Token {
            val startBits = start.value.toLong() and POSITION_MASK
            val endBits = (end.value.toLong() and POSITION_MASK) shl 28
            val tagBits = tag.ordinal.toLong() shl 56
            return Token(startBits or endBits or tagBits)
        }

        fun unpackStart(start: ZigSourcePosition): Long = start.value.toLong() and POSITION_MASK
    }
}
