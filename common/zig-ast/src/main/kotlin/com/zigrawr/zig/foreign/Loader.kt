package com.zigrawr.zig.foreign

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
        val linker = Linker.nativeLinker()
        val zrStartSession = linker.downcallHandle(lib.findOrThrow("zrStartSession"), Layout.Functions.zrStartSession)
        val zrCloseSession = linker.downcallHandle(lib.findOrThrow("zrCloseSession"), Layout.Functions.zrCloseSession)
        val zrParseAst = linker.downcallHandle(lib.findOrThrow("zrParseAst"), Layout.Functions.zrParseAst)
        val zrParseTokens = linker.downcallHandle(lib.findOrThrow("zrParseTokens"), Layout.Functions.zrParseTokens)
        val zrFree = linker.downcallHandle(lib.findOrThrow("zrFree"), Layout.Functions.zrFree)
        val zrAllocInput = linker.downcallHandle(lib.findOrThrow("zrAllocInput"), Layout.Functions.zrAllocInput)
        return Handles(
            zrStartSession = zrStartSession,
            zrCloseSession = zrCloseSession,
            zrParseAst = zrParseAst,
            zrParseTokens = zrParseTokens,
            zrFree = zrFree,
            zrAllocInput = zrAllocInput
        )
    }
}
