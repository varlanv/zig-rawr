package com.zigrawr.jbplugin.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.zig.foreign.model.TokenTag

class ZigRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        if (element.containingFile?.fileType != ZigFileType || element.firstChild != null) return null
        val type = element.node?.elementType
        if (type != ZigTokenTypes.forTag(TokenTag.IDENTIFIER) && type != ZigTokenTypes.forTag(TokenTag.KEYWORD_TEST)) return null
        val target = ZigRunTargets.markerAt(element) ?: return null
        val actions = if (ZigRunTargets.fileTests(element.containingFile) == null) ExecutorAction.getActions(0)
            else ExecutorAction.getActions(0) + ExecutorAction.getActions(1)
        return Info(AllIcons.Actions.Execute, actions) { target.displayName }
    }
}
