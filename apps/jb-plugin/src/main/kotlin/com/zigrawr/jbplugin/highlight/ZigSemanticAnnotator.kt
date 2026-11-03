package com.zigrawr.jbplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.zigrawr.jbplugin.IdeProjectState
import com.zigrawr.jbplugin.declaration.ZigDeclarationResolver
import com.zigrawr.jbplugin.declaration.ZigDeclarations
import com.zigrawr.jbplugin.declaration.ZigResolvedKind
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag

private const val UNKNOWN: Byte = 0
private const val NONE: Byte = 1
private const val FIELD: Byte = 2
private const val FUNCTION_DECLARATION: Byte = 3
private const val STATIC_FUNCTION_CALL: Byte = 4
private const val ERROR_NAME: Byte = 5
private const val KEYWORD: Byte = 6

private val snapshotKey = Key.create<SemanticSnapshot>("zigrawr.semantic.highlighting")

private class SemanticSnapshot(val symbols: ZigDeclarations, val stamp: Long) {
    val roles = ByteArray(symbols.ast.tokenCount())

    init {
        symbols.ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            when (tag) {
                NodeTag.FN_PROTO_SIMPLE, NodeTag.FN_PROTO_MULTI, NodeTag.FN_PROTO_ONE, NodeTag.FN_PROTO ->
                    if (symbols.tag(main + 1) == TokenTag.IDENTIFIER) roles[main + 1] = FUNCTION_DECLARATION

                NodeTag.CONTAINER_FIELD, NodeTag.CONTAINER_FIELD_INIT, NodeTag.CONTAINER_FIELD_ALIGN,
                NodeTag.ENUM_LITERAL -> if (symbols.tag(main) == TokenTag.IDENTIFIER) roles[main] = FIELD

                NodeTag.ERROR_VALUE -> if (symbols.tag(main + 2) == TokenTag.IDENTIFIER) roles[main + 2] = ERROR_NAME

                NodeTag.ERROR_SET_DECL -> {
                    val end = symbols.matching.getOrElse(main + 1) { -1 }
                    for (token in main + 2 until end) {
                        if (token and 255 == 0) ProgressManager.checkCanceled()
                        if (symbols.tag(token) == TokenTag.IDENTIFIER) roles[token] = ERROR_NAME
                    }
                }

                else -> Unit
            }
        }
    }

    fun role(file: PsiFile, token: Int): Byte {
        val cached = roles[token]
        if (cached != UNKNOWN) return cached
        val span = symbols.ast.tokenPositions(token)
        if (span.end - span.start == 4 && symbols.source.regionMatches(span.start, "null", 0, 4)) {
            roles[token] = KEYWORD
            return KEYWORD
        }
        val previous = symbols.tag(token - 1)
        if (previous == TokenTag.PERIOD && symbols.tag(token + 1) == TokenTag.EQUAL &&
            (symbols.tag(token - 2) == TokenTag.L_BRACE || symbols.tag(token - 2) == TokenTag.COMMA)
        ) {
            roles[token] = FIELD
            return FIELD
        }
        val role = if (previous != TokenTag.PERIOD && symbols.tag(token + 1) != TokenTag.L_PAREN) NONE else {
            when (ZigDeclarationResolver.semanticKind(file, symbols, token)) {
                ZigResolvedKind.FIELD -> FIELD
                ZigResolvedKind.STATIC_FUNCTION -> STATIC_FUNCTION_CALL
                ZigResolvedKind.INSTANCE_FUNCTION, null -> NONE
            }
        }
        roles[token] = role
        return role
    }
}

class ZigSemanticAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != ZigTokenTypes.forTag(TokenTag.IDENTIFIER)) return
        val file = element.containingFile ?: return
        val symbols = ZigDeclarations.get(file)
        val token = symbols.tokenAt(element.textOffset)
        if (token < 0 || symbols.tag(token) != TokenTag.IDENTIFIER) return
        val stamp = PsiModificationTracker.getInstance(file.project).modificationCount
        val cache = file.project.service<IdeProjectState>()
        val cached = cache.cached(file, snapshotKey)
        val snapshot = if (cached != null && cached.symbols === symbols && cached.stamp == stamp) cached else {
            SemanticSnapshot(symbols, stamp).also { cache.cache(file, snapshotKey, it) }
        }
        val attributes: TextAttributesKey = when (snapshot.role(file, token)) {
            FIELD -> ZigHighlightingColors.FIELD
            FUNCTION_DECLARATION -> ZigHighlightingColors.FUNCTION_DECLARATION
            STATIC_FUNCTION_CALL -> ZigHighlightingColors.STATIC_FUNCTION_CALL
            ERROR_NAME -> ZigHighlightingColors.ERROR_NAME
            KEYWORD -> DefaultLanguageHighlighterColors.KEYWORD
            else -> return
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(attributes).create()
    }
}
