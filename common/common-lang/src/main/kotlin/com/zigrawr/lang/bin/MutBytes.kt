@file:Suppress("NOTHING_TO_INLINE")

package com.zigrawr.lang.bin

import com.zigrawr.lang.intViewHandle
import com.zigrawr.lang.longViewHandle
import com.zigrawr.lang.shortViewHandle

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
private typealias BytesImpl = ByteArray

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
@JvmInline
value class MutBytes @PublishedApi internal constructor(@PublishedApi internal val impl: BytesImpl) {
    inline val size: Int get() = impl.size

    constructor(dataSize: DataSize) : this(ByteArray(dataSize.bytes))

    inline fun getPackedLong(idx: Int): Long = longViewHandle.get(impl, idx) as Long

    fun setPackedLong(idx: Int, value: Long) {
        longViewHandle.set(impl, idx, value)
    }

    inline fun getPackedInt(idx: Int): Int = intViewHandle.get(impl, idx) as Int

    inline fun setPackedInt(idx: Int, value: Int) {
        intViewHandle.set(impl, idx, value)
    }

    inline fun getPackedShort(idx: Int): Short = shortViewHandle.get(impl, idx) as Short

    inline fun setPackedShort(idx: Int, value: Short) {
        shortViewHandle.set(impl, idx, value)
    }

    inline operator fun get(idx: Int): Byte = impl[idx]

    inline operator fun set(idx: Int, value: Byte) {
        impl[idx] = value
    }

    inline fun forEach(block: (Byte) -> Unit) = impl.forEach(block)

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) = impl.forEachIndexed(block)

    inline fun fill(value: Byte) = impl.fill(value)

    fun hash(offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > impl.size - length) {
            throw IndexOutOfBoundsException()
        }
        val end = offset + length
        return hashSwar(bytes = this.asReadonly(), start = offset, end = end, initial = 1)
    }

    inline fun copyInto(
        destination: MutBytes,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = size,
    ) {
        require(startIndex <= endIndex)
        System.arraycopy(impl, startIndex, destination.impl, destinationOffset, endIndex - startIndex)
    }

    inline fun copyOf(newCapacity: Int = impl.size): MutBytes = MutBytes(impl.copyOf(newCapacity))

    inline fun copyOfRange(from: Int, to: Int): MutBytes = MutBytes(impl.copyOfRange(from, to))

    companion object {
        val empty: MutBytes = MutBytes(ByteArray(0))

        inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes {
            val s = dataSize.bytes
            val arr = ByteArray(s)
            for (idx in 0 until s) {
                arr[idx] = init(idx)
            }
            return MutBytes(arr)
        }

        inline operator fun invoke(array: ByteArray): MutBytes = MutBytes(array)
    }
}

inline fun MutBytes.asReadonly(): Bytes = Bytes(this)

inline fun MutBytes.asList(): List<Byte> = this.asReadonly().asList()

inline fun MutBytes.byteArrayClone(): ByteArray {
    val res = ByteArray(impl.size)
    copyInto(destination = res.asMut())
    return res
}

inline fun MutBytes.forEachIndex(block: (idx: Int) -> Unit) {
    val sz = size
    for (idx in 0 until sz) {
        block(idx)
    }
}

//inline fun MutBytes.decodeToString(
//    startIndex: Int = 0,
//    endIndex: Int = this.size,
//): String = Charset.Utf8.decodeFromBytes(bytes = this.asReadonly(), offset = startIndex, len = endIndex - startIndex)
