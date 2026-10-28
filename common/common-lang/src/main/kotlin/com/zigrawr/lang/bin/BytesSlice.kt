@file:Suppress("NOTHING_TO_INLINE")

package com.zigrawr.lang.bin

class BytesSlice(
    val bytes: Bytes,
    val offset: Int,
    val len: Int,
) {
    init {
        require(offset >= 0 && len >= 0 && offset <= bytes.size - len)
    }

    inline fun getPackedInt(idx: Int): Int {
        require(idx >= 0 && idx <= len - Int.SIZE_BYTES)
        return bytes.bytes.getPackedInt(offset + idx)
    }

    inline fun getPackedLong(idx: Int): Long {
        require(idx >= 0 && idx <= len - Long.SIZE_BYTES)
        return bytes.bytes.getPackedLong(offset + idx)
    }

    inline fun getPackedShort(idx: Int): Short {
        require(idx >= 0 && idx <= len - Short.SIZE_BYTES)
        return bytes.bytes.getPackedShort(offset + idx)
    }

    inline fun forEach(block: (Byte) -> Unit) {
        val limit = offset + len
        for (idx in offset until limit) {
            block(bytes.bytes.impl[idx])
        }
    }

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) {
        val limit = offset + len
        for (idx in offset until limit) {
            block(idx - offset, bytes.bytes.impl[idx])
        }
    }

    inline operator fun get(idx: Int): Byte {
        require(idx in 0..<len)
        return bytes.bytes.impl[offset + idx]
    }

    inline fun byteArrayClone(): ByteArray {
        val res = ByteArray(len)
        bytes.copyInto(destination = res.asMut(), destinationOffset = 0, startIndex = offset, endIndex = len)
        return res
    }

    override fun equals(other: Any?): Boolean =
        other is BytesSlice &&
            bytes.mismatch(
                aFromIndex = offset,
                aToIndex = len + offset,
                b = other.bytes,
                bFromIndex = other.offset,
                bToIndex = other.offset + other.len,
            ) == -1

    override fun hashCode(): Int = bytes.bytes.hash(offset = offset, length = len)

    companion object {
        val empty: BytesSlice = BytesSlice(bytes = Bytes.empty, offset = 0, len = 0)
    }
}
