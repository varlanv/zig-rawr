package com.zigrawr.zig.foreign

import com.zigrawr.lang.bin.asList
import com.zigrawr.lang.bin.asReadonly
import com.zigrawr.lang.log.Log
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.ErrorExtraKind
import com.zigrawr.zig.foreign.model.ErrorTag
import com.zigrawr.zig.foreign.model.Mode
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag
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
            val ast = com.zigrawr.zig.foreign.model.Ast(bindings.parseChars(source, ParseMode.Zig))
            ast.sourceLength() shouldBe source.length
        }
    }

    should("parse source bytes") {
        useBindings { bindings ->
            val source = "const x = \"π\";"
            val ast = com.zigrawr.zig.foreign.model.Ast(
                bindings.parseBytes(
                    source.toByteArray().asReadonly(), ParseMode.Zig
                )
            )
            ast.sourceLength() shouldBe source.length
        }
    }

    should("parse a source file") {
        useTempDir { dir ->
            val path = Files.createFile(dir.resolve("zigrawr.zig"))
            val source = "const x = \"π\";"
            Files.writeString(path, source)
            useBindings { bindings ->
                val ast = com.zigrawr.zig.foreign.model.Ast(bindings.parseAtPath(path))
                ast.sourceLength() shouldBe source.length
            }
        }
    }

    should("read the native AST snapshot") {
        useBindings { bindings ->
            val ast = com.zigrawr.zig.foreign.model.Ast(
                bindings.parseChars(
                    "const x = \"π\";",
                    ParseMode.Zig
                )
            )
            ast.mode() shouldBe Mode.Zig
            ast.sourceLength() shouldBe 14
            ast.tokenPositions(0).start shouldBe 0
            ast.tokenPositions(0).end shouldBe 5
            ast.nodeTag(0) shouldBe NodeTag.ROOT
            ast.nodeMainToken(0) shouldBe 0
            ast.tokenTag(0) shouldBe TokenTag.KEYWORD_CONST
            ast.extraData(0) shouldBe 1
        }
    }

    should("read native parse errors") {
        useBindings { bindings ->
            val ast =
                com.zigrawr.zig.foreign.model.Ast(bindings.parseChars("const = ;", ParseMode.Zig))
            (ast.errorCount() > 0) shouldBe true
            ast.errorTag(0) shouldBe ErrorTag.EXPECTED_TOKEN
            (ast.errorToken(0) in 0 until ast.tokenCount()) shouldBe true
            ast.errorExtraKind(0) shouldBe ErrorExtraKind.ExpectedToken
            ast.errorIsNote(0) shouldBe (ast.errorFlags(0) and 1 != 0)
            ast.errorTokenIsPrevious(0) shouldBe (ast.errorFlags(0) and 2 != 0)
            (ast.errorExtraValue(0) >= 0) shouldBe true
        }
    }

    should("expose UTF-16 token ranges and source length including trailing comments") {
        useBindings { bindings ->
            val source = "const x = \"π€😀\"; \n// π€😀\n"
            val ast = com.zigrawr.zig.foreign.model.Ast(bindings.parseChars(source, ParseMode.Zig))
            val expectedRanges = listOf(0 to 5, 6 to 7, 8 to 9, 10 to 16, 16 to 17, source.length to source.length)

            ast.sourceLength() shouldBe source.length
            ast.tokenCount() shouldBe expectedRanges.size
            List(ast.tokenCount()) { index ->
                val positions = ast.tokenPositions(index)
                positions.start to positions.end
            } shouldBe expectedRanges
            ast.tokenTag(ast.tokenCount() - 1) shouldBe TokenTag.EOF

            val iteratedRanges = mutableListOf<Pair<Int, Int>>()
            ast.forEachToken { _, _, positions -> iteratedRanges.add(positions.start to positions.end) }
            iteratedRanges shouldBe expectedRanges
        }
    }

    should("expose UTF-16 offsets for ZON snapshots") {
        useBindings { bindings ->
            val source = ".{ .text = \"π€😀\" }"
            val ast = com.zigrawr.zig.foreign.model.Ast(bindings.parseChars(source, ParseMode.Zon))

            ast.mode() shouldBe Mode.Zon
            ast.sourceLength() shouldBe source.length
            List(ast.tokenCount()) { index ->
                val positions = ast.tokenPositions(index)
                source.substring(positions.start, positions.end)
            } shouldBe listOf(".", "{", ".", "text", "=", "\"π€😀\"", "}", "")
        }
    }

    should("exclude whitespace and ordinary comments from token ranges") {
        useBindings { bindings ->
            val source = "  const  x // π😀\n = \"€\" ; // tail"
            val ast = com.zigrawr.zig.foreign.model.Ast(bindings.parseChars(source, ParseMode.Zig))
            List(ast.tokenCount()) { index ->
                val positions = ast.tokenPositions(index)
                source.substring(positions.start, positions.end)
            } shouldBe listOf("const", "x", "=", "\"€\"", ";", "")
        }
    }

    should("expose EOF and unterminated string ranges") {
        useBindings { bindings ->
            for (source in listOf("", " \r\n\t", "// π😀\n", "const x = \"π😀")) {
                val ast =
                    com.zigrawr.zig.foreign.model.Ast(bindings.parseChars(source, ParseMode.Zig))
                val eof = ast.tokenPositions(ast.tokenCount() - 1)
                eof.start shouldBe source.length
                eof.end shouldBe source.length
                ast.sourceLength() shouldBe source.length
                if (ast.tokenCount() > 1) {
                    val invalid = ast.tokenPositions(ast.tokenCount() - 2)
                    source.substring(invalid.start, invalid.end) shouldBe "\"π😀"
                }
            }
        }
    }

    should("check token position indices") {
        useBindings { bindings ->
            val ast =
                com.zigrawr.zig.foreign.model.Ast(bindings.parseChars("const x = 1;", ParseMode.Zig))
            shouldThrow<IndexOutOfBoundsException> { ast.tokenPositions(-1) }
            shouldThrow<IndexOutOfBoundsException> { ast.tokenPositions(ast.tokenCount()) }
        }
    }

    should("reject snapshots with an unsupported ABI version") {
        useBindings { bindings ->
            val snapshot = bindings.parseChars("const x = 1;", ParseMode.Zig)
            val abiVersionOffset = Layout.Data.zrSnapshotHeader.byteOffset(groupElement("abiVersion")).toInt()
            snapshot.setPackedInt(abiVersionOffset, 2)

            shouldThrow<IllegalArgumentException> { com.zigrawr.zig.foreign.model.Ast(snapshot) }
        }
    }

    should("iterate native tokens and nodes") {
        useBindings { bindings ->
            val ast = com.zigrawr.zig.foreign.model.Ast(
                bindings.parseChars(
                    "const x = \"π\";",
                    ParseMode.Zig
                )
            )
            var tokensSeen = 0
            ast.forEachToken { index, tag, positions ->
                index shouldBe tokensSeen
                tag shouldBe ast.tokenTag(index)
                positions shouldBe ast.tokenPositions(index)
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
            com.zigrawr.zig.foreign.model.Ast(grown).sourceLength() shouldBe large.length

            countedBindings.parseChars(large, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 3

            val shrunk = countedBindings.parseChars(small, ParseMode.Zig)
            counter.calls shouldBe afterFirst + 4
            com.zigrawr.zig.foreign.model.Ast(shrunk).sourceLength() shouldBe small.length
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
        com.zigrawr.zig.foreign.model.Ast(snapshot).sourceLength() shouldBe 14
        com.zigrawr.zig.foreign.model.Ast(snapshot).errorCount() shouldBe 0
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
