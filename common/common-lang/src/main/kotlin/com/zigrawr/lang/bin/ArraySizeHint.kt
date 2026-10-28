@file:Suppress("NOTHING_TO_INLINE")

package com.zigrawr.lang.bin

typealias ArraySizeHint = Int

fun ArraySizeHint.orElse(fallback: Int): Int {
    return if (this < 0) fallback else this
}
