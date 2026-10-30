package com.zigrawr.lang.primitives

val stableEmptyIntArray: IntArray = intArrayOf()
val stableEmptyLongArray: LongArray = longArrayOf()

class IntList(initialCapacity: Int = 0) {

    private var data = IntArray(initialCapacity)

    var size: Int = 0
        private set

    fun add(value: Int) {
        ensureCapacity(1)
        data[size] = value
        size++
    }

    fun addAssumeCapacity(value: Int) {
        data[size] = value
        size++
    }

    fun addAll(values: IntArray) {
        ensureCapacity(values.size)
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

    fun ensureCapacity(requested: Int) {
        val capacity = data.size
        if (size + requested <= capacity) return
        data = if (capacity == 0) {
            IntArray(10)
        } else {
            data.copyOf(maxOf(size + requested, capacity + maxOf(1, (capacity + 1) / 2)))
        }
    }

}

class LongList(initialCapacity: Int = 0) {

    private var data = LongArray(initialCapacity)

    var size: Int = 0
        private set

    fun add(value: Long) {
        ensureCapacity(size + 1)
        data[size] = value
        size++
    }

    fun addPacked(value1: Int, value2: Int) {
        add(PackedLong2.pack(value1, value2).packed)
    }

    fun addAssumeCapacity(value: Long) {
        data[size] = value
        size++
    }

    fun addAll(values: LongArray) {
        ensureCapacity(size + values.size)
        values.copyInto(data, destinationOffset = size)
        size += values.size
    }

    operator fun get(index: Int): Long {
        if (index !in 0 until size) throw IndexOutOfBoundsException(index)
        return data[index]
    }

    operator fun set(index: Int, value: Long) {
        if (index !in 0 until size) throw IndexOutOfBoundsException(index)
        data[index] = value
    }

    fun toLongArray(): LongArray = data.copyOf(size)

    fun backingArray(): LongArray {
        return data
    }

    fun capacity(): Int {
        return data.size
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= data.size) return
        val capacity = data.size
        data = if (capacity == 0) {
            LongArray(10)
        } else {
            data.copyOf(
                maxOf(
                    minCapacity,
                    capacity + maxOf(1, (capacity + 1) / 2)
                ),
            )
        }
    }

}
