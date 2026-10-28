package com.zigrawr.lang

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.nio.ByteOrder

private val byteOrder = ByteOrder.LITTLE_ENDIAN

@JvmField
val longViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, byteOrder)

@JvmField
val intViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(IntArray::class.java, byteOrder)

@JvmField
val shortViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, byteOrder)
