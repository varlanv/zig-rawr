package com.zigrawr.jbplugin.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ZigRunConfigurationEditor(project: Project) : SettingsEditor<ZigRunConfiguration>() {
    private val kind = ComboBox(ZigRunKind.entries.toTypedArray())
    private val sourceFile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile().withTitle("Select Zig Source File"))
    }
    private val testName = JBTextField()
    private val compilerArguments = JBTextField()
    private val programArguments = JBTextField()
    private val workingDirectory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir().withTitle("Select Working Directory"))
    }
    private val panel = JPanel(BorderLayout()).apply {
        add(FormBuilder.createFormBuilder()
            .addLabeledComponent("Run:", kind)
            .addLabeledComponent("Source file:", sourceFile)
            .addLabeledComponent("Test name or group:", testName)
            .addLabeledComponent("Compiler arguments:", compilerArguments)
            .addLabeledComponent("Program arguments:", programArguments)
            .addLabeledComponent("Working directory:", workingDirectory)
            .addTooltip("Leave the working directory empty to use the source file's directory.")
            .panel, BorderLayout.NORTH)
    }

    init {
        kind.addActionListener { updateEnabledFields() }
        updateEnabledFields()
    }

    override fun resetEditorFrom(configuration: ZigRunConfiguration) {
        kind.selectedItem = configuration.kind
        sourceFile.text = configuration.filePath
        testName.text = configuration.testName
        compilerArguments.text = configuration.compilerArguments
        programArguments.text = configuration.programArguments
        workingDirectory.text = configuration.workingDirectory
        updateEnabledFields()
    }

    override fun applyEditorTo(configuration: ZigRunConfiguration) {
        configuration.kind = kind.selectedItem as ZigRunKind
        configuration.filePath = sourceFile.text.trim()
        configuration.testName = testName.text
        configuration.compilerArguments = compilerArguments.text
        configuration.programArguments = programArguments.text
        configuration.workingDirectory = workingDirectory.text.trim()
    }

    override fun createEditor(): JComponent = panel

    override fun disposeEditor() {
        Disposer.dispose(sourceFile)
        Disposer.dispose(workingDirectory)
    }

    private fun updateEnabledFields() {
        val selected = kind.selectedItem as ZigRunKind
        testName.isEnabled = selected == ZigRunKind.TEST || selected == ZigRunKind.TEST_GROUP
        programArguments.isEnabled = selected == ZigRunKind.MAIN
    }
}
