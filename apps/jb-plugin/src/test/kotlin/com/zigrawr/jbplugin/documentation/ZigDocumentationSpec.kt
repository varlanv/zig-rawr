package com.zigrawr.jbplugin.documentation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ZigDocumentationSpec : IdeaHarness({
    should("show declaration signatures and escaped documentation at declarations and usages") {
        runSelectionFixture({ fixture ->
            fixture.configureByText("documentation.zig", """
                /// Adds <values> & returns a result.
                pub fn add(a: u32, b: u32) u32 { const implementation_detail = a + b; return implementation_detail; }
                /// Fixed <limit>.
                const limit: u32 = 4;
                /// Stores <state>.
                const Box = struct { hidden_detail: u32, };
                /// Orphaned comment.

                const plain = 2;
                fn caller() void { _ = add(limit, plain); _ = Box; _ = missing; }
            """.trimIndent())

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
            val library = fixture.addFileToProject("lib/math.zig", """
                /// Adds one.
                pub fn add(value: u32) u32 { return value + 1; }
            """.trimIndent())
            val main = fixture.addFileToProject("main.zig", """
                const math = @import("lib/math.zig");
                const alias = math;
                fn caller() void { _ = alias.add(2); _ = @import("lib/math.zig").add(3); }
            """.trimIndent())
            fixture.configureFromExistingVirtualFile(main.virtualFile)
            val selected = targets(fixture, "alias.add(2)", "add").single()
            val before = html(selected)
            before shouldContain "pub fn add(value: u32) u32"
            before shouldContain "Adds one."
            html(targets(fixture, ").add(3)", "add").single()) shouldBe before
            val pointer = ReadAction.computeBlocking<com.intellij.model.Pointer<out DocumentationTarget>, RuntimeException> {
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
