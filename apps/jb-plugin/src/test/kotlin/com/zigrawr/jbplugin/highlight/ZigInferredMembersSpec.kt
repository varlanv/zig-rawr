package com.zigrawr.jbplugin.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.documentation.ZigDocumentationProvider
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.jbplugin.toolchain.ZigToolchainSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class ZigInferredMembersSpec : IdeaHarness({
    should("resolve nested state fields and slice properties in the actual parse helper") {
        withHandlesSource { fixture, source, highlights, _ ->
            val status = target(fixture, source, "state.result.status", "status")
            status.text shouldBe "status"
            status.containingFile.name shouldBe "shared.zig"
            hasRole(source, highlights, "state.result.status", "status", ZigHighlightingColors.FIELD) shouldBe true
            usageRanges(fixture, source, "state.result.status", "status")
                .contains(memberRange(source, "state.result.status", "status")) shouldBe true

            val bytesDeclaration = source.indexOf("const bytes =") + "const ".length
            for (name in listOf("ptr", "len")) {
                val context = "bytes.$name"
                hasRole(source, highlights, context, name, ZigHighlightingColors.FIELD) shouldBe true
                val destination = target(fixture, source, context, name)
                destination.containingFile shouldBe fixture.file
                destination.textOffset shouldBe bytesDeclaration
                destination.text shouldBe "bytes"
                val usages = usageRanges(fixture, source, context, name)
                usages.contains(memberRange(source, context, name)) shouldBe true
                if (name == "len") usages.contains(memberRange(source, "source.len / 8", "len")) shouldBe false
            }
        }
    }

    should("follow tokenizer initialization and next results through token member chains") {
        withHandlesSource { fixture, source, highlights, _ ->
            val next = target(fixture, source, "scanner.next()", "next")
            next.text shouldBe "next"
            next.containingFile.name shouldBe "tokenizer.zig"
            hasRole(source, highlights, "scanner.next()", "next", ZigHighlightingColors.STATIC_FUNCTION_CALL) shouldBe false
            usageRanges(fixture, source, "scanner.next()", "next")
                .contains(memberRange(source, "scanner.next()", "next")) shouldBe true

            for ((context, name) in listOf(
                "token.tag," to "tag",
                "token.loc.start" to "loc",
                "token.loc.start" to "start",
                "token.loc.end" to "end",
            )) {
                val destination = target(fixture, source, context, name)
                destination.text shouldBe name
                destination.containingFile.name shouldBe "tokenizer.zig"
                hasRole(source, highlights, context, name, ZigHighlightingColors.FIELD) shouldBe true
            }
            val tags = usageRanges(fixture, source, "token.tag,", "tag")
            for (context in listOf("token.tag,", "token.tag == .eof")) {
                tags.contains(memberRange(source, context, "tag")) shouldBe true
            }
            val locations = usageRanges(fixture, source, "token.loc.start", "loc")
            for (context in listOf("token.loc.start", "token.loc.end")) {
                locations.contains(memberRange(source, context, "loc")) shouldBe true
            }
        }
    }

    should("resolve methods of a standard library collection instantiated with an anonymous token type") {
        withHandlesSource { fixture, source, highlights, standardLibrary ->
            for (name in listOf("deinit", "ensureTotalCapacity", "append")) {
                val context = "tokens.$name("
                val destination = target(fixture, source, context, name)
                destination.text shouldBe name
                destination.containingFile.virtualFile.toNioPath().toRealPath().startsWith(standardLibrary) shouldBe true
                hasRole(source, highlights, context, name, ZigHighlightingColors.STATIC_FUNCTION_CALL) shouldBe false
                usageRanges(fixture, source, context, name)
                    .contains(memberRange(source, context, name)) shouldBe true
            }
        }
    }

    should("resolve contextual members and distinguish receiver occurrences in the actual parse source") {
        withHandlesSource { fixture, source, _, _ ->
            val initializer = "return .{\n        .tokenTags = tokenTags"
            val field = target(fixture, source, initializer, "tokenTags")
            field.text shouldBe "tokenTags"
            field.containingFile.name shouldBe "export_tokenizer.zig"
            documentation(fixture, source, initializer, "tokenTags") shouldContain "tokenTags: u32"
            usageRanges(fixture, source, initializer, "tokenTags")
                .contains(memberRange(source, initializer, "tokenTags")) shouldBe true

            val reserveStart = source.indexOf("fn reserve(")
            val reserveEnd = source.indexOf("fn tokenLayoutFor(", reserveStart)
            val errors = Regex("\\bTooLarge\\b").findAll(source, reserveStart)
                .takeWhile { it.range.first < reserveEnd }
                .map { TextRange.from(it.range.first, it.value.length) }.toList()
            errors.size shouldBe 4
            usageRanges(fixture, source, "std.math.mul(usize, count, itemSize) catch return error.TooLarge", "TooLarge")
                .sortedBy { it.startOffset } shouldBe errors

            val layoutStart = source.indexOf("fn layoutFor(")
            val layoutEnd = source.indexOf("fn parseInto(", layoutStart)
            for (name in listOf("tokens", "nodes")) {
                val expected = Regex("ast\\.$name\\.len\\b").findAll(source, layoutStart)
                    .takeWhile { it.range.first < layoutEnd }
                    .map { TextRange.from(it.range.first + it.value.lastIndexOf("len"), 3) }.toList()
                check(expected.size > 1)
                usageRanges(fixture, source, "ast.$name.len >", "len").sortedBy { it.startOffset } shouldBe expected
            }

            for (name in listOf("fn_proto_simple", "fn_proto", "fn_proto_one", "fn_proto_multi")) {
                val context = when (name) {
                    "fn_proto_simple" -> ".fn_proto_simple => ast.nodeData"
                    "fn_proto" -> ".fn_proto, .fn_proto_one"
                    "fn_proto_one" -> ".fn_proto_one, .fn_proto_multi"
                    else -> ".fn_proto_multi => ast.nodeData"
                }
                val declaration = target(fixture, source, context, name)
                declaration.text shouldBe name
                declaration.containingFile.name shouldBe "Ast.zig"
                val astSource = declaration.containingFile.text
                val node = astSource.indexOf("pub const Node = struct")
                val tags = astSource.indexOf("pub const Tag = enum", node)
                (declaration.textOffset > tags) shouldBe true
                documentation(fixture, source, context, name) shouldContain name
                usageRanges(fixture, source, context, name)
                    .contains(memberRange(source, context, name)) shouldBe true
            }
        }
    }

    should("resolve indexed node payload fields and every switch tag against their actual declarations") {
        withHandlesSource { fixture, source, highlights, _ ->
            val start = source.indexOf("fn nodePayload(")
            val end = source.indexOf("fn testSnapshot(", start)
            val payload = source.substring(start, end)
            val fields = Regex("data\\.(@\"[^\"]+\"|[A-Za-z_][A-Za-z_0-9]*)").findAll(payload)
                .map { it.value to it.groupValues[1] }.distinct().toList() + listOf(
                "data.extra_range.start" to "start",
                "data.extra_range.end" to "end",
            )
            fields.any { it.second == "node_and_node" } shouldBe true
            fields.any { it.second == "@\"for\"" } shouldBe true
            for ((context, name) in fields) {
                val destination = target(fixture, source, context, name)
                destination.text shouldBe name
                destination.containingFile.name shouldBe "Ast.zig"
                val astSource = destination.containingFile.text
                val container = astSource.indexOf(if (name == "start" || name == "end") "pub const SubRange = struct" else "pub const Data = union")
                (destination.textOffset > container) shouldBe true
                hasRole(source, highlights, context, name, ZigHighlightingColors.FIELD) shouldBe true
                val occurrences = usageRanges(fixture, source, context, name)
                occurrences.contains(memberRange(source, context, name)) shouldBe true
                documentation(fixture, source, context, name) shouldContain name.replace("@\"", "").removeSuffix("\"")
            }

            val labels = Regex("(?m)^[\\t ]*\\.(?!\\{)[^\\r\\n]*").findAll(payload)
                .flatMap { line ->
                    Regex("\\.(@\"[^\"]+\"|[A-Za-z_][A-Za-z_0-9]*)")
                        .findAll(line.value.substringBefore("=>")).map {
                            it.groupValues[1] to start + line.range.first + it.range.first + 1
                        }
                }.distinctBy { it.first }.toList()
            (labels.size > 100) shouldBe true
            for ((name, offset) in labels) {
                val selection = TextRange.from(offset, name.length)
                val destination = targetAt(fixture, selection.startOffset, name)
                destination.text shouldBe name
                destination.containingFile.name shouldBe "Ast.zig"
                val astSource = destination.containingFile.text
                val tags = astSource.indexOf("pub const Tag = enum", astSource.indexOf("pub const Node = struct"))
                (destination.textOffset > tags) shouldBe true
                highlights.any {
                    it.startOffset == selection.startOffset && it.endOffset == selection.endOffset &&
                        it.forcedTextAttributesKey == ZigHighlightingColors.FIELD
                } shouldBe true
                usageRangesAt(fixture, selection.startOffset).contains(selection) shouldBe true
            }
        }
    }

    should("resolve comparison enums and initializer fields inside the actual returned switch branches") {
        withHandlesSource { fixture, source, highlights, _ ->
            val mode = target(fixture, source, "if (ast.mode == .zig)", "zig")
            mode.containingFile.name shouldBe "Ast.zig"
            mode.text shouldBe "zig"
            mode.textOffset shouldBe mode.containingFile.text.indexOf("zig, zon")
            documentation(fixture, source, "if (ast.mode == .zig)", "zig") shouldContain "zig"
            usageRanges(fixture, source, "if (ast.mode == .zig)", "zig")
                .contains(memberRange(source, "if (ast.mode == .zig)", "zig")) shouldBe true
            hasRole(source, highlights, "if (ast.mode == .zig)", "zig", ZigHighlightingColors.FIELD) shouldBe true
            for (name in listOf("first", "second")) {
                val context = ".$name = word(data.extra_range."
                val field = target(fixture, source, context, name)
                field.containingFile.name shouldBe "export_ast.zig"
                field.text shouldBe name
                field.textOffset shouldBe field.containingFile.text.indexOf("$name: u32")
                documentation(fixture, source, context, name) shouldContain "$name: u32"
                hasRole(source, highlights, context, name, ZigHighlightingColors.FIELD) shouldBe true
                usageRanges(fixture, source, context, name).contains(memberRange(source, context, name)) shouldBe true
            }
        }
    }

    should("resolve collection field enum arguments to the actual node fields") {
        withHandlesSource { fixture, source, highlights, _ ->
            for ((name, type) in listOf("tag" to "Tag", "data" to "Data")) {
                val context = "ast.nodes.items(.$name)[index]"
                val destination = target(fixture, source, context, name)
                destination.text shouldBe name
                destination.containingFile.name shouldBe "Ast.zig"
                val astSource = destination.containingFile.text
                destination.textOffset shouldBe astSource.indexOf("$name: $type", astSource.indexOf("pub const Node = struct"))
                hasRole(source, highlights, context, name, ZigHighlightingColors.FIELD) shouldBe true
                usageRanges(fixture, source, context, name).contains(memberRange(source, context, name)) shouldBe true
                documentation(fixture, source, context, name) shouldContain "$name: $type"
            }
            target(fixture, source, "two(data.node_and_node)", "node_and_node").text shouldBe "node_and_node"
            target(fixture, source, "return switch (tag) {\n        .root", "root").text shouldBe "root"
        }
    }

    should("keep generated field enums and indexed results specific to their aggregate type") {
        runSelectionFixture({ fixture ->
            fixture.project.service<ZigToolchainSettings>().executable = ""
            PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
            val source = """
                const std = @import("std");
                const KindA = enum { alpha, beta };
                const KindB = enum { alpha, gamma };
                const Payload = union { count: usize, ready: bool };
                const EntryA = struct { kind: KindA, payload: Payload };
                const EntryB = struct { kind: KindB, body: Payload };
                const RowsA = std.MultiArrayList(EntryA);
                const RowsB = std.MultiArrayList(EntryB);
                fn inspect(left: RowsA.Slice, right: RowsB.Slice, index: usize) void {
                    const leftKind = left.items(.kind)[index];
                    const rightKind = right.items(.kind)[index];
                    const payload = left.items(.payload)[index];
                    _ = payload.count;
                    switch (leftKind) { .alpha => {}, .beta => {} }
                    switch (rightKind) { .alpha => {}, .gamma => {} }
                    _ = left.items(.tag);
                    _ = right.items(.payload);
                }
            """.trimIndent()
            fixture.configureByText("generated-field-enums.zig", source)
            val highlights = fixture.doHighlighting()
            val kindA = source.indexOf("kind: KindA")
            val kindB = source.indexOf("kind: KindB")
            for ((context, expected) in listOf("left.items(.kind)" to kindA, "right.items(.kind)" to kindB)) {
                val declaration = target(fixture, source, context, "kind")
                declaration.containingFile shouldBe fixture.file
                declaration.textOffset shouldBe expected
                hasRole(source, highlights, context, "kind", ZigHighlightingColors.FIELD) shouldBe true
                usageRanges(fixture, source, context, "kind").sortedBy { it.startOffset } shouldBe listOf(
                    TextRange.from(expected, 4), memberRange(source, context, "kind")
                )
            }
            target(fixture, source, "left.items(.payload)", "payload").textOffset shouldBe source.indexOf("payload: Payload")
            documentation(fixture, source, "left.items(.payload)", "payload") shouldContain "payload: Payload"
            target(fixture, source, "payload.count", "count").textOffset shouldBe source.indexOf("count: usize")
            target(fixture, source, "switch (leftKind) { .alpha", "alpha").textOffset shouldBe source.indexOf("alpha, beta")
            target(fixture, source, "switch (rightKind) { .alpha", "alpha").textOffset shouldBe source.indexOf("alpha, gamma")
            target(fixture, source, ".beta =>", "beta").textOffset shouldBe source.indexOf("beta }")
            target(fixture, source, ".gamma =>", "gamma").textOffset shouldBe source.indexOf("gamma }")
            for ((context, name) in listOf("left.items(.tag)" to "tag", "right.items(.payload)" to "payload")) {
                val offset = memberRange(source, context, name).startOffset
                fixture.editor.caretModel.moveToOffset(offset)
                GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).size shouldBe 0
                usageRangesAt(fixture, offset) shouldBe emptyList()
            }
        }, "zig reflected aggregate field enums")
    }

    should("preserve slice and element types while rejecting aggregate fields on reflection metadata") {
        runSelectionFixture({ fixture ->
            val source = """
                const Kind = enum { ready };
                const Entry = struct { kind: Kind };
                fn inspect(rows: []Entry) void {
                    const slice = rows[0..];
                    _ = slice[0].kind;
                    _ = rows[0..][0].kind;
                    _ = rows[0].kind;
                    _ = rows[0..].len;
                    _ = slice.len;
                    _ = @typeInfo(Entry).kind;
                    _ = @typeInfo(Entry).@"struct".kind;
                }
            """.trimIndent()
            fixture.configureByText("indexed-slice-metadata.zig", source)
            val highlights = fixture.doHighlighting()
            for (context in listOf("slice[0].kind", "rows[0..][0].kind", "rows[0].kind")) {
                target(fixture, source, context, "kind").textOffset shouldBe source.indexOf("kind: Kind")
                hasRole(source, highlights, context, "kind", ZigHighlightingColors.FIELD) shouldBe true
                usageRanges(fixture, source, context, "kind").contains(memberRange(source, context, "kind")) shouldBe true
            }
            for ((context, declaration) in listOf("rows[0..].len" to "rows: []Entry", "slice.len" to "slice = rows")) {
                target(fixture, source, context, "len").textOffset shouldBe source.indexOf(declaration)
                hasRole(source, highlights, context, "len", ZigHighlightingColors.FIELD) shouldBe true
                documentation(fixture, source, context, "len") shouldContain "len: usize"
            }
            for (context in listOf("@typeInfo(Entry).kind", "@typeInfo(Entry).@\"struct\".kind")) {
                val offset = memberRange(source, context, "kind").startOffset
                fixture.editor.caretModel.moveToOffset(offset)
                GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset).size shouldBe 0
                usageRangesAt(fixture, offset) shouldBe emptyList()
                hasRole(source, highlights, context, "kind", ZigHighlightingColors.FIELD) shouldBe false
            }
        }, "zig indexed slices and reflected metadata")
    }
})

