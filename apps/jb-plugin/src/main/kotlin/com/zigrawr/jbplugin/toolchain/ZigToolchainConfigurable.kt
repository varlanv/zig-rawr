package com.zigrawr.jbplugin.toolchain

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.zigrawr.jbplugin.highlight.ZigSemanticStylesPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

class ZigToolchainConfigurable(private val project: Project) : SearchableConfigurable {
    private class Form(project: Project) {
        val executable = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.singleFile().withTitle("Select Zig Executable")
            )
        }
        val check = JButton("Check")
        val reload = JButton("Reload")
        val status = JBLabel()
        val detectedExecutable = JBLabel().setCopyable(true)
        val version = JBLabel()
        val standardLibrary = JBLabel().setCopyable(true)
        val styles = ZigSemanticStylesPanel()
        val panel = JPanel(BorderLayout()).apply {
            add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent("Zig executable:", executable)
                    .addTooltip("Leave empty to use Zig from PATH.")
                    .addComponentToRightColumn(JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply {
                        add(check)
                        add(reload)
                    })
                    .addLabeledComponent("Status:", status)
                    .addLabeledComponent("Detected executable:", detectedExecutable)
                    .addLabeledComponent("Version:", version)
                    .addLabeledComponent("Standard library:", standardLibrary)
                    .addSeparator()
                    .addComponent(styles.panel)
                    .panel, BorderLayout.NORTH
            )
        }
    }

    private var form: Form? = null
    private var request = 0
    private var updateJob: Job? = null

    override fun getId(): String = "com.zigrawr.toolchain"
    override fun getDisplayName(): String = "Zig Rawr"
    override fun getPreferredFocusedComponent(): JComponent? = form?.executable?.textField

    override fun createComponent(): JComponent {
        form?.let { return it.panel }
        val created = Form(project)
        form = created
        created.check.addActionListener {
            val executable = created.executable.text.trim()
            update(created, executable, project.service<ZigToolchainService>().inspect(executable))
        }
        created.reload.addActionListener {
            val executable = project.service<ZigToolchainSettings>().executable
            if (created.executable.text.trim() != executable) {
                display(created, null, "Apply the executable change before reloading.")
            } else {
                update(created, executable, project.service<ZigToolchainService>().refresh())
            }
        }
        reset()
        return created.panel
    }

    override fun isModified(): Boolean {
        val current = form ?: return false
        return current.styles.isModified() || current.executable.text.trim() != project.service<ZigToolchainSettings>().executable
    }

    override fun apply() {
        val current = form ?: return
        current.styles.apply()
        val executable = current.executable.text.trim()
        if (project.service<ZigToolchainSettings>().executable == executable) return
        project.service<ZigToolchainSettings>().executable = executable
        update(current, executable, project.service<ZigToolchainService>().refresh())
    }

    override fun reset() {
        val current = form ?: return
        updateJob?.cancel()
        updateJob = null
        request++
        current.check.isEnabled = true
        current.reload.isEnabled = true
        current.executable.text = project.service<ZigToolchainSettings>().executable
        current.styles.reset()
        val service = project.service<ZigToolchainService>()
        display(current, service.currentInfo, service.statusMessage)
    }

    override fun disposeUIResources() {
        updateJob?.cancel()
        updateJob = null
        request++
        val current = form ?: return
        form = null
        Disposer.dispose(current.executable)
    }

    private fun update(current: Form, executable: String, result: CompletableFuture<out ZigToolchainInfo?>) {
        val sequence = ++request
        current.check.isEnabled = false
        current.reload.isEnabled = false
        display(current, null, "Checking Zig…")
        val modality = ModalityState.stateForComponent(current.panel)
        updateJob?.cancel()
        updateJob = project.service<ZigToolchainService>().coroutineScope.launch(
            Dispatchers.EDT + modality.asContextElement()
        ) {
            var info: ZigToolchainInfo? = null
            var error: Exception? = null
            try {
                info = result.await()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ProcessCanceledException) {
                throw exception
            } catch (exception: Exception) {
                error = exception
            }
            if (form !== current || sequence != request || project.isDisposed) return@launch
            current.check.isEnabled = true
            current.reload.isEnabled = true
            if (current.executable.text.trim() != executable) {
                display(current, null, "Executable changed. Check again.")
            } else if (error != null) {
                display(current, null, error.cause?.message ?: error.message ?: "Unable to inspect Zig.")
            } else {
                display(current, info, if (info == null) project.service<ZigToolchainService>().statusMessage else "Ready")
            }
        }
    }

    private fun display(current: Form, info: ZigToolchainInfo?, message: String) {
        current.status.text = message
        current.detectedExecutable.text = info?.executable ?: "—"
        current.version.text = info?.version ?: "—"
        current.standardLibrary.text = info?.stdDirectory ?: "—"
    }
}
