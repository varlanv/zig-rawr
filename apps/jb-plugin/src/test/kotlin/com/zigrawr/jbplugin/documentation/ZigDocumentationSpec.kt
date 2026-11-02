package com.zigrawr.jbplugin.documentation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.PlatformTestUtil
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.jbplugin.toolchain.ZigToolchainSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ZigDocumentationSpec : IdeaHarness({
    should("show declaration signatures and escaped documentation at declarations and usages") {
        runSelectionFixture({ fixture ->
            fixture.configureByText(
                "documentation.zig", """
                /// Adds <values> & returns a result.
                pub fn add(a: u32, b: u32) u32 { const implementation_detail = a + b; return implementation_detail; }
                /// Fixed <limit>.
                const limit: u32 = 4;
                /// Stores <state>.
                const Box = struct { hidden_detail: u32, };
                /// Orphaned comment.

                const plain = 2;
                fn caller() void { _ = add(limit, plain); _ = Box; _ = missing; }
            """.trimIndent()
            )

            val function = html(targets(fixture, "fn add(", "add").single())
            function shouldContain "pub fn add(a: u32, b: u32) u32"
            function shouldContain "Adds &lt;values&gt; &amp; returns a result."
            function shouldNotContain "implementation_detail"
            html(targets(fixture, "= add(limit", "add").single()) shouldBe function
            val variable = html(targets(fixture, "const limit:", "limit").single())
            variable shouldContain "const limit: u32"
            variable shouldContain "Fixed &lt;limit&gt;."
            html(targets(fixture, "add(limit,", "limit").single()) shouldBe variable
            val structure = html(targets(fixture, "const Box", "Box").single())
            structure shouldContain "const Box = struct"
            structure shouldContain "Stores &lt;state&gt;."
            structure shouldNotContain "hidden_detail"
            html(targets(fixture, "= Box;", "Box").single()) shouldBe structure
            val plain = html(targets(fixture, "const plain", "plain").single())
            plain shouldContain "const plain = 2"
            plain shouldNotContain "Orphaned comment"
            targets(fixture, "= missing;", "missing").size shouldBe 0
        }, "zig documentation")
    }

    should("resolve imported documentation and refresh a target pointer after library edits") {
        runSelectionFixture({ fixture ->
            val library = fixture.addFileToProject(
                "lib/math.zig", """
                /// Adds one.
                pub fn add(value: u32) u32 { return value + 1; }
            """.trimIndent()
            )
            val main = fixture.addFileToProject(
                "main.zig", """
                const math = @import("lib/math.zig");
                const alias = math;
                fn caller() void { _ = alias.add(2); _ = @import("lib/math.zig").add(3); }
            """.trimIndent()
            )
            fixture.configureFromExistingVirtualFile(main.virtualFile)
            val selected = targets(fixture, "alias.add(2)", "add").single()
            val before = html(selected)
            before shouldContain "pub fn add(value: u32) u32"
            before shouldContain "Adds one."
            html(targets(fixture, ").add(3)", "add").single()) shouldBe before
            val pointer =
                ReadAction.computeBlocking<com.intellij.model.Pointer<out DocumentationTarget>, RuntimeException> {
                    selected.createPointer()
                }
            val document = PsiDocumentManager.getInstance(fixture.project).getDocument(library)!!
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                document.insertString(0, "const header = 1;\n")
                val start = document.text.indexOf("Adds one.")
                document.replaceString(start, start + "Adds one.".length, "Updated <documentation>.")
            }
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
            val after = html(pointer.dereference()!!)
            after shouldContain "pub fn add(value: u32) u32"
            after shouldContain "Updated &lt;documentation&gt;."
            after shouldNotContain "Adds one."
            html(targets(fixture, "alias.add(2)", "add").single()) shouldBe after
        }, "zig imported documentation")
    }

    should("show inferred local types from standard library generic functions and error handling") {
        runSelectionFixture({ fixture ->
            fixture.project.service<ZigToolchainSettings>().executable = ""
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            fixture.configureByText(
                "inferred_types.zig", """
                const std = @import("std");
                fn reserve(cursor: *usize, alignment: usize, count: usize, itemSize: usize) error{TooLarge}!u32 {
                    const start = std.mem.alignForward(usize, cursor.*, alignment);
                    const size = std.math.mul(usize, count, itemSize) catch return error.TooLarge;
                    const end = std.math.add(usize, start, size) catch return error.TooLarge;
                    cursor.* = end;
                    return @intCast(start);
                }
            """.trimIndent()
            )
            for (name in listOf("start", "size", "end")) {
                val declaration = html(targets(fixture, "const $name =", name).single())
                declaration shouldContain "const $name ="
                declaration shouldContain "Type: <code>usize</code>"
            }
            html(targets(fixture, "start, size)", "size").single()) shouldContain "Type: <code>usize</code>"
            html(targets(fixture, "cursor.* = end", "end").single()) shouldContain "Type: <code>usize</code>"
            html(targets(fixture, "cursor: *usize", "cursor").single()) shouldContain "cursor: *usize"
        }, "zig inferred local documentation")
    }

    should("document and navigate built-in properties on string literals and slices") {
        runSelectionFixture({ fixture ->
            fixture.configureByText(
                "builtin_properties.zig", """
                fn example(bytes: []align(8) u8) void {
                    const fallback = "".ptr;
                    _ = fallback;
                    _ = bytes.ptr;
                    _ = bytes.len;
                }
            """.trimIndent()
            )
            html(targets(fixture, "\"\".ptr", "ptr").single()) shouldContain "ptr: [*:0]const u8"
            html(targets(fixture, "const fallback =", "fallback").single()) shouldContain "Type: <code>[*:0]const u8</code>"
            html(targets(fixture, "bytes.ptr", "ptr").single()) shouldContain "ptr: [*]align(8) u8"
            html(targets(fixture, "bytes.len", "len").single()) shouldContain "len: usize"
            val source = fixture.editor.document.text
            val literalOffset = source.indexOf("\"\".ptr") + 3
            fixture.editor.caretModel.moveToOffset(literalOffset)
            val target = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, literalOffset).single()
            target.text shouldBe "\"\""
            HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
            fixture.editor.markupModel.allHighlighters.filter {
                it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES ||
                    it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
            }.map { TextRange(it.startOffset, it.endOffset) } shouldBe listOf(TextRange.from(literalOffset, 3))
            val pointer = ReadAction.computeBlocking<com.intellij.model.Pointer<out DocumentationTarget>, RuntimeException> {
                targets(fixture, "\"\".ptr", "ptr").single().createPointer()
            }
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.editor.document.insertString(0, "\n")
            }
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
            html(pointer.dereference()!!) shouldContain "ptr: [*:0]const u8"
        }, "zig built-in property documentation")
    }
})

private fun targets(fixture: CodeInsightTestFixture, context: String, name: String): List<DocumentationTarget> =
    ReadAction.computeBlocking<List<DocumentationTarget>, RuntimeException> {
        val source = fixture.editor.document.immutableCharSequence
        val start = source.indexOf(context)
        check(start >= 0 && context.indexOf(name) >= 0)
        DocumentationTargetProvider.EP_NAME.extensionList.single { it is ZigDocumentationProvider }
            .documentationTargets(fixture.file, start + context.indexOf(name))
    }

private fun html(target: DocumentationTarget): String = ReadAction.computeBlocking<String, RuntimeException> {
    (target.computeDocumentation() as DocumentationData).html
}
