package com.zigrawr.lang.bin

import kotlin.jvm.JvmInline

@Suppress("NOTHING_TO_INLINE")
@JvmInline
value class MutBytesSlice @PublishedApi internal constructor(@PublishedApi internal val delegate: BytesSlice) {
    inline fun setPackedLong(idx: Int, value: Long) {
        require(idx >= 0 && idx <= delegate.len - Long.SIZE_BYTES)
        delegate.bytes.bytes.setPackedLong(idx = delegate.offset + idx, value = value)
    }

    inline fun setPackedInt(idx: Int, value: Int) {
        require(idx >= 0 && idx <= delegate.len - Int.SIZE_BYTES)
        delegate.bytes.bytes.setPackedInt(idx = delegate.offset + idx, value = value)
    }

    inline fun setPackedShort(idx: Int, value: Short) {
        require(idx >= 0 && idx <= delegate.len - Short.SIZE_BYTES)
        delegate.bytes.bytes.setPackedShort(idx = delegate.offset + idx, value = value)
    }

    inline operator fun set(idx: Int, value: Byte) {
        require(idx >= 0 && idx < delegate.len)
        delegate.bytes.bytes.impl[delegate.offset + idx] = value
    }

    inline operator fun get(idx: Int): Byte {
        require(idx >= 0 && idx < delegate.len)
        return delegate.bytes.bytes.impl[delegate.offset + idx]
    }

    inline fun copyFrom(
        source: Bytes,
        sourceOffset: Int = 0,
        sourceLength: Int = source.size - sourceOffset,
    ) {
        require(
            sourceOffset >= 0 &&
                sourceLength >= 0 &&
                sourceOffset <= source.size - sourceLength &&
                sourceLength <= delegate.len,
        )
        source.bytes.copyInto(
            destination = delegate.bytes.bytes,
            destinationOffset = delegate.offset,
            startIndex = sourceOffset,
            endIndex = sourceOffset + sourceLength,
        )
    }
}
