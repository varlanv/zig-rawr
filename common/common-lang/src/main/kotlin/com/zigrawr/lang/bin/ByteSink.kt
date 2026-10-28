package com.zigrawr.lang.bin

/** Writes bytes from a caller-provided array. */
interface ByteSink {
    /** Writes [length] bytes from [source] starting at [offset]. */
    fun writeTo(
        source: Bytes,
        offset: Int,
        length: Int,
    )
}

/** Accumulates bytes in a reusable, growing array. */
class ReusableByteArraySink(initialCapacity: DataSize) : ByteSink {
    @PublishedApi
    internal var bytes: MutBytes = MutBytes(initialCapacity)

    @PublishedApi
    internal var position: Int = 0

    /** Clears the written length while retaining the allocated array. */
    fun reset() {
        position = 0
    }

    /** Peek into readonly view of backing array without copying. */
    inline fun <R> useBytes(block: (bytes: Bytes, len: Int) -> R): R {
        return block(bytes.asReadonly(), position)
    }

    override fun writeTo(
        source: Bytes,
        offset: Int,
        length: Int,
    ) {
        if (offset < 0 || length < 0 || offset > source.size - length) {
            throw IndexOutOfBoundsException()
        }
        ensureCapacity(length)
        val pos = position
        source.copyInto(destination = bytes, destinationOffset = pos, startIndex = offset, endIndex = offset + length)
        position = pos + length
    }

    internal fun ensureCapacity(additionalBytes: Int) {
        val pos = position
        val bts = bytes
        if (additionalBytes <= bts.size - pos) {
            return
        }
        if (additionalBytes > Int.MAX_VALUE - pos) {
            error("Required buffer capacity exceeds Int.MAX_VALUE")
        }
        val requiredCapacity = pos + additionalBytes
        val doubledCapacity = if (bts.size <= Int.MAX_VALUE / 2) {
            bts.size * 2
        } else {
            Int.MAX_VALUE
        }
        val newCapacity = maxOf(requiredCapacity, doubledCapacity)
        bytes = bts.copyOf(newCapacity)
    }
}
