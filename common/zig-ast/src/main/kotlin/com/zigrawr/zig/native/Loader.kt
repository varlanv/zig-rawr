package com.zigrawr.zig.native

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.nio.file.Path


object Loader {

    fun load(binaryPath: String = System.getProperty("zigAstBinaryPath")): Handles {
        return loadFromPath(Path.of(binaryPath))
    }

    fun loadFromPath(binaryPath: Path): Handles {
        val lib = SymbolLookup.libraryLookup(binaryPath, Arena.global())
        val symbol = lib.findOrThrow("zrParse")
        val linker = Linker.nativeLinker()
        val zrStartSession = linker.downcallHandle(lib.findOrThrow("zrStartSession"), Layout.Functions.zrStartSession)
        val zrCloseSession = linker.downcallHandle(lib.findOrThrow("zrCloseSession"), Layout.Functions.zrCloseSession)
        val zrParse = linker.downcallHandle(symbol, Layout.Functions.zrParse)
        val zrFree = linker.downcallHandle(lib.findOrThrow("zrFree"), Layout.Functions.zrFree)
        val zrAllocInput = linker.downcallHandle(lib.findOrThrow("zrAllocInput"), Layout.Functions.zrAllocInput)
        return Handles(zrStartSession, zrCloseSession, zrParse, zrFree, zrAllocInput)
    }
}
