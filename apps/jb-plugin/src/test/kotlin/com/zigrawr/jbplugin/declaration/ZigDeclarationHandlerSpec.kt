package com.zigrawr.jbplugin.declaration

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe

class ZigDeclarationHandlerSpec : IdeaHarness({
    should("navigate from every caret position through the end of an identifier") {
        runSelectionFixture({ fixture ->
            val source = "const value = 1;\nfn target() void {}\nfn caller() void { target(); _ = value; }"
            fixture.configureByText("caret-navigation.zig", source)
            for ((usage, name) in listOf("target();" to "target", "value;" to "value")) {
                val start = source.indexOf(usage)
                for (position in 0..name.length) {
                    val offset = start + position
                    fixture.editor.caretModel.moveToOffset(offset)
                    val target = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).single()
                    target.text shouldBe name
                    target.textOffset shouldBe source.indexOf(name)
                }
            }
        }, "zig identifier caret navigation")
    }

    should("navigate lexical declarations and refresh them after edits") {
        runSelectionFixture({ fixture ->
            val source = """
                const value = 1;
                fn first(parameter: u32) void {
                    const local = parameter;
                    {
                        const value = local;
                        _ = value + 1;
                    }
                    _ = value + 2;
                    _ = local + 3;
                    later();
                }
                fn later() void {}
                fn sibling() void { const hidden = 1; _ = hidden; }
                fn isolated() void { _ = hidden; }
                const cycleA = cycleB;
                const cycleB = cycleA;
                fn cyclic() void { cycleA.add(); }
            """.trimIndent()
            fixture.configureByText("navigation.zig", source)

            fun check(context: String, name: String, declaration: String) {
                val target = targets(fixture, context, name).single()
                target.containingFile shouldBe fixture.file
                target.text shouldBe name
                target.textOffset shouldBe source.indexOf(declaration) + declaration.indexOf(name)
            }

            check("= parameter;", "parameter", "parameter: u32")
            check("= value + 1;", "value", "const value = local;")
            check("= value + 2;", "value", "const value = 1;")
            check("= local + 3;", "local", "const local = parameter;")
            check("later();", "later", "fn later()")
            targets(fixture, "fn isolated() void { _ = hidden; }", "hidden").size shouldBe 0
            targets(fixture, "cycleA.add();", "add").size shouldBe 0

            val declarationOffset = source.indexOf("local = parameter")
            fixture.editor.selectionModel.setSelection(declarationOffset, declarationOffset + "local".length)
            fixture.type("fresh")
            targets(fixture, "= local + 3;", "local").size shouldBe 0

            val usageOffset = fixture.editor.document.text.indexOf("local + 3")
            fixture.editor.selectionModel.setSelection(usageOffset, usageOffset + "local".length)
            fixture.type("fresh")
            targets(fixture, "= fresh + 3;", "fresh").single().textOffset shouldBe declarationOffset
        }, "zig local navigation")
    }

    should("navigate relative imports and members across files and namespaces") {
        runSelectionFixture({ fixture ->
            val librarySource = """
                pub fn add(a: u32, b: u32) u32 { return a + b; }
                fn secret() void {}
                pub const Tools = struct {
                    pub fn twice(value: u32) u32 { return value * 2; }
                    pub fn hidden() void {}
                };
            """.trimIndent()
            val library = fixture.addFileToProject("src/lib/math.zig", librarySource)
            val source = """
                const math = @import("lib/math.zig");
                const Tools = math.Tools;
                const Box: type = struct { pub fn run() void {} };
                fn main() void {
                    _ = math.add(1, 2);
                    _ = @import("lib/math.zig").add(3, 4);
                    _ = (math).add(9, 10);
                    _ = math.Tools.twice(5);
                    _ = Tools.twice(6);
                    Box.run();
                    math.hidden();
                    math.secret();
                    unknown.add(7, 8);
                }
            """.trimIndent()
            val main = fixture.addFileToProject("src/main.zig", source)
            fixture.configureFromExistingVirtualFile(main.virtualFile)

            targets(fixture, "const math = @import(\"lib/math.zig\");", "lib/math.zig").single() shouldBe library
            for (context in listOf("math.add(1, 2)", "@import(\"lib/math.zig\").add(3, 4)", "(math).add(9, 10)")) {
                val found = targets(fixture, context, "add")
                check(found.size == 1) { context }
                val target = found.single()
                target.containingFile shouldBe library
                target.text shouldBe "add"
                target.textOffset shouldBe librarySource.indexOf("add(")
            }
            for (context in listOf("math.Tools.twice(5)", "Tools.twice(6)")) {
                val target = targets(fixture, context, "twice").single()
                target.containingFile shouldBe library
                target.textOffset shouldBe librarySource.indexOf("twice(")
            }
            targets(fixture, "Box.run();", "run").single().textOffset shouldBe source.indexOf("run()")
            targets(fixture, "math.hidden();", "hidden").size shouldBe 0
            targets(fixture, "math.secret();", "secret").size shouldBe 0
            targets(fixture, "unknown.add(7, 8);", "add").size shouldBe 0

            val prefix = "const header = 1;\n"
            val libraryDocument = PsiDocumentManager.getInstance(fixture.project).getDocument(library)!!
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                libraryDocument.insertString(0, prefix)
            }
            targets(fixture, "math.add(1, 2)", "add").single().textOffset shouldBe
                prefix.length + librarySource.indexOf("add(")
        }, "zig imported navigation")
    }
})

private fun targets(fixture: CodeInsightTestFixture, context: String, name: String): Array<PsiElement> {
    val contextOffset = fixture.editor.document.text.indexOf(context)
    check(contextOffset >= 0)
    val nameOffset = context.indexOf(name)
    check(nameOffset >= 0)
    val offset = contextOffset + nameOffset
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    return GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset)
}
