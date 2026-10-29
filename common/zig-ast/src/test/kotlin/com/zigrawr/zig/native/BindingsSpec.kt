package com.zigrawr.zig.native

import com.zigrawr.lang.bin.asList
import com.zigrawr.lang.bin.asReadonly
import com.zigrawr.lang.log.Log
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.tokenizer.BaseSpec
import com.zigrawr.zig.tokenizer.useTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BindingsSpec : BaseSpec({
    val handles = context(Log.stderr) { BundledBindingsLoader.load() }

    fun useBindings(block: (bindings: Bindings) -> Unit) {
        val bindings = Bindings(handles)
        try {
            block(bindings)
        } finally {
            bindings.close()
        }
    }

    should("encode source text as UTF-8") {
        useBindings { bindings ->
            val source = "const x = \"π\";"
            val ast = NativeAst(bindings.parseChars(source, ParseMode.Zig))
            ast.sourceLength() shouldBe source.length
        }
    }

    should("parse source bytes") {
        useBindings { bindings ->
            val source = "const x = \"π\";"
            val ast = NativeAst(bindings.parseBytes(source.toByteArray().asReadonly(), ParseMode.Zig))
            ast.sourceLength() shouldBe source.length
        }
    }

    should("parse a source file") {
        useTempDir { dir ->
            val path = Files.createFile(dir.resolve("zigrawr.zig"))
            val source = "const x = \"π\";"
            Files.writeString(path, source)
            useBindings { bindings ->
                val ast = NativeAst(bindings.parseAtPath(path))
                ast.sourceLength() shouldBe source.length
            }
        }
    }

    should("read the native AST snapshot") {
        useBindings { bindings ->
            val ast = NativeAst(bindings.parseChars("const x = \"π\";", ParseMode.Zig))
            ast.mode() shouldBe NativeAst.Mode.Zig
            ast.sourceLength() shouldBe 14
            ast.tokenStart(0) shouldBe 0
            ast.nodeTag(0) shouldBe NativeAst.NodeTag.ROOT
            ast.nodeMainToken(0) shouldBe 0
            ast.tokenTag(0) shouldBe NativeAst.TokenTag.KEYWORD_CONST
            ast.extraData(0) shouldBe 1
        }
    }

    should("read native parse errors") {
        useBindings { bindings ->
            val ast = NativeAst(bindings.parseChars("const = ;", ParseMode.Zig))
            (ast.errorCount() > 0) shouldBe true
            ast.errorTag(0) shouldBe NativeAst.ErrorTag.EXPECTED_TOKEN
            (ast.errorToken(0) in 0 until ast.tokenCount()) shouldBe true
            ast.errorExtraKind(0) shouldBe NativeAst.ErrorExtraKind.ExpectedToken
            ast.errorIsNote(0) shouldBe (ast.errorFlags(0) and 1 != 0)
            ast.errorTokenIsPrevious(0) shouldBe (ast.errorFlags(0) and 2 != 0)
            (ast.errorExtraValue(0) >= 0) shouldBe true
        }
    }

    should("expose UTF-16 token starts and source length including trailing comments") {
        useBindings { bindings ->
            val source = "const x = \"π€😀\"; \n// π€😀\n"
            val ast = NativeAst(bindings.parseChars(source, ParseMode.Zig))
            val expectedStarts = listOf(0, 6, 8, 10, source.indexOf(';'), source.length)

            ast.sourceLength() shouldBe source.length
            ast.tokenCount() shouldBe expectedStarts.size
            List(ast.tokenCount(), ast::tokenStart) shouldBe expectedStarts
            ast.tokenTag(ast.tokenCount() - 1) shouldBe NativeAst.TokenTag.EOF

            val iteratedStarts = mutableListOf<Int>()
            ast.forEachToken { _, _, startOffset -> iteratedStarts.add(startOffset) }
            iteratedStarts shouldBe expectedStarts
        }
    }

    should("expose UTF-16 offsets for ZON snapshots") {
        useBindings { bindings ->
            val source = ".{ .text = \"π€😀\" }"
            val ast = NativeAst(bindings.parseChars(source, ParseMode.Zon))

            ast.mode() shouldBe NativeAst.Mode.Zon
            ast.sourceLength() shouldBe source.length
            List(ast.tokenCount(), ast::tokenStart) shouldBe listOf(0, 1, 3, 4, 9, 11, source.indexOf('}'), source.length)
        }
    }

    should("reject snapshots with an unsupported ABI version") {
        useBindings { bindings ->
            val snapshot = bindings.parseChars("const x = 1;", ParseMode.Zig)
            val abiVersionOffset = Layout.Data.zrSnapshotHeader.byteOffset(groupElement("abiVersion")).toInt()
            snapshot.setPackedInt(abiVersionOffset, 2)

            shouldThrow<IllegalArgumentException> { NativeAst(snapshot) }
        }
    }

    should("iterate native tokens and nodes") {
        useBindings { bindings ->
            val ast = NativeAst(bindings.parseChars("const x = \"π\";", ParseMode.Zig))
            var tokensSeen = 0
            ast.forEachToken { index, tag, start ->
                index shouldBe tokensSeen
                tag shouldBe ast.tokenTag(index)
                start shouldBe ast.tokenStart(index)
                tokensSeen++
            }
            tokensSeen shouldBe ast.tokenCount()

            var nodesSeen = 0
            ast.forEachNode { index, tag, mainToken, payloadWord0, payloadWord1 ->
                index shouldBe nodesSeen
                tag shouldBe ast.nodeTag(index)
                mainToken shouldBe ast.nodeMainToken(index)
                if (index == 0) {
                    payloadWord0 shouldBe 0
                    payloadWord1 shouldBe 1
                }
                nodesSeen++
            }
            nodesSeen shouldBe ast.nodeCount()
        }
    }

    should("parse once per call even when the snapshot grows") {
        val counter = ParseCallCounter()
        val countedBindings = Bindings(counter.wrap(handles))
        try {
            val small = "const x = 1;"
            val large = buildString {
                repeat(1000) { append("const x$it = $it;\n") }
            }

            countedBindings.parseChars(small, ParseMode.Zig)
            val afterFirst = counter.calls
            afterFirst shouldBe 1
            countedBindings.parseChars(small, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 1

            val grown = countedBindings.parseChars(large, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 2
            NativeAst(grown).sourceLength() shouldBe large.length

            countedBindings.parseChars(large, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 3

            val shrunk = countedBindings.parseChars(small, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 4
            NativeAst(shrunk).sourceLength() shouldBe small.length
        } finally {
            countedBindings.close()
        }
    }

    should("keep returned snapshots valid after later parses and close") {
        val bindings = Bindings(handles)
        val snapshot = try {
            val first = bindings.parseChars("const x = \"π\";", ParseMode.Zig)
            val original = first.asList().toList()
            bindings.parseChars(".{ .answer = 42 }", ParseMode.Zon)
            bindings.parseChars("const = ;".repeat(1000), ParseMode.Zig)
            first.asList() shouldBe original
            first
        } finally {
            bindings.close()
        }
        NativeAst(snapshot).sourceLength() shouldBe 14
        NativeAst(snapshot).errorCount() shouldBe 0
    }

    should("parse concurrently through the same bindings instance") {
        val sources = List(4) { index ->
            buildString {
                repeat((index + 1) * 50) { append("const x$it = \"π$index\";\n") }
            }
        }
        val reference = Bindings(handles)
        val expected = try {
            sources.map { reference.parseChars(it, ParseMode.Zig).asList().toList() }
        } finally {
            reference.close()
        }
        useBindings { bindings ->
            val start = CyclicBarrier(sources.size)
            Executors.newFixedThreadPool(sources.size).use { executor ->
                val futures = sources.mapIndexed { index, source ->
                    executor.submit {
                        start.await(10, TimeUnit.SECONDS)
                        val bytes = source.toByteArray().asReadonly()
                        repeat(30) { iteration ->
                            val snapshot = if (iteration % 2 == 0) {
                                bindings.parseChars(source, ParseMode.Zig)
                            } else {
                                bindings.parseBytes(bytes, ParseMode.Zig)
                            }
                            snapshot.asList() shouldBe expected[index]
                        }
                    }
                }
                futures.forEach { it.get(30, TimeUnit.SECONDS) }
            }
        }
    }
})

private class ParseCallCounter {
    var calls = 0
        private set

    fun record(result: MemorySegment): MemorySegment {
        calls++
        return result
    }

    fun wrap(handles: Handles): Handles {
        val record = MethodHandles.lookup().findVirtual(
            ParseCallCounter::class.java,
            "record",
            MethodType.methodType(MemorySegment::class.java, MemorySegment::class.java),
        ).bindTo(this)
        return Handles(
            handles.zrStartSession, handles.zrCloseSession,
            MethodHandles.filterReturnValue(handles.zrParse, record), handles.zrFree, handles.zrAllocInput,
        )
    }
}
