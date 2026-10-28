package com.zigrawr.lang.bin

import com.zigrawr.lang.longViewHandle

internal sealed interface BytesHash {
    fun hash(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int
}

private object ScalarBytesHash : BytesHash {
    override fun hash(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int = hashSwar(bytes = bytes, start = start, end = end, initial = 1)
}

internal fun hashSwar(
    bytes: Bytes,
    start: Int,
    end: Int,
    initial: Int,
): Int {
    var result = initial
    var index = start
    while (index <= end - Long.SIZE_BYTES) {
        val word = longViewHandle.get(bytes, index) as Long
        result = 31 * result + word.toByte()
        result = 31 * result + (word ushr 8).toByte()
        result = 31 * result + (word ushr 16).toByte()
        result = 31 * result + (word ushr 24).toByte()
        result = 31 * result + (word ushr 32).toByte()
        result = 31 * result + (word ushr 40).toByte()
        result = 31 * result + (word ushr 48).toByte()
        result = 31 * result + (word ushr 56).toByte()
        index += Long.SIZE_BYTES
    }
    while (index < end) {
        result = 31 * result + bytes[index]
        index++
    }
    return result
}
