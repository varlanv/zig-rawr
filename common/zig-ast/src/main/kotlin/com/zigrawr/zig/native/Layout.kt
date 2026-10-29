package com.zigrawr.zig.native

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout

object Layout {

    object Data {
        val zrParseResult: StructLayout = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("data"),
            ValueLayout.JAVA_LONG.withName("size"),
            ValueLayout.JAVA_INT.withName("status"),
            MemoryLayout.paddingLayout(4),
        )
        val zrSnapshotHeader: StructLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("magic"),
            ValueLayout.JAVA_INT.withName("abiVersion"),
            ValueLayout.JAVA_INT.withName("mode"),
            ValueLayout.JAVA_INT.withName("sourceLen"),
            ValueLayout.JAVA_INT.withName("tokenCount"),
            ValueLayout.JAVA_INT.withName("nodeCount"),
            ValueLayout.JAVA_INT.withName("extraDataCount"),
            ValueLayout.JAVA_INT.withName("errorCount"),
            ValueLayout.JAVA_INT.withName("tokenTagsOffset"),
            ValueLayout.JAVA_INT.withName("tokenStartsOffset"),
            ValueLayout.JAVA_INT.withName("nodeTagsOffset"),
            ValueLayout.JAVA_INT.withName("nodeMainTokensOffset"),
            ValueLayout.JAVA_INT.withName("nodePayloadsOffset"),
            ValueLayout.JAVA_INT.withName("extraDataOffset"),
            ValueLayout.JAVA_INT.withName("errorsOffset"),
            ValueLayout.JAVA_INT.withName("totalSize"),
        )
        val zrNodePayload: StructLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("first"),
            ValueLayout.JAVA_INT.withName("second"),
        )
        val zrErrorView: StructLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("tag"),
            ValueLayout.JAVA_INT.withName("token"),
            ValueLayout.JAVA_INT.withName("flags"),
            ValueLayout.JAVA_INT.withName("extraKind"),
            ValueLayout.JAVA_LONG.withName("extraValue"),
        )
    }

    object Functions {

        val zrStartSession: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.ADDRESS)
        val zrCloseSession: FunctionDescriptor = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        val zrParse: FunctionDescriptor = FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
        )
        val zrFree: FunctionDescriptor = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        val zrAllocInput: FunctionDescriptor =
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
    }
}
