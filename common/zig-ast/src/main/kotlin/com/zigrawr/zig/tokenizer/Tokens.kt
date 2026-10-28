package com.zigrawr.zig.tokenizer

class Tokens(estimatedSize: Int) {

    private var data = LongArray(estimatedSize)

    var size: Int = 0
        private set

    fun append(token: Token) {
        val pos = size
        if (pos == data.size) {
            val capacity = data.size
            data = data.copyOf(capacity + maxOf(1, (capacity + 1) / 2))
        }
        data[pos] = token.packed
        size = pos + 1
    }

    fun get(index: Int): Token {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        }
        return Token(data[index])
    }
}
