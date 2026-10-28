package com.zigrawr.lang.primitives

val stableEmptyIntArray: IntArray = intArrayOf()

class IntArrayList(initialCapacity: Int = 0) {

    private var data = IntArray(initialCapacity)

    var size: Int = 0
        private set

    fun add(value: Int) {
        ensureCapacity(size + 1)
        data[size] = value
        size++
    }

    fun addAll(values: IntArray) {
        ensureCapacity(size + values.size)
        values.copyInto(data, destinationOffset = size)
        size += values.size
    }

    operator fun get(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException(index)
        return data[index]
    }

    operator fun set(index: Int, value: Int) {
        if (index !in 0 until size) throw IndexOutOfBoundsException(index)
        data[index] = value
    }

    fun toIntArray(): IntArray = data.copyOf(size)

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= data.size) return
        val capacity = data.size
        data = data.copyOf(maxOf(minCapacity, capacity + maxOf(1, (capacity + 1) / 2)))
    }

}
