package com.zigrawr.lang.math

fun Int.multiplyExact(other: Int): Int = Math.multiplyExact(this, other)

fun Long.multiplyExact(other: Long): Long = Math.multiplyExact(this, other)

fun Int.addExact(other: Int): Int = Math.addExact(this, other)

fun Long.addExact(other: Long): Long = Math.addExact(this, other)

fun Long.subtractExact(other: Long): Long = Math.subtractExact(this, other)

fun Long.multiplyHigh(other: Long): Long = Math.multiplyHigh(this, other)

fun Long.unsignedMultiplyHigh(other: Long): Long = Math.unsignedMultiplyHigh(this, other)

fun Long.compareUnsigned(other: Long): Int = toULong().compareTo(other.toULong())

fun Long.remainderUnsigned(other: Long): Long = (toULong() % other.toULong()).toLong()

fun Int.numberOfLeadingZeros(): Int = countLeadingZeroBits()
