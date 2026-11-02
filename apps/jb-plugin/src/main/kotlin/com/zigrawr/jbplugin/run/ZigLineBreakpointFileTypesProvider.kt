package com.zigrawr.jbplugin.run

import com.intellij.openapi.fileTypes.FileType
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointFileTypesProvider
import com.zigrawr.jbplugin.file.ZigFileType

class ZigLineBreakpointFileTypesProvider : CidrLineBreakpointFileTypesProvider {
    private val types: Set<FileType> = setOf(ZigFileType)

    override fun getFileTypes(): Set<FileType> = types
}
