package com.zigrawr.jbplugin.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.zigrawr.jbplugin.declaration.ZigDeclarations
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag

private const val MAX_SIGNATURE_CHARS = 4096

internal object ZigDocumentationRenderer {
    fun render(declaration: PsiElement): String? {
        val symbols = ZigDeclarations.get(declaration.containingFile)
        val token = symbols.tokenAt(declaration.textOffset)
        if (symbols.tag(token) != TokenTag.IDENTIFIER) return null
        val node = findNode(symbols, token)
        val start: Int
        var end: Int
        var suffix = ""
        if (node >= 0) {
            val span = symbols.ast.nodePositions(node)
            start = span.start
            end = span.end
            if (symbols.tag(token - 1) == TokenTag.KEYWORD_CONST || symbols.tag(token - 1) == TokenTag.KEYWORD_VAR) {
                val name = symbols.name(token) ?: return null
                val record = symbols.find(name, token)
                var value = if (record < 0) -1 else symbols.valueToken(record)
                while (symbols.tag(value) == TokenTag.KEYWORD_PACKED || symbols.tag(value) == TokenTag.KEYWORD_EXTERN) value++
                when (symbols.tag(value)) {
                    TokenTag.KEYWORD_STRUCT, TokenTag.KEYWORD_UNION, TokenTag.KEYWORD_ENUM, TokenTag.KEYWORD_OPAQUE -> {
                        val brace = symbols.containerBrace(value)
                        if (brace >= 0) {
                            end = symbols.ast.tokenPositions(brace).end
                            suffix = " … };"
                        }
                    }
                    else -> {
                        val next = symbols.tokenAt(end - 1) + 1
                        if (symbols.tag(next) == TokenTag.SEMICOLON) end = symbols.ast.tokenPositions(next).end
                    }
                }
            }
        } else {
            if (symbols.tag(token + 1) != TokenTag.COLON) return null
            start = symbols.ast.tokenPositions(token).start
            var index = token + 2
            while (index < symbols.ast.tokenCount() && symbols.tag(index) != TokenTag.COMMA &&
                symbols.tag(index) != TokenTag.R_PAREN && symbols.tag(index) != TokenTag.EOF) {
                if (index and 255 == 0) ProgressManager.checkCanceled()
                index = if (symbols.matching[index] > index) symbols.matching[index] + 1 else index + 1
            }
            end = symbols.ast.tokenPositions(index - 1).end
        }
        if (end - start > MAX_SIGNATURE_CHARS) {
            end = start + MAX_SIGNATURE_CHARS
            if (symbols.source[end - 1].isHighSurrogate()) end--
            suffix = " …"
        }
        val html = StringBuilder()
        html.append(DocumentationMarkup.DEFINITION_START)
        html.append(StringUtil.escapeXmlEntities(symbols.source.subSequence(start, end).toString()))
        html.append(suffix)
        html.append(DocumentationMarkup.DEFINITION_END)
        appendComments(html, symbols, start)
        return html.toString()
    }

    private fun findNode(symbols: ZigDeclarations, token: Int): Int {
        symbols.ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            when (tag) {
                NodeTag.GLOBAL_VAR_DECL, NodeTag.LOCAL_VAR_DECL, NodeTag.SIMPLE_VAR_DECL, NodeTag.ALIGNED_VAR_DECL,
                NodeTag.FN_PROTO_SIMPLE, NodeTag.FN_PROTO_MULTI, NodeTag.FN_PROTO_ONE, NodeTag.FN_PROTO ->
                    if (main + 1 == token) return node
                NodeTag.CONTAINER_FIELD, NodeTag.CONTAINER_FIELD_INIT, NodeTag.CONTAINER_FIELD_ALIGN ->
                    if (main == token) return node
                else -> Unit
            }
        }
        return -1
    }

    private fun appendComments(html: StringBuilder, symbols: ZigDeclarations, declarationStart: Int) {
        val declarationToken = symbols.tokenAt(declarationStart)
        var first = declarationToken
        var nextStart = declarationStart
        while (first > 0 && symbols.tag(first - 1) == TokenTag.DOC_COMMENT) {
            val span = symbols.ast.tokenPositions(first - 1)
            if (!adjacentLines(symbols.source, span.end, nextStart)) break
            first--
            nextStart = span.start
        }
        if (first == declarationToken) return
        html.append(DocumentationMarkup.CONTENT_START)
        for (token in first until declarationToken) {
            if (token and 255 == 0) ProgressManager.checkCanceled()
            val span = symbols.ast.tokenPositions(token)
            var start = span.start + 3
            if (start < span.end && symbols.source[start] == ' ') start++
            if (token != first) html.append("<br>")
            html.append(StringUtil.escapeXmlEntities(symbols.source.subSequence(start, span.end).toString()))
        }
        html.append(DocumentationMarkup.CONTENT_END)
    }

    private fun adjacentLines(source: CharSequence, start: Int, end: Int): Boolean {
        var newlines = 0
        for (index in start until end) {
            when (source[index]) {
                '\n' -> if (++newlines > 1) return false
                ' ', '\t', '\r' -> Unit
                else -> return false
            }
        }
        return newlines == 1
    }
}
