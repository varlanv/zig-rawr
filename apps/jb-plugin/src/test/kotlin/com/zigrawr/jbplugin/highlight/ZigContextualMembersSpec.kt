package com.zigrawr.jbplugin.highlight

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.documentation.ZigDocumentationProvider
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.zig.foreign.model.TokenTag
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ZigContextualMembersSpec : IdeaHarness({
    should("color null literals with the keyword key used for return") {
        runSelectionFixture({ fixture ->
            val source = """
                fn nullable(value: ?usize) ?usize {
                    if (value == null) return null;
                    _ = "null";
                    const @"null" = 1;
                    _ = @"null";
                    return value;
                }
            """.trimIndent()
            fixture.configureByText("null-keyword.zig", source)
            val keyword = ZigSyntaxHighlighter().getTokenHighlights(ZigTokenTypes.forTag(TokenTag.KEYWORD_RETURN)).single()
            keyword shouldBe DefaultLanguageHighlighterColors.KEYWORD
            val highlights = fixture.doHighlighting()
            for (context in listOf("value == null", "return null")) {
                val range = memberRange(source, context, "null")
                highlights.any {
                    it.startOffset == range.startOffset && it.endOffset == range.endOffset && it.forcedTextAttributesKey == keyword
                } shouldBe true
            }
            val string = memberRange(source, "\"null\"", "null")
            highlights.any {
                it.startOffset == string.startOffset && it.endOffset == string.endOffset && it.forcedTextAttributesKey == keyword
            } shouldBe false
            val quoted = memberRange(source, "_ = @\"null\"", "@\"null\"")
            highlights.any {
                it.startOffset == quoted.startOffset && it.endOffset == quoted.endOffset && it.forcedTextAttributesKey == keyword
            } shouldBe false
        }, "zig null keyword coloring")
    }

    should("resolve anonymous initializer fields from return and assignment context") {
        runSelectionFixture({ fixture ->
            val source = """
                const Layout = struct { tokenTags: u32, totalSize: usize };
                const Other = struct { tokenTags: bool };
                fn make() Layout { return .{ .tokenTags = 1, .totalSize = 2 }; }
                fn update(layout: *Layout) void {
                    layout.* = .{ .tokenTags = 3, .totalSize = 4 };
                    _ = layout.tokenTags;
                    const unrelated: Other = .{ .tokenTags = true };
                    _ = unrelated;
                }
            """.trimIndent()
            fixture.configureByText("initializers.zig", source)
            val declaration = source.indexOf("tokenTags: u32")
            val expected = listOf(
                TextRange.from(declaration, "tokenTags".length),
                memberRange(source, ".tokenTags = 1", "tokenTags"),
                memberRange(source, ".tokenTags = 3", "tokenTags"),
                memberRange(source, "layout.tokenTags", "tokenTags"),
            )
            for (context in listOf(".tokenTags = 1", ".tokenTags = 3")) {
                declarationOffset(fixture, source, context, "tokenTags") shouldBe declaration
                documentation(fixture, source, context, "tokenTags") shouldContain "tokenTags: u32"
                usages(fixture, source, context, "tokenTags") shouldBe expected
            }
            declarationOffset(fixture, source, ".totalSize = 2", "totalSize") shouldBe source.indexOf("totalSize: usize")
            declarationOffset(fixture, source, ".tokenTags = true", "tokenTags") shouldBe source.indexOf("tokenTags: bool")
        }, "zig initializer member context")
    }

    should("resolve switch enum members using the switched expression type") {
        runSelectionFixture({ fixture ->
            val source = """
                const Kind = enum { fn_proto, fn_proto_one, fn_proto_multi };
                const Other = enum { fn_proto, fn_proto_one };
                const Node = struct { tag: Kind };
                fn inspect(node: Node) void {
                    switch (node.tag) {
                        .fn_proto, .fn_proto_one => {},
                        .fn_proto_multi => {},
                    }
                    const selected: Kind = .fn_proto;
                    _ = selected;
                }
                fn different(value: Other) void { switch (value) { .fn_proto => {}, else => {} } }
            """.trimIndent()
            fixture.configureByText("switch.zig", source)
            val declaration = source.indexOf("fn_proto,")
            val expected = listOf(
                TextRange.from(declaration, "fn_proto".length),
                memberRange(source, ".fn_proto, .fn_proto_one", "fn_proto"),
                memberRange(source, "Kind = .fn_proto", "fn_proto"),
            )
            for (name in listOf("fn_proto", "fn_proto_one", "fn_proto_multi")) {
                val context = when (name) {
                    "fn_proto" -> ".fn_proto, .fn_proto_one"
                    "fn_proto_one" -> ".fn_proto_one =>"
                    else -> ".fn_proto_multi =>"
                }
                declarationOffset(fixture, source, context, name) shouldBe source.indexOf(name)
                documentation(fixture, source, context, name) shouldContain name
            }
            usages(fixture, source, ".fn_proto, .fn_proto_one", "fn_proto") shouldBe expected
            declarationOffset(fixture, source, "switch (value) { .fn_proto", "fn_proto") shouldBe
                source.indexOf("fn_proto,", source.indexOf("const Other"))
        }, "zig switch enum context")
    }

    should("propagate comparison and nested result types without replacing a nearer call argument type") {
        runSelectionFixture({ fixture ->
            val source = """
                const Mode = enum { zig, zon };
                const Tag = enum { root, other };
                const Node = struct { mode: Mode };
                const Payload = struct { first: u32, second: u32 };
                const Other = struct { first: bool, second: bool };
                fn take(value: Other) Payload { _ = value; return .{ .first = 1, .second = 2 }; }
                fn make(node: Node, tag: Tag, flag: bool) Payload {
                    _ = node.mode != .zon;
                    _ = .zig == node.mode;
                    const local: Payload = if (flag) .{ .first = 21, .second = 22 } else .{ .first = 23, .second = 24 };
                    _ = local;
                    return switch (tag) {
                        .root => if (node.mode == .zig) .{ .first = 11, .second = 12 } else .{ .first = 13, .second = 14 },
                        .other => take(.{ .first = true, .second = false }),
                    };
                }
            """.trimIndent()
            fixture.configureByText("nested-results.zig", source)
            for ((context, name) in listOf(
                "node.mode != .zon" to "zon",
                ".zig == node.mode" to "zig",
                "node.mode == .zig" to "zig",
            )) {
                declarationOffset(fixture, source, context, name) shouldBe source.indexOf(if (name == "zig") "zig, zon" else "zon }")
                documentation(fixture, source, context, name) shouldContain name
                usages(fixture, source, context, name).contains(memberRange(source, context, name)) shouldBe true
            }
            for ((name, values) in listOf("first" to listOf(11, 13, 21, 23), "second" to listOf(12, 14, 22, 24))) {
                for (value in values) {
                    val context = ".$name = $value"
                    declarationOffset(fixture, source, context, name) shouldBe source.indexOf("$name: u32")
                    documentation(fixture, source, context, name) shouldContain "$name: u32"
                    val occurrences = usages(fixture, source, context, name)
                    occurrences.contains(memberRange(source, context, name)) shouldBe true
                    occurrences.contains(memberRange(source, ".$name = ${if (name == "first") "true" else "false"}", name)) shouldBe false
                }
            }
            for ((name, value) in listOf("first" to "true", "second" to "false")) {
                val context = ".$name = $value"
                declarationOffset(fixture, source, context, name) shouldBe source.indexOf("$name: bool")
                documentation(fixture, source, context, name) shouldContain "$name: bool"
                usages(fixture, source, context, name).contains(memberRange(source, context, name)) shouldBe true
            }
        }, "zig comparison and nested result context")
    }

    should("keep built-in property occurrences specific to their receiver chain") {
        runSelectionFixture({ fixture ->
            val source = """
                const Storage = struct { tokens: []u8, nodes: []u8 };
                fn inspect(ast: *Storage, other: *Storage) void {
                    _ = ast.tokens.len + ast.nodes.len;
                    _ = ast.tokens.len;
                    _ = other.tokens.len;
                }
            """.trimIndent()
            fixture.configureByText("property-receivers.zig", source)
            val first = memberRange(source, "ast.tokens.len +", "len")
            val repeated = memberRange(source, "ast.tokens.len;", "len")
            usages(fixture, source, "ast.tokens.len +", "len") shouldBe listOf(first, repeated)
            usages(fixture, source, "ast.nodes.len", "len") shouldBe listOf(memberRange(source, "ast.nodes.len", "len"))
            usages(fixture, source, "other.tokens.len", "len") shouldBe listOf(memberRange(source, "other.tokens.len", "len"))
        }, "zig property receiver identity")
    }

    should("highlight error-set declarations and matching error values within their function") {
        runSelectionFixture({ fixture ->
            val source = """
                fn reserve(value: usize) error{TooLarge}!u32 {
                    if (value > 10) return error.TooLarge;
                    if (value == 0) return error.TooLarge;
                    return 1;
                }
                fn unrelated() error{TooLarge}!u32 { return error.TooLarge; }
            """.trimIndent()
            fixture.configureByText("error-occurrences.zig", source)
            val signature = TextRange.from(source.indexOf("TooLarge"), "TooLarge".length)
            val first = memberRange(source, "if (value > 10) return error.TooLarge", "TooLarge")
            val second = memberRange(source, "if (value == 0) return error.TooLarge", "TooLarge")
            val expected = listOf(signature, first, second)
            usages(fixture, source, "error{TooLarge}!", "TooLarge") shouldBe expected
            usages(fixture, source, "if (value > 10) return error.TooLarge", "TooLarge") shouldBe expected
            usages(fixture, source, "if (value == 0) return error.TooLarge", "TooLarge") shouldBe expected
        }, "zig error occurrence scope")
    }
})

