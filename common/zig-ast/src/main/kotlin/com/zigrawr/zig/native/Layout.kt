package com.zigrawr.zig.native

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

object Layout {

    object Data {
        val zrSnapshotHeader = MemoryLayout.structLayout(
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
        val zrNodePayload = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("first"),
            ValueLayout.JAVA_INT.withName("second"),
        )
        val zrErrorView = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("tag"),
            ValueLayout.JAVA_INT.withName("token"),
            ValueLayout.JAVA_INT.withName("flags"),
            ValueLayout.JAVA_INT.withName("extraKind"),
            ValueLayout.JAVA_LONG.withName("extraValue"),
        )
    }

    object Functions {

        val zrInit = FunctionDescriptor.of(ValueLayout.ADDRESS)
        val zrClose = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        val zrParse = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
        )
    }
}
