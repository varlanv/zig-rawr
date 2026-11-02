package com.zigrawr.jbplugin.toolchain

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class ZigStandardLibrary(private val root: VirtualFile, private val version: String) : SyntheticLibrary(), ItemPresentation {
    private val roots = listOf(root)

    override fun getSourceRoots(): Collection<VirtualFile> = roots
    override fun getPresentableText(): String = "Zig $version standard library"
    override fun getLocationString(): String = root.path
    override fun getIcon(unused: Boolean): Icon? = null
    override fun equals(other: Any?): Boolean = other is ZigStandardLibrary && root == other.root && version == other.version
    override fun hashCode(): Int = 31 * root.hashCode() + version.hashCode()
}

class ZigLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
        project.service<ZigToolchainService>().libraries

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        project.service<ZigToolchainService>().sourceRoots
}

class ZigToolchainStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<ZigToolchainService>().refresh()
    }
}
