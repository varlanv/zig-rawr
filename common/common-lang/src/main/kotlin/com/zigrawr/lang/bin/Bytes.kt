@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")

package com.zigrawr.lang.bin

import java.util.*

fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int {
    return Arrays.mismatch(this.bytes.impl, aFromIndex, aToIndex, b.bytes.impl, bFromIndex, bToIndex)
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@JvmInline
value class Bytes(@PublishedApi internal val bytes: MutBytes) {
    inline val size: Int get() = bytes.size

    inline operator fun get(idx: Int): Byte = bytes.impl[idx]

    inline fun copyInto(
        destination: MutBytes,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = size,
    ) = bytes.copyInto(
        destination = destination,
        destinationOffset = destinationOffset,
        startIndex = startIndex,
        endIndex = endIndex,
    )

    inline fun asList(): List<Byte> = bytes.impl.asList()

    inline fun forEachIndex(block: (idx: Int) -> Unit) {
        val sz = bytes.impl.size
        for (idx in 0 until sz) {
            block(idx)
        }
    }

    inline fun forEach(block: (Byte) -> Unit) {
        val impl = bytes.impl
        val sz = impl.size
        for (idx in 0 until sz) {
            block(impl[idx])
        }
    }

    inline fun forEachIndexed(block: (index: Int, Byte) -> Unit) {
        val impl = bytes.impl
        val sz = impl.size
        for (idx in 0 until sz) {
            block(idx, impl[idx])
        }
    }

    class Unsafe internal constructor() {
        inline fun <R> useInternal(bytes: Bytes, block: (ByteArray) -> R): R {
            return block(bytes.bytes.impl)
        }

        inline fun <R> useInternal(bytes: MutBytes, block: (ByteArray) -> R): R {
            return block(bytes.impl)
        }
    }

    companion object {
        @PublishedApi
        internal val unsafe: Unsafe = Unsafe()

        val empty: Bytes = Bytes(MutBytes.empty)

        /**
         * Provides unsafe [Bytes] operations.
         * The caller is responsible for ensuring invariants are preserved and guaranteeing thread safety.
         * Turning [Bytes] into invalid state via usage of [unsafe] results in undefined behavior.
         */
        inline fun <R> unsafe(block: Unsafe.() -> R): R {
            return block(unsafe)
        }
    }
}

inline fun Bytes.isEmpty(): Boolean = size == 0

inline fun Bytes.copyOf(newCapacity: Int = size): Bytes = this.bytes.copyOf(newCapacity).asReadonly()

inline fun Bytes.toSlice(offset: Int, len: Int): BytesSlice = BytesSlice(bytes = this, offset = offset, len = len)

inline fun Bytes.getPackedLong(idx: Int): Long = bytes.getPackedLong(idx)

inline fun Bytes.getPackedInt(idx: Int): Int = bytes.getPackedInt(idx)

inline fun Bytes.getPackedShort(idx: Int): Short = bytes.getPackedShort(idx)

inline fun ByteArray.asReadonly(): Bytes = Bytes(MutBytes.Companion(this))

inline fun ByteArray.asMut(): MutBytes = MutBytes.Companion(this)

inline fun Bytes.equals(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Boolean = mismatch(
    aFromIndex = aFromIndex,
    aToIndex = aToIndex,
    b = b,
    bFromIndex = bFromIndex,
    bToIndex = bToIndex,
) < 0

inline fun Bytes.containsNeedle(
    needle: Bytes,
    fromIndex: Int = 0,
): Boolean = indexOfNeedle(needle = needle, fromIndex = fromIndex) != -1

fun Bytes.indexOfNeedle(needle: Bytes, fromIndex: Int): Int {
    if (needle.isEmpty()) {
        return fromIndex.coerceIn(0, size)
    }
    val needleSize = needle.size
    val needleFirst = needle[0]
    val lastPossible = size - needleSize
    var i = fromIndex.coerceAtLeast(0)
    while (i <= lastPossible) {
        if (this[i] == needleFirst && (
                needleSize == 1 ||
                    mismatch(
                        aFromIndex = i + 1,
                        aToIndex = i + needleSize,
                        b = needle,
                        bFromIndex = 1,
                        bToIndex = needleSize,
                    ) == -1
                )
        ) {
            return i
        }
        i++
    }
    return -1
}

inline fun Bytes.startsWith(
    prefix: Bytes,
    offset: Int = 0,
): Boolean = offset >= 0 &&
    prefix.size <= size - offset &&
    mismatch(
        aFromIndex = offset,
        aToIndex = offset + prefix.size,
        b = prefix,
        bFromIndex = 0,
        bToIndex = prefix.size,
    ) == -1
