package com.zigrawr.jbplugin.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.lexer.ZigLexer
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.TokenTag

private val keywordAttributes = arrayOf(DefaultLanguageHighlighterColors.KEYWORD)
private val stringAttributes = arrayOf(DefaultLanguageHighlighterColors.STRING)
private val numberAttributes = arrayOf(DefaultLanguageHighlighterColors.NUMBER)
private val lineCommentAttributes = arrayOf(DefaultLanguageHighlighterColors.LINE_COMMENT)
private val docCommentAttributes = arrayOf(DefaultLanguageHighlighterColors.DOC_COMMENT)
private val builtinAttributes = arrayOf(DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)
private val operatorAttributes = arrayOf(DefaultLanguageHighlighterColors.OPERATION_SIGN)
private val parenthesesAttributes = arrayOf(DefaultLanguageHighlighterColors.PARENTHESES)
private val bracketsAttributes = arrayOf(DefaultLanguageHighlighterColors.BRACKETS)
private val bracesAttributes = arrayOf(DefaultLanguageHighlighterColors.BRACES)
private val commaAttributes = arrayOf(DefaultLanguageHighlighterColors.COMMA)
private val dotAttributes = arrayOf(DefaultLanguageHighlighterColors.DOT)
private val semicolonAttributes = arrayOf(DefaultLanguageHighlighterColors.SEMICOLON)
private val badCharacterAttributes = arrayOf(HighlighterColors.BAD_CHARACTER)
private val fieldAttributes = arrayOf(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
private val functionCallAttributes = arrayOf(DefaultLanguageHighlighterColors.FUNCTION_CALL)

private val tokenHighlights = buildMap<IElementType, Array<TextAttributesKey>> {
    for (tag in TokenTag.entries) {
        val attributes = if (tag.name.startsWith("KEYWORD_")) keywordAttributes else when (tag) {
            TokenTag.IDENTIFIER, TokenTag.EOF -> TextAttributesKey.EMPTY_ARRAY
            TokenTag.INVALID -> badCharacterAttributes
            TokenTag.STRING_LITERAL, TokenTag.MULTILINE_STRING_LITERAL_LINE, TokenTag.CHAR_LITERAL -> stringAttributes
            TokenTag.NUMBER_LITERAL -> numberAttributes
            TokenTag.DOC_COMMENT, TokenTag.CONTAINER_DOC_COMMENT -> docCommentAttributes
            TokenTag.BUILTIN -> builtinAttributes
            TokenTag.L_PAREN, TokenTag.R_PAREN -> parenthesesAttributes
            TokenTag.L_BRACKET, TokenTag.R_BRACKET -> bracketsAttributes
            TokenTag.L_BRACE, TokenTag.R_BRACE -> bracesAttributes
            TokenTag.COMMA -> commaAttributes
            TokenTag.PERIOD -> dotAttributes
            TokenTag.SEMICOLON -> semicolonAttributes
            else -> operatorAttributes
        }
        put(ZigTokenTypes.forTag(tag), attributes)
    }
    put(ZigTokenTypes.LINE_COMMENT, lineCommentAttributes)
    put(ZigTokenTypes.FIELD_NAME, fieldAttributes)
    put(ZigTokenTypes.FUNCTION_CALL, functionCallAttributes)
    put(TokenType.BAD_CHARACTER, badCharacterAttributes)
}

class ZigSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = ZigLexer(service<IdeProcessState>())

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        tokenHighlights[tokenType] ?: TextAttributesKey.EMPTY_ARRAY
}
