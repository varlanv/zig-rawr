package com.zigrawr.jbplugin.run

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.jetbrains.cidr.execution.debugger.CidrDebuggerEditorsExtension
import com.zigrawr.jbplugin.file.ZigFileType

class ZigDebuggerEditorsExtension : CidrDebuggerEditorsExtension {
    override fun createDocument(
        project: Project,
        text: String,
        sourcePosition: XSourcePosition,
        mode: EvaluationMode,
    ): Document {
        val file = LightVirtualFile("debug-expression.zig", ZigFileType, text)
        return requireNotNull(FileDocumentManager.getInstance().getDocument(file))
    }
}
