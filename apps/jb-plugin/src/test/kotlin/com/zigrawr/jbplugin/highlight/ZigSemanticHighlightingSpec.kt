package com.zigrawr.jbplugin.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.file.ZigLanguage
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.jbplugin.toolchain.ZigToolchainSettings
import io.kotest.matchers.shouldBe
import java.awt.Font

class ZigSemanticHighlightingSpec : IdeaHarness({
    should("color struct fields and implicit enum members without coloring namespace constants") {
        withSemanticSource { fixture, source, highlights ->
            for ((context, name) in listOf(
                "magic: u32" to "magic",
                "abiVersion: u32" to "abiVersion",
                "node_and_node: u32" to "node_and_node",
                ".magic = pair" to "magic",
                ".abiVersion = 1" to "abiVersion",
                "sample.magic" to "magic",
                "nodeData(node).node_and_node" to "node_and_node",
                "enum { if_simple }" to "if_simple",
                "== .if_simple" to "if_simple",
            )) expectRole(source, highlights, context, name, ZigHighlightingColors.FIELD)
            rolesAt(source, highlights, "Ast.staticCheck", "Ast") shouldBe emptySet()
            rolesAt(source, highlights, "const Self", "Self") shouldBe emptySet()
            syntaxKeysAt(fixture, tokenRange(source, "magic: u32", "magic").startOffset)
                .contains(ZigHighlightingColors.BUILTIN_FUNCTION) shouldBe false
        }
    }

    should("color function declarations separately from calls") {
        withSemanticSource { _, source, highlights ->
            for (name in listOf("nodeTag", "nodeData", "staticCheck", "assert", "reserve", "inspect")) {
                expectRole(source, highlights, "fn $name", name, ZigHighlightingColors.FUNCTION_DECLARATION)
            }
            rolesAt(source, highlights, "tree.nodeTag(node)", "nodeTag")
                .contains(ZigHighlightingColors.FUNCTION_DECLARATION) shouldBe false
            rolesAt(source, highlights, "assert(tree", "assert")
                .contains(ZigHighlightingColors.FUNCTION_DECLARATION) shouldBe false
        }
    }

    should("italicize static calls while keeping methods with named pointer receivers plain after edits") {
        withSemanticSource { fixture, source, highlights ->
            expectRole(source, highlights, "assert(tree", "assert", ZigHighlightingColors.STATIC_FUNCTION_CALL)
            expectRole(source, highlights, "Ast.staticCheck", "staticCheck", ZigHighlightingColors.STATIC_FUNCTION_CALL)
            rolesAt(source, highlights, "tree.nodeTag(node)", "nodeTag") shouldBe emptySet()
            rolesAt(source, highlights, "tree.nodeData(node)", "nodeData") shouldBe emptySet()
            val style = fixture.editor.colorsScheme.getAttributes(ZigHighlightingColors.STATIC_FUNCTION_CALL)
            (style.fontType and Font.ITALIC) shouldBe Font.ITALIC

            val edited = source.replace("tree", "receiver")
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.editor.document.setText(edited)
                PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor.document)
            }
            val refreshed = fixture.doHighlighting()
            expectRole(edited, refreshed, "assert(receiver", "assert", ZigHighlightingColors.STATIC_FUNCTION_CALL)
            rolesAt(edited, refreshed, "receiver.nodeTag(node)", "nodeTag") shouldBe emptySet()
            rolesAt(edited, refreshed, "receiver.nodeData(node)", "nodeData") shouldBe emptySet()
        }
    }

    should("color error members in signatures and returned error values") {
        withSemanticSource { _, source, highlights ->
            expectRole(source, highlights, "error{TooLarge}", "TooLarge", ZigHighlightingColors.ERROR_NAME)
            expectRole(source, highlights, "error.TooLarge", "TooLarge", ZigHighlightingColors.ERROR_NAME)
            rolesAt(source, highlights, "== .if_simple", "if_simple")
                .contains(ZigHighlightingColors.ERROR_NAME) shouldBe false
        }
    }

    should("keep functions receiving imported state instances plain") {
        runSelectionFixture({ fixture ->
            fixture.addFileToProject("shared.zig", "pub const ScopedState = struct { result: usize = 0 };")
            val source = """
                const shared = @import("shared.zig");
                const ScopedState = shared.ScopedState;
                fn beginParse(state: *ScopedState, sourceLen: usize) usize {
                    state.result = sourceLen;
                    return state.result;
                }
                fn finishParse(context: *const shared.ScopedState, output: usize) usize {
                    return context.result + output;
                }
                fn staticHelper(value: usize) usize { return value; }
                pub fn parse(state: *ScopedState, sourceLen: usize) usize {
                    const source = beginParse(state, sourceLen);
                    return finishParse(state, staticHelper(source));
                }
            """.trimIndent()
            val file = fixture.addFileToProject("handles.zig", source)
            fixture.configureFromExistingVirtualFile(file.virtualFile)
            val highlights = fixture.doHighlighting()
            expectRole(source, highlights, "fn beginParse", "beginParse", ZigHighlightingColors.FUNCTION_DECLARATION)
            expectRole(source, highlights, "fn finishParse", "finishParse", ZigHighlightingColors.FUNCTION_DECLARATION)
            expectRole(source, highlights, "state.result =", "result", ZigHighlightingColors.FIELD)
            expectRole(source, highlights, "context.result +", "result", ZigHighlightingColors.FIELD)
            expectRole(source, highlights, "staticHelper(source)", "staticHelper", ZigHighlightingColors.STATIC_FUNCTION_CALL)
            rolesAt(source, highlights, "beginParse(state, sourceLen)", "beginParse") shouldBe emptySet()
            rolesAt(source, highlights, "finishParse(state, staticHelper", "finishParse") shouldBe emptySet()
        }, "zig imported state colors")
    }

    should("color builtin function tokens without coloring matching text inside strings and comments") {
        withSemanticSource { fixture, source, _ ->
            for (name in listOf("@intCast", "@This")) {
                val offset = tokenRange(source, if (name == "@intCast") "return @intCast" else "= @This", name).startOffset
                syntaxKeysAt(fixture, offset).contains(ZigHighlightingColors.BUILTIN_FUNCTION) shouldBe true
            }
            for (context in listOf("\"@intCast\"", "// @intCast")) {
                syntaxKeysAt(fixture, tokenRange(source, context, "@intCast").startOffset)
                    .contains(ZigHighlightingColors.BUILTIN_FUNCTION) shouldBe false
            }
        }
    }

    should("color selected-toolchain standard library static calls and aliases while preserving namespace qualifiers") {
        runSelectionFixture({ fixture ->
            fixture.project.service<ZigToolchainSettings>().executable = ""
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val source = """
                const std = @import("std");
                const assert = std.debug.assert;
                const memory = std.mem;
                fn reserve(cursor: *usize, alignment: usize, count: usize, itemSize: usize) error{TooLarge}!u32 {
                    const start = std.mem.alignForward(usize, cursor.*, alignment);
                    const size = std.math.mul(usize, count, itemSize) catch return error.TooLarge;
                    const end = std.math.add(usize, start, size) catch return error.TooLarge;
                    assert(end >= start);
                    cursor.* = memory.alignForward(usize, end, alignment);
                    return @intCast(start);
                }
            """.trimIndent()
            val file = fixture.addFileToProject("standard-colors.zig", source)
            fixture.configureFromExistingVirtualFile(file.virtualFile)
            val highlights = fixture.doHighlighting()
            for ((context, name) in listOf(
                "std.mem.alignForward(" to "alignForward",
                "std.math.mul(" to "mul",
                "std.math.add(" to "add",
                "assert(end" to "assert",
                "memory.alignForward(" to "alignForward",
            )) expectRole(source, highlights, context, name, ZigHighlightingColors.STATIC_FUNCTION_CALL)
            for ((context, name) in listOf(
                "std.mem.alignForward(" to "mem",
                "std.math.mul(" to "math",
                "std.debug.assert" to "debug",
                "memory.alignForward(" to "memory",
            )) rolesAt(source, highlights, context, name) shouldBe emptySet()
        }, "zig standard library colors")
    }
})

