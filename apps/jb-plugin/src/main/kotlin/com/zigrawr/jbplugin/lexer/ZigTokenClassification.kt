package com.zigrawr.jbplugin.lexer

import com.intellij.psi.tree.IElementType
import com.zigrawr.zig.foreign.model.Ast
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag

internal fun Ast.classifyTokens(): Array<IElementType?> {
    val types = arrayOfNulls<IElementType>(tokenCount())
    for (index in 1 until tokenCount() - 1) {
        if (tokenTag(index) == TokenTag.IDENTIFIER &&
            tokenTag(index - 1) == TokenTag.PERIOD && tokenTag(index + 1) == TokenTag.EQUAL
        ) {
            types[index] = ZigTokenTypes.FIELD_NAME
        }
    }
    forEachNode { _, tag, mainToken, payloadWord0, payloadWord1 ->
        when (tag) {
            NodeTag.FIELD_ACCESS -> {
                val nameToken = payloadWord1
                if (nameToken in types.indices && tokenTag(nameToken) == TokenTag.IDENTIFIER &&
                    types[nameToken] != ZigTokenTypes.FUNCTION_CALL
                ) {
                    types[nameToken] = ZigTokenTypes.FIELD_NAME
                }
            }

            NodeTag.CONTAINER_FIELD_INIT, NodeTag.CONTAINER_FIELD_ALIGN, NodeTag.CONTAINER_FIELD -> {
                if (tokenTag(mainToken) == TokenTag.IDENTIFIER &&
                    mainToken + 1 < tokenCount() && tokenTag(mainToken + 1) == TokenTag.COLON
                ) {
                    types[mainToken] = ZigTokenTypes.FIELD_NAME
                }
            }

            NodeTag.CALL_ONE, NodeTag.CALL_ONE_COMMA, NodeTag.CALL, NodeTag.CALL_COMMA -> {
                val callee = payloadWord0
                val nameToken = when (nodeTag(callee)) {
                    NodeTag.IDENTIFIER -> nodeMainToken(callee)
                    NodeTag.FIELD_ACCESS -> nodeMainToken(callee) + 1
                    else -> -1
                }
                if (nameToken in types.indices && tokenTag(nameToken) == TokenTag.IDENTIFIER) {
                    types[nameToken] = ZigTokenTypes.FUNCTION_CALL
                }
            }

            else -> Unit
        }
    }
    return types
}
