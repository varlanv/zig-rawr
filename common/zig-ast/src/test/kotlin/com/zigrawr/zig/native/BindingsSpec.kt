package com.zigrawr.zig.native

import com.zigrawr.lang.bin.asReadonly
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.tokenizer.BaseSpec
import com.zigrawr.zig.tokenizer.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files

val handles = Loader.load()

fun useBindings(block: (bindings: Bindings) -> Unit) {
    val bindings = Bindings(handles)
    try {
        block(bindings)
    } finally {
        bindings.close()
    }
}

class BindingsSpec : BaseSpec({

    should("encode source text as UTF-8") {
        useBindings { bindings ->
            val parse = bindings.parseChars("const x = \"π\";", ParseMode.Zig)
            parse[12].toInt() shouldBe 15
        }
    }

    should("parse source bytes") {
        useBindings { bindings ->
            val parse = bindings.parseBytes("const x = \"π\";".toByteArray().asReadonly(), ParseMode.Zig)
            parse[12].toInt() shouldBe 15
        }
    }

    should("parse a source file") {
        useTempDir { dir ->
            val path = Files.createFile(dir.resolve("zigrawr.zig"))
            Files.writeString(path, "const x = \"π\";")
            useBindings { bindings ->
                val parse = bindings.parseAtPath(path)
                parse[12].toInt() shouldBe 15
            }
        }
    }

    should("read the native AST snapshot") {
        useBindings { bindings ->
            val ast = NativeAst(bindings.parseChars("const x = \"π\";", ParseMode.Zig))
            ast.mode() shouldBe NativeAst.Mode.Zig
            ast.sourceLength() shouldBe 15
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
            ast.forEachNode { index, tag, mainToken, first, second ->
                index shouldBe nodesSeen
                tag shouldBe ast.nodeTag(index)
                mainToken shouldBe ast.nodeMainToken(index)
                if (index == 0) {
                    first shouldBe 0
                    second shouldBe 1
                }
                nodesSeen++
            }
            nodesSeen shouldBe ast.nodeCount()
        }
    }
})
