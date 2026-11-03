package com.zigrawr.jbplugin.toolchain

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.builtin.ZigBuiltinCatalog
import com.zigrawr.jbplugin.builtin.ZigBuiltinEntry
import com.zigrawr.jbplugin.lexer.decodeZigStringLiteral
import com.zigrawr.zig.foreign.model.TokenTag
import com.zigrawr.zig.foreign.model.Tokens
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

data class ZigToolchainInfo(val executable: String, val version: String, val stdDirectory: String)

private data class ToolchainSnapshot(
    val selection: String,
    val info: ZigToolchainInfo?,
    val root: VirtualFile?,
    val libraries: List<SyntheticLibrary>,
    val builtins: ZigBuiltinCatalog?,
    val message: String,
)

@Service(Service.Level.PROJECT)
class ZigToolchainService(private val project: Project, internal val coroutineScope: CoroutineScope) : Disposable {
    private val generation = AtomicLong()

    @Volatile
    private var snapshot = ToolchainSnapshot("", null, null, emptyList(), null, "Zig has not been detected yet.")

    val currentInfo: ZigToolchainInfo?
        get() = currentSnapshot()?.info

    val statusMessage: String
        get() {
            val current = snapshot
            if (current.selection != project.service<ZigToolchainSettings>().executable.trim()) {
                return "Zig executable changed. Apply settings to detect it."
            }
            if (current.info != null && current.root?.isValid != true) {
                return "Zig standard library is unavailable. Reload the toolchain."
            }
            return current.message
        }

    internal val libraries: List<SyntheticLibrary>
        get() = currentSnapshot()?.libraries ?: emptyList()

    internal val sourceRoots: Collection<VirtualFile>
        get() = libraries.firstOrNull()?.sourceRoots ?: emptyList()

    internal fun standardLibraryFile(): VirtualFile? = currentSnapshot()?.root?.findChild("std.zig")

    internal fun builtin(name: String): ZigBuiltinEntry? = currentSnapshot()?.builtins?.find(name)

    fun inspect(executable: String): CompletableFuture<ZigToolchainInfo> =
        coroutineScope.future(Dispatchers.IO) { runInterruptible { probe(executable.trim()) } }

