package com.zigrawr.jbplugin.lsp

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.jbplugin.file.ZonFileType

class ZigLspIntegrationProvider : LspIntegrationProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.fileType === ZigFileType || file.fileType === ZonFileType) {
            // todo revisit this - test what if LSP dead; build for resilience
            clientStarter.ensureClientStarted(ZigLspClientDescriptor(project))
        }
    }
}
