package com.zigrawr.lang.primitives

@JvmInline
value class PackedIntPair(val packed: Long) {

    val left: Int get() = packed.toInt()
    val right: Int get() = (packed ushr 32).toInt()

    companion object {

        fun pack(left: Int, right: Int): PackedIntPair {
            return PackedIntPair((left.toLong() and 0xFFFF_FFFFL) or (right.toLong() shl 32))
        }
    }
}
