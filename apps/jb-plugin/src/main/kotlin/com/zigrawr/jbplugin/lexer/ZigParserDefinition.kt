package com.zigrawr.jbplugin.lexer

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.file.ZigLanguage
import com.zigrawr.jbplugin.file.ZigPsiFile
import com.zigrawr.zig.foreign.model.TokenTag

private val FILE_NODE_TYPE = IFileElementType(ZigLanguage)
private val COMMENT_TOKENS = TokenSet.create(
    ZigTokenTypes.LINE_COMMENT,
    ZigTokenTypes.forTag(TokenTag.DOC_COMMENT),
    ZigTokenTypes.forTag(TokenTag.CONTAINER_DOC_COMMENT),
)
private val STRING_LITERAL_TOKENS = TokenSet.create(
    ZigTokenTypes.forTag(TokenTag.STRING_LITERAL),
    ZigTokenTypes.forTag(TokenTag.MULTILINE_STRING_LITERAL_LINE),
    ZigTokenTypes.forTag(TokenTag.CHAR_LITERAL),
)

class ZigParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = ZigLexer(IdeProcessState.getInstance())

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        file.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE_NODE_TYPE

    override fun getCommentTokens(): TokenSet = COMMENT_TOKENS

    override fun getStringLiteralElements(): TokenSet = STRING_LITERAL_TOKENS

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ZigPsiFile(viewProvider)
}
