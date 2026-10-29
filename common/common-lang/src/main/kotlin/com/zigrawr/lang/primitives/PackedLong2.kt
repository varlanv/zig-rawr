package com.zigrawr.lang.primitives

/**
 * 2 ints packed as one long
 */
@JvmInline
value class PackedLong2(val packed: Long) {

    // Value 1
    inline val v1: Int get() = packed.toInt()

    // Value 2
    inline val v2: Int get() = (packed ushr 32).toInt()

    companion object {

        fun pack(v1: Int, v2: Int): PackedLong2 {
            return PackedLong2((v1.toLong() and 0xFFFF_FFFFL) or (v2.toLong() shl 32))
        }
    }
}
