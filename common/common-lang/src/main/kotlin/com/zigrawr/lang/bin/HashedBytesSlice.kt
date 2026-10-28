package com.zigrawr.lang.bin

/**
 * Same as [BytesSlice], but with implemented equals and hashcode.
 * Hashcode is implemented eagerly on instantiation.
 */
class HashedBytesSlice(val slice: BytesSlice) {

    val hash = BytesHash.target.hash(slice.bytes, slice.offset, slice.offset + slice.len)

    override fun equals(other: Any?): Boolean =
        other is HashedBytesSlice && other.slice == slice

    override fun hashCode(): Int = hash
}

fun String.toHashedSlice(): HashedBytesSlice {
    val bytes = this.encodeToByteArray()
    return HashedBytesSlice(BytesSlice(bytes.asReadonly(), 0, bytes.size))
}
