package com.zigrawr.jbplugin.highlight

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import com.zigrawr.jbplugin.declaration.ZigDeclarationResolver
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.TokenTag

class ZigHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactoryBase(), DumbAware {
    override fun createHighlightUsagesHandler(
        editor: Editor,
        psiFile: PsiFile,
        target: PsiElement,
    ): HighlightUsagesHandlerBase<*>? {
        if (psiFile.fileType != ZigFileType) return null
        val type = target.node?.elementType
        if (type != ZigTokenTypes.forTag(TokenTag.IDENTIFIER) && type != ZigTokenTypes.forTag(TokenTag.BUILTIN)) return null
        return ZigHighlightUsagesHandler(editor, psiFile, target)
    }
}

private class ZigHighlightUsagesHandler(
    editor: Editor,
    file: PsiFile,
    private val target: PsiElement,
) : HighlightUsagesHandlerBase<PsiElement>(editor, file), DumbAware {
    override fun getTargets(): List<PsiElement> = listOf(target)

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        myReadUsages.clear()
        myWriteUsages.clear()
        val selected = targets.singleOrNull() ?: return
        if (!selected.isValid || selected.containingFile != myFile) return
        myReadUsages.addAll(ZigDeclarationResolver.occurrences(myFile, selected.textRange.startOffset))
    }
}
