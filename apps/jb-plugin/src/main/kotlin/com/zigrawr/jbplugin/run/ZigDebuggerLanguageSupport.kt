package com.zigrawr.jbplugin.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.jetbrains.cidr.execution.debugger.CidrDebuggerLanguageSupport

class ZigDebuggerLanguageSupport : CidrDebuggerLanguageSupport() {
    override fun createEditor(project: Project, profile: RunProfile?): XDebuggerEditorsProvider? =
        if (profile is ZigRunConfiguration) createEditorProvider() else null
}
