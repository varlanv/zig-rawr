package com.zigrawr.jbplugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor

class ZigLspClientDescriptor(project: Project) :
    ProjectWideLspClientDescriptor(project, "ZLS") {

    override fun isSupportedFile(file: VirtualFile): Boolean =
        file.fileType === ZigFileType || file.fileType === ZonFileType

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine("zls")
}
