package com.zigrawr.jbplugin.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.file.ZigLanguage
import com.zigrawr.zig.foreign.model.TokenTag

object ZigTokenTypes {

    val LINE_COMMENT = IElementType("LINE_COMMENT", ZigLanguage)
    val FIELD_NAME = IElementType("FIELD_NAME", ZigLanguage)
    val FUNCTION_CALL = IElementType("FUNCTION_CALL", ZigLanguage)

    private val nativeTypes = TokenTag.entries.map { tag ->
        if (tag == TokenTag.INVALID) TokenType.BAD_CHARACTER else IElementType(tag.name, ZigLanguage)
    }.toTypedArray()

    fun forTag(tag: TokenTag): IElementType = nativeTypes[tag.ordinal]
}
