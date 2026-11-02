package com.zigrawr.jbplugin.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile

class ZigDocumentationProvider: DocumentationTargetProvider {

    override fun documentationTargets(
        file: PsiFile,
        offset: Int
    ): List<DocumentationTarget> {
        TODO("Not yet implemented")
    }
}
