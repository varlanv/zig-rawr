package com.zigrawr.zig.native

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.nio.file.Path


object Loader {

    fun load(binaryPath: String = System.getProperty("zigAstBinaryPath")): Handles {
        val lib = SymbolLookup.libraryLookup(Path.of(binaryPath), Arena.global())
        val symbol = lib.findOrThrow("zrParse")
        val linker = Linker.nativeLinker()
        val zrInit = linker.downcallHandle(lib.findOrThrow("zrInit"), Layout.Functions.zrInit)
        val zrClose = linker.downcallHandle(lib.findOrThrow("zrClose"), Layout.Functions.zrClose)
        val zrParse = linker.downcallHandle(symbol, Layout.Functions.zrParse)
        val zrFree = linker.downcallHandle(lib.findOrThrow("zrFree"), Layout.Functions.zrFree)
        val zrAllocInput = linker.downcallHandle(lib.findOrThrow("zrAllocInput"), Layout.Functions.zrAllocInput)
        return Handles(zrInit, zrClose, zrParse, zrFree, zrAllocInput)
    }
}
