package com.zigrawr.jbplugin.declaration

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.zigrawr.jbplugin.file.ZigFileType

class ZigDeclarationHandler : GotoDeclarationHandler, DumbAware {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<out PsiElement?>? {
        val file = sourceElement?.containingFile ?: return null
        if (file.fileType != ZigFileType) return null
        val target = ZigDeclarationResolver.resolve(file, offset) ?: return null
        return arrayOf(target)
    }
}
