package com.zigrawr.lang.bin


class StringSource(val string: String) : ByteSource {
    private var position = 0
    private var pending = 0
    private var pendingSize = 0

    override val sizeHint: ArraySizeHint = string.length

    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        TODO()
    }
}