private fun memberRange(source: String, context: String, name: String): TextRange {
    val start = source.indexOf(context)
    check(start >= 0 && context.indexOf(name) >= 0) { "Missing context: $context / $name" }
    return TextRange.from(start + context.indexOf(name), name.length)
}

private fun declarationOffset(fixture: CodeInsightTestFixture, source: String, context: String, name: String): Int {
    val offset = memberRange(source, context, name).startOffset
    fixture.editor.caretModel.moveToOffset(offset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    val destinations = GotoDeclarationAction.findAllTargetElements(fixture.project, fixture.editor, offset)
    check(destinations.size == 1) { "Expected one declaration for $context / $name, got ${destinations.size}" }
    destinations.single().containingFile shouldBe fixture.file
    return destinations.single().textOffset
}

private fun documentation(fixture: CodeInsightTestFixture, source: String, context: String, name: String): String =
    ReadAction.computeBlocking<String, RuntimeException> {
        val offset = memberRange(source, context, name).startOffset
        val targets = DocumentationTargetProvider.EP_NAME.extensionList.single { it is ZigDocumentationProvider }
            .documentationTargets(fixture.file, offset)
        check(targets.size == 1) { "Expected documentation for $context / $name, got ${targets.size}" }
        (targets.single().computeDocumentation() as DocumentationData).html
    }

private fun usages(fixture: CodeInsightTestFixture, source: String, context: String, name: String): List<TextRange> {
    val manager = HighlightManager.getInstance(fixture.project)
    for (highlight in fixture.editor.markupModel.allHighlighters) manager.removeSegmentHighlighter(fixture.editor, highlight)
    fixture.editor.caretModel.moveToOffset(memberRange(source, context, name).startOffset)
    PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    HighlightUsagesHandler.invoke(fixture.project, fixture.editor, fixture.file)
    return fixture.editor.markupModel.allHighlighters.filter {
        it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES ||
            it.textAttributesKey == EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
    }.map { TextRange(it.startOffset, it.endOffset) }.sortedBy { it.startOffset }
}