private fun withHandlesSource(block: (CodeInsightTestFixture, String, List<HighlightInfo>, Path) -> Unit) {
    runSelectionFixture({ fixture ->
        fixture.project.service<ZigToolchainSettings>().executable = ""
        val info = PlatformTestUtil.waitForFuture(fixture.project.service<ZigToolchainService>().refresh(), 15_000)!!
        val nativeSource = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve("native/zig/src") }.first { Files.isRegularFile(it.resolve("handles.zig")) }
        for (relative in listOf(
            "shared.zig", "export_ast.zig", "export_tokenizer.zig", "utf_conversion.zig",
            "ziglang/Ast.zig", "ziglang/tokenizer.zig",
        )) {
            fixture.addFileToProject("src/$relative", Files.readString(nativeSource.resolve(relative)))
        }
        val source = Files.readString(nativeSource.resolve("handles.zig"))
        val file = fixture.addFileToProject("src/handles.zig", source)
        fixture.configureFromExistingVirtualFile(file.virtualFile)
        block(fixture, source, fixture.doHighlighting(), Path.of(info.stdDirectory).toRealPath())
    }, "zig inferred member navigation")
}

private fun memberRange(source: String, context: String, name: String): TextRange {
    val start = source.indexOf(context)
    check(start >= 0 && context.indexOf(name) >= 0) { "Missing member context: $context / $name" }
    return TextRange.from(start + context.indexOf(name), name.length)
}