private val semanticSource = """
    const Tag = enum { if_simple };
    const NodeData = struct { node_and_node: u32 };
    const Ast = struct {
        const Self = @This();
        magic: u32,
        abiVersion: u32,
        pub fn nodeTag(tree: *const Ast, node: u32) Tag { _ = tree; _ = node; return .if_simple; }
        pub fn nodeData(tree: *const Self, node: u32) NodeData { _ = tree; return .{ .node_and_node = node }; }
        pub fn staticCheck(ok: bool) void { _ = ok; }
    };
    fn assert(ok: bool) void { _ = ok; }
    fn reserve(cursor: *usize) error{TooLarge}!u32 {
        if (cursor.* > 0) return error.TooLarge;
        return @intCast(cursor.*);
    }
    pub fn inspect(tree: *const Ast, node: u32) void {
        assert(tree.nodeTag(node) == .if_simple);
        const pair = tree.nodeData(node).node_and_node;
        const sample = Ast{ .magic = pair, .abiVersion = 1 };
        Ast.staticCheck(sample.magic == 1);
        const text = "@intCast";
        _ = text;
        // @intCast
    }
""".trimIndent()

private fun withSemanticSource(block: (CodeInsightTestFixture, String, List<HighlightInfo>) -> Unit) {
    runSelectionFixture({ fixture ->
        val file = fixture.addFileToProject("semantic-colors.zig", semanticSource)
        fixture.configureFromExistingVirtualFile(file.virtualFile)
        block(fixture, semanticSource, fixture.doHighlighting())
    }, "zig semantic colors")
}

private fun tokenRange(source: String, context: String, name: String): TextRange {
    val start = source.indexOf(context)
    check(start >= 0 && context.indexOf(name) >= 0) { "Missing token context: $context / $name" }
    return TextRange.from(start + context.indexOf(name), name.length)
}

private fun expectRole(source: String, highlights: List<HighlightInfo>, context: String, name: String, key: TextAttributesKey) {
    rolesAt(source, highlights, context, name).contains(key) shouldBe true
}

private fun rolesAt(source: String, highlights: List<HighlightInfo>, context: String, name: String): Set<TextAttributesKey> {
    val range = tokenRange(source, context, name)
    return highlights.asSequence().filter { it.startOffset == range.startOffset && it.endOffset == range.endOffset }
        .mapNotNull { it.forcedTextAttributesKey }.filter { it in semanticKeys }.toSet()
}

private val semanticKeys = setOf(
    ZigHighlightingColors.FIELD,
    ZigHighlightingColors.FUNCTION_DECLARATION,
    ZigHighlightingColors.STATIC_FUNCTION_CALL,
    ZigHighlightingColors.BUILTIN_FUNCTION,
    ZigHighlightingColors.ERROR_NAME,
)

private fun syntaxKeysAt(fixture: CodeInsightTestFixture, offset: Int): Set<TextAttributesKey> =
    ReadAction.computeBlocking<Set<TextAttributesKey>, RuntimeException> {
        val token = (fixture.editor as EditorEx).highlighter.createIterator(offset).tokenType
        SyntaxHighlighterFactory.getSyntaxHighlighter(ZigLanguage, fixture.project, fixture.file.virtualFile)
            .getTokenHighlights(token).toSet()
    }
