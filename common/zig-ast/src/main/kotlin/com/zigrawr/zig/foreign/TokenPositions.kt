package com.zigrawr.zig.foreign

import com.zigrawr.lang.primitives.PackedLong2

/** Inclusive start and exclusive end offsets in UTF-16 code units. */
@JvmInline
value class TokenPositions(val packed: PackedLong2) {

    inline val start: Int get() = packed.v1
    inline val end: Int get() = packed.v2
}
