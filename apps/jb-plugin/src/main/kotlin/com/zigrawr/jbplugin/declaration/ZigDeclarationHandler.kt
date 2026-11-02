package com.zigrawr.jbplugin.declaration

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class ZigDeclarationHandler: GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<out PsiElement?>? {
        TODO("Not yet implemented")
    }
}
