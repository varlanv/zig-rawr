package com.zigrawr.lang.bin

/** Reads bytes into a caller-provided array. */
interface ByteSource {
    /**
     * Hint as to how many bytes are available in this stream, if known.
     * The returned value is not meant to represent concrete final number that will be read from source.
     * The returned value may be less, but never more that final number that will be read from source.
     */
    val sizeHint: ArraySizeHint

    /** Reads up to [length] bytes into [sink] at [offset]; returns -1 at end, or 0 for zero length. */
    fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int
}

/** Reads the contents of [slice] in order. */
class ByteArraySource(private val slice: BytesSlice) : ByteSource {
    private var position: Int = 0
    override val sizeHint: ArraySizeHint = slice.len

    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        if (offset < 0 || length < 0 || offset > sink.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) {
            return 0
        }
        val remaining = slice.len - position
        if (remaining == 0) {
            return -1
        }
        val count = minOf(length, remaining)
        val start = slice.offset + position
        slice.bytes.bytes.copyInto(
            destination = sink,
            destinationOffset = offset,
            startIndex = start,
            endIndex = start + count,
        )
        position += count
        return count
    }
}
