package com.zigrawr.jbplugin.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.zigrawr.jbplugin.declaration.ZigDeclarations
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag

internal data class ZigRunTarget(
    val kind: ZigRunKind,
    val offset: Int,
    val start: Int,
    val end: Int,
    val testName: String,
    val displayName: String,
)

private data class RunTargetsSnapshot(
    val stamp: Long,
    val fileName: String,
    val targets: List<ZigRunTarget>,
    val hasTests: Boolean
)

private val targetsKey = Key.create<RunTargetsSnapshot>("zigrawr.run.targets")

internal object ZigRunTargets {
    fun checkTestSelection(file: PsiFile, kind: ZigRunKind, name: String) {
        val targets = get(file).targets
        if (targets.none { it.kind == kind && it.testName == name }) {
            throw RuntimeConfigurationError("Test target '$name' no longer exists. Recreate its run configuration from the gutter.")
        }
        val filter = if (kind == ZigRunKind.TEST_GROUP) name.trimEnd('.') + "." else name
        for (target in targets) {
            if (target.kind != ZigRunKind.TEST || !target.testName.contains(filter)) continue
            val intended =
                if (kind == ZigRunKind.TEST_GROUP) target.testName.startsWith(filter) else target.testName == name
            if (!intended) {
                throw RuntimeConfigurationError(
                    "Zig's `--test-filter [text]` currently cannot distinguish '$name' from '${target.testName}'. " +
                        "As a workaround, rename the test or group to make its name unique, then select it again from the gutter.",
                )
            }
        }
    }

    fun at(element: PsiElement): ZigRunTarget? {
        val file = element.containingFile ?: return null
        if (file.fileType != ZigFileType) return null
        val targets = get(file).targets
        if (element is PsiFile) return targets.firstOrNull { it.kind == ZigRunKind.MAIN }
        val offset = element.textOffset
        var selected: ZigRunTarget? = null
        for (target in targets) {
            if (offset >= target.start && offset < target.end &&
                (selected == null || target.end - target.start < selected.end - selected.start)
            ) selected = target
        }
        return selected
    }

    fun markerAt(element: PsiElement): ZigRunTarget? {
        val file = element.containingFile ?: return null
        if (file.fileType != ZigFileType) return null
        val targets = get(file).targets
        val offset = element.textOffset
        var low = 0
        var high = targets.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (targets[middle].offset < offset) low = middle + 1 else high = middle
        }
        return targets.getOrNull(low)?.takeIf { it.offset == offset }
    }

    fun fileTests(file: PsiFile): ZigRunTarget? {
        if (file.fileType != ZigFileType || !get(file).hasTests) return null
        return ZigRunTarget(ZigRunKind.TEST_FILE, 0, 0, file.textLength, "", "All tests in ${file.name}")
    }

    private fun get(file: PsiFile): RunTargetsSnapshot {
        val stamp =
            PsiDocumentManager.getInstance(file.project).getDocument(file)?.modificationStamp ?: file.modificationStamp
        val cached = file.getUserData(targetsKey)
        if (cached != null && cached.stamp == stamp && cached.fileName == file.name) return cached
        val symbols = ZigDeclarations.get(file)
        val targets = ArrayList<ZigRunTarget>()
        val tests = ArrayList<ZigRunTarget>()
        val rootName = file.name.removeSuffix(".zig")
        val anonymous = HashMap<String, Int>()
        val namespaces = HashMap<Int, String?>()
        fun namespace(token: Int): String? {
            val owner = symbols.enclosingNamespace(token)
            if (namespaces.containsKey(owner)) return namespaces[owner]
            return symbols.namespacePath(token).also { namespaces[owner] = it }
        }
        symbols.ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            if (tag == NodeTag.FN_DECL && symbols.name(main + 1) == "main" && symbols.enclosingNamespace(main) < 0) {
                val span = symbols.ast.nodePositions(node)
                targets.add(
                    ZigRunTarget(
                        ZigRunKind.MAIN, symbols.ast.tokenPositions(main + 1).start,
                        span.start, span.end, "", "${file.name}: main"
                    )
                )
            }
            if (tag == NodeTag.TEST_DECL) {
                val path = namespace(main) ?: return@forEachNode
                val prefix = if (path.isEmpty()) rootName else "$rootName.$path"
                val name = when (symbols.tag(main + 1)) {
                    TokenTag.STRING_LITERAL -> "test.${symbols.string(main + 1) ?: return@forEachNode}"
                    TokenTag.IDENTIFIER -> "decltest.${symbols.name(main + 1) ?: return@forEachNode}"
                    else -> {
                        val ordinal = anonymous[prefix] ?: 0
                        anonymous[prefix] = ordinal + 1
                        "test_$ordinal"
                    }
                }
                val span = symbols.ast.nodePositions(node)
                tests.add(
                    ZigRunTarget(
                        ZigRunKind.TEST, symbols.ast.tokenPositions(main).start,
                        span.start, span.end, "$prefix.$name", "${file.name}: $name"
                    )
                )
            }
        }
        tests.sortBy { it.offset }
        targets.addAll(tests)
        symbols.ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            if (tag != NodeTag.SIMPLE_VAR_DECL && tag != NodeTag.ALIGNED_VAR_DECL &&
                tag != NodeTag.GLOBAL_VAR_DECL && tag != NodeTag.LOCAL_VAR_DECL
            ) return@forEachNode
            val name = symbols.name(main + 1) ?: return@forEachNode
            val declaration = symbols.find(name, main + 1)
            if (declaration < 0) return@forEachNode
            var value = symbols.valueToken(declaration)
            while (symbols.tag(value) == TokenTag.KEYWORD_PACKED || symbols.tag(value) == TokenTag.KEYWORD_EXTERN) value++
            when (symbols.tag(value)) {
                TokenTag.KEYWORD_STRUCT, TokenTag.KEYWORD_UNION, TokenTag.KEYWORD_ENUM, TokenTag.KEYWORD_OPAQUE -> Unit
                else -> return@forEachNode
            }
            val brace = symbols.containerBrace(value)
            if (brace < 0) return@forEachNode
            val span = symbols.ast.nodePositions(node)
            val bodyStart = symbols.ast.tokenPositions(brace).end
            var low = 0
            var high = tests.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (tests[middle].offset < bodyStart) low = middle + 1 else high = middle
            }
            if (low >= tests.size || tests[low].offset >= span.end) return@forEachNode
            val path = namespace(brace + 1) ?: return@forEachNode
            targets.add(
                ZigRunTarget(
                    ZigRunKind.TEST_GROUP, symbols.ast.tokenPositions(main + 1).start,
                    span.start, span.end, "$rootName.$path", "${file.name}: $path tests"
                )
            )
        }
        targets.sortBy { it.offset }
        return RunTargetsSnapshot(stamp, file.name, targets, tests.isNotEmpty()).also {
            file.putUserData(
                targetsKey,
                it
            )
        }
    }
}
