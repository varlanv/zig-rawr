package com.zigrawr.jbplugin.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

internal object ZigHighlightingColors {
    val FIELD: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ZIGRAWR_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD,
    )
    val FUNCTION_DECLARATION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ZIGRAWR_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
    )
    val STATIC_FUNCTION_CALL: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ZIGRAWR_STATIC_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL,
    )
    val BUILTIN_FUNCTION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ZIGRAWR_BUILTIN_FUNCTION", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL,
    )
    val ERROR_NAME: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ZIGRAWR_ERROR_NAME", DefaultLanguageHighlighterColors.CONSTANT,
    )
}
