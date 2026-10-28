@file:Suppress("NOTHING_TO_INLINE")

package com.zigrawr.lang.bin

import com.zigrawr.lang.math.addExact
import com.zigrawr.lang.math.multiplyExact
import kotlin.math.abs

@JvmInline
value class DataSize @PublishedApi internal constructor(val bytes: Int) {
    init {
        require(bytes >= 0) { "DataSize should be positive or zero - [ $bytes ]" }
    }

    inline operator fun plus(bytes: Int): DataSize = DataSize(this.bytes.addExact(bytes))

    inline operator fun plus(other: DataSize): DataSize = DataSize(this.bytes.addExact(other.bytes))

    override fun toString(): String {
        val magnitude = abs(bytes.toLong())

        if (magnitude < KIB) {
            return "${bytes}B"
        }

        val readable = when {
            magnitude < MIB -> "${format(magnitude = magnitude, unit = KIB)}KiB"
            magnitude < GIB -> "${format(magnitude = magnitude, unit = MIB)}MiB"
            else -> "${format(magnitude = magnitude, unit = GIB)}GiB"
        }

        return "$readable ($bytes B)"
    }

    inline fun allocate(): MutBytes = MutBytes(this)

    private fun format(magnitude: Long, unit: Int): String {
        // Round to thousandths, with ties away from zero.
        // Safe in Long for every possible Int byte count.
        val scaled = (magnitude * 1000L + unit / 2) / unit
        val fraction = (scaled % 1000L).toInt()

        return buildString {
            if (bytes < 0) {
                append('-')
            }
            append(scaled / 1000L)

            if (fraction != 0) {
                append('.')
                append('0' + fraction / 100)

                if (fraction % 100 != 0) {
                    append('0' + (fraction / 10) % 10)

                    if (fraction % 10 != 0) {
                        append('0' + fraction % 10)
                    }
                }
            }
        }
    }
}

@PublishedApi
internal const val KIB = 1024

@PublishedApi
internal const val MIB = KIB * 1024

@PublishedApi
internal const val GIB = MIB * 1024

inline fun Int.bytes() = DataSize(this)

inline fun Int.kilobytes() = DataSize(this.multiplyExact(KIB))

inline fun Int.megabytes() = DataSize(this.multiplyExact(MIB))

inline fun Int.gigabytes() = DataSize(this.multiplyExact(GIB))