private fun target(fixture: CodeInsightTestFixture, source: String, context: String, name: String): PsiElement {
    val offset = memberRange(source, context, name).startOffset
    return targetAt(fixture, offset, "$context / $name")
}

private fun targetAt(fixture: CodeInsightTestFixture, offset: Int, context: String): PsiElement {
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    val destinations = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset)
    check(destinations.size == 1) { "Expected a declaration for $context, got ${destinations.size}" }
    return destinations.single()
}

private fun hasRole(
    source: String,
    highlights: List<HighlightInfo>,
    context: String,
    name: String,
    role: TextAttributesKey,
): Boolean {
    val range = memberRange(source, context, name)
    return highlights.any { it.startOffset == range.startOffset && it.endOffset == range.endOffset && it.forcedTextAttributesKey == role }
}

private fun usageRanges(fixture: CodeInsightTestFixture, source: String, context: String, name: String): List<TextRange> {
    return usageRangesAt(fixture, memberRange(source, context, name).startOffset)
}

private fun usageRangesAt(fixture: CodeInsightTestFixture, offset: Int): List<TextRange> {
    val manager = HighlightManager.getInstance(fixture.project)
    for (highlight in fixture.editor.markupModel.allHighlighters) {
        manager.removeSegmentHighlighter(fixture.editor, highlight)
    }
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
    val highlights = fixture.editor.markupModel.allHighlighters.filter {
        it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES ||
            it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }
    highlights.all { it.getTextAttributes(fixture.editor.colorsScheme)?.backgroundColor != null } shouldBe true
    return highlights.map { TextRange(it.startOffset, it.endOffset) }
}

private fun documentation(fixture: CodeInsightTestFixture, source: String, context: String, name: String): String =
    ReadAction.computeBlocking<String, RuntimeException> {
        val offset = memberRange(source, context, name).startOffset
        val targets = DocumentationTargetProvider.EP_NAME.extensionList.single { it is ZigDocumentationProvider }
            .documentationTargets(fixture.file, offset)
        check(targets.size == 1) { "Expected documentation for $context / $name, got ${targets.size}" }
        (targets.single().computeDocumentation() as DocumentationData).html
    }
