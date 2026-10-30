package com.zigrawr.jbplugin.selection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.jbplugin.file.ZonFileType
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.Ast

private data class SelectionSnapshot(val stamp: Long, val mode: ParseMode, val ranges: ZigSelectionRanges)

private val selectionSnapshotKey = Key.create<SelectionSnapshot>("zigrawr.selection.snapshot")

class ZigSelectionHandler : ExtendWordSelectionHandler {

    // SelectWordUtil visits leaves and their parents, but excludes the PsiFile itself.
    override fun canSelect(e: PsiElement): Boolean =
        e.firstChild == null && (e.containingFile?.fileType == ZigFileType || e.containingFile?.fileType == ZonFileType)

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
        val document = editor.document
        val mode = if (e.containingFile.fileType == ZonFileType) ParseMode.Zon else ParseMode.Zig
        val stamp = document.modificationStamp
        val cached = document.getUserData(selectionSnapshotKey)
        val ranges = if (cached != null && cached.stamp == stamp && cached.mode == mode) {
            cached.ranges
        } else {
            val text = document.immutableCharSequence
            val ast = Ast(IdeProcessState.getInstance().bindings.ast.parseChars(text, mode))
            val r = ZigSelectionRanges.build(ast, text)
            if (document.modificationStamp == stamp) document.putUserData(
                selectionSnapshotKey,
                SelectionSnapshot(stamp, mode, r)
            )
            r
        }
        return if (document.modificationStamp == stamp) ranges.at(cursorOffset) else emptyList()
    }
}