    fun refresh(): CompletableFuture<ZigToolchainInfo?> {
        val modality = ModalityState.defaultModalityState()
        val selection = project.service<ZigToolchainSettings>().executable.trim()
        val request = generation.incrementAndGet()
        return coroutineScope.future(Dispatchers.IO + modality.asContextElement()) {
            var info: ZigToolchainInfo? = null
            var root: VirtualFile? = null
            var builtins: ZigBuiltinCatalog? = null
            var failure: Exception? = null
            try {
                runInterruptible {
                    info = probe(selection)
                    if (!project.isDisposed && request == generation.get()) {
                        root = LocalFileSystem.getInstance()
                            .refreshAndFindFileByNioFile(Path.of(info.stdDirectory, "std.zig"))?.parent
                        check(root != null) { "The Zig standard library is not available in the IDE filesystem." }
                        try {
                            builtins = ZigBuiltinCatalog.load(info)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: ProcessCanceledException) {
                            throw exception
                        } catch (_: Exception) {
                            // Builtin documentation is optional.
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ProcessCanceledException) {
                throw exception
            } catch (exception: Exception) {
                failure = exception
            }
            withContext(Dispatchers.EDT) {
                if (project.isDisposed || request != generation.get() ||
                    selection != project.service<ZigToolchainSettings>().executable.trim()
                ) return@withContext null
                val previous = snapshot
                val ready = if (root == null || failure != null) null else info
                val libraries = if (ready == null) emptyList() else listOf(ZigStandardLibrary(root!!, ready.version))
                snapshot = ToolchainSnapshot(
                    selection, ready, root, libraries,
                    if (ready == null) null else builtins, failure?.message ?: "Ready",
                )
                WriteAction.run<RuntimeException> {
                    AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                        project, "Zig standard library", previous.libraries.flatMap { it.sourceRoots },
                        libraries.flatMap { it.sourceRoots }, "zigrawr.std",
                    )
                }
                DaemonCodeAnalyzer.getInstance(project).restart("Zig toolchain changed")
                ready
            }
        }
    }

    private fun currentSnapshot(): ToolchainSnapshot? {
        val current = snapshot
        return if (current.selection == project.service<ZigToolchainSettings>().executable.trim() &&
            (current.info == null || current.root?.isValid == true)
        ) current else null
    }

    private fun probe(selection: String): ZigToolchainInfo {
        check(!project.isDisposed) { "The project is closed." }
        val requested = selection.ifEmpty { "zig" }
        val executable = if (requested.indexOf('/') < 0 && requested.indexOf('\\') < 0) {
            PathEnvironmentVariableUtil.findInPath(requested)?.toPath()
                ?: error("Zig executable was not found on PATH: $requested")
        } else {
            val path = Path.of(requested)
            if (path.isAbsolute) path else Path.of(project.basePath ?: ".").resolve(path)
        }.toAbsolutePath().normalize()
        check(Files.isRegularFile(executable) && Files.isExecutable(executable)) { "Invalid Zig executable: $executable" }
        val command = GeneralCommandLine(executable.toString(), "env").withCharset(Charsets.UTF_8)
        project.basePath?.let { if (Files.isDirectory(Path.of(it))) command.withWorkDirectory(it) }
        val handler = CapturingProcessHandler(command)
        val output = try {
            handler.runProcess(5000, true)
        } finally {
            if (!handler.isProcessTerminated) handler.destroyProcess()
        }
        check(!output.isTimeout) { "Zig environment detection timed out." }
        check(!output.isCancelled && output.exitCode == 0) { "Zig environment detection failed (exit ${output.exitCode})." }
        val source = output.stdout
        val tokens = Tokens(IdeProcessState.getInstance().bindings.tokens.parseChars(source))
        var stdDirectory: String? = null
        var libDirectory: String? = null
        var version: String? = null
        var reportedExecutable: String? = null
        var depth = 0
        for (index in 0 until tokens.tokenCount() - 2) {
            when (tokens.tokenTag(index)) {
                TokenTag.L_BRACE -> depth++
                TokenTag.R_BRACE -> depth--
                else -> Unit
            }
            if (depth != 1 || tokens.tokenTag(index + 2) != TokenTag.STRING_LITERAL) continue
            val tag = tokens.tokenTag(index)
            if (!(tag == TokenTag.IDENTIFIER && tokens.tokenTag(index + 1) == TokenTag.EQUAL) &&
                !(tag == TokenTag.STRING_LITERAL && tokens.tokenTag(index + 1) == TokenTag.COLON)
            ) continue
            val keySpan = tokens.tokenPositions(index)
            val key = if (tag == TokenTag.IDENTIFIER) source.substring(keySpan.start, keySpan.end)
            else decodeZigStringLiteral(source, keySpan.start, keySpan.end)
            if (key != "std_dir" && key != "lib_dir" && key != "version" && key != "zig_exe") continue
            val valueSpan = tokens.tokenPositions(index + 2)
            val value = decodeZigStringLiteral(source, valueSpan.start, valueSpan.end)
                ?: error("Zig reported an invalid $key value.")
            when (key) {
                "std_dir" -> stdDirectory = value
                "lib_dir" -> libDirectory = value
                "version" -> version = value
                "zig_exe" -> reportedExecutable = value
            }
        }
        check(!version.isNullOrBlank()) { "Zig did not report its version." }
        val directory = stdDirectory?.let(Path::of) ?: libDirectory?.let { Path.of(it, "std") }
        ?: error("Zig did not report its standard library directory.")
        val resolved = if (directory.isAbsolute) directory else (command.workDirectory?.toPath()
            ?: Path.of(".")).resolve(directory)
        check(Files.isRegularFile(resolved.resolve("std.zig"))) { "Zig standard library not found: $resolved" }
        return ZigToolchainInfo(reportedExecutable ?: executable.toString(), version, resolved.toRealPath().toString())
    }

    override fun dispose() {
        generation.incrementAndGet()
    }
}
