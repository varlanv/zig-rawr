package com.zigrawr.jbplugin.indent

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesNoCommitDelegate
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.TokenTag

class ZigEnterBetweenBracesDelegate : EnterBetweenBracesNoCommitDelegate() {

    override fun isCommentType(type: IElementType): Boolean =
        type == ZigTokenTypes.LINE_COMMENT ||
            type == ZigTokenTypes.forTag(TokenTag.DOC_COMMENT) ||
            type == ZigTokenTypes.forTag(TokenTag.CONTAINER_DOC_COMMENT)
}
