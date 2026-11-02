package com.zigrawr.jbplugin.highlight

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel

internal class ZigSemanticStylesPanel {
    private class Row(val label: String, val key: TextAttributesKey) {
        val color = JBTextField(8).apply { accessibleContext.accessibleName = "${this@Row.label} color" }
        val bold = JBCheckBox("Bold").apply { accessibleContext.accessibleName = "${this@Row.label} bold" }
        val italic = JBCheckBox("Italic").apply { accessibleContext.accessibleName = "${this@Row.label} italic" }
        val plain = JButton("Plain").apply {
            accessibleContext.accessibleName = "${this@Row.label} plain"
            addActionListener {
                bold.isSelected = false
                italic.isSelected = false
            }
        }
        var originalColor: Color? = null
        var originalFont: Int = Font.PLAIN

        fun foreground(): Color? {
            val value = color.text.trim().removePrefix("#")
            if (value.length != 6 || value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
            return Color(value.toInt(16))
        }

        fun font(): Int = (if (bold.isSelected) Font.BOLD else Font.PLAIN) or (if (italic.isSelected) Font.ITALIC else Font.PLAIN)
        fun isModified(): Boolean = foreground() != originalColor || font() != originalFont
    }

    private val rows = arrayOf(
        Row("Fields and enum members", ZigHighlightingColors.FIELD),
        Row("Function declarations", ZigHighlightingColors.FUNCTION_DECLARATION),
        Row("Static function calls", ZigHighlightingColors.STATIC_FUNCTION_CALL),
        Row("Builtin functions", ZigHighlightingColors.BUILTIN_FUNCTION),
        Row("Error names", ZigHighlightingColors.ERROR_NAME),
    )
    private val schemeName = JBLabel()
    val panel = JPanel(GridBagLayout()).apply {
        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(3, 0, 3, 12)
            gridx = 0
            gridy = 0
            gridwidth = 5
        }
        add(schemeName, constraints)
        constraints.gridy++
        add(JBLabel("Text color: #RRGGBB. Clear Bold and Italic, or select Plain, for regular text."), constraints)
        constraints.gridwidth = 1
        for (row in rows) {
            constraints.gridy++
            constraints.gridx = 0
            add(JBLabel(row.label).apply { labelFor = row.color }, constraints)
            constraints.gridx++
            add(row.color, constraints)
            constraints.gridx++
            add(row.bold, constraints)
            constraints.gridx++
            add(row.italic, constraints)
            constraints.gridx++
            add(row.plain, constraints)
        }
    }

    fun isModified(): Boolean = rows.any { it.isModified() }

    fun reset() {
        val scheme = EditorColorsManager.getInstance().globalScheme
        schemeName.text = "Semantic colors — ${scheme.name.removePrefix("_@user_")}"
        for (row in rows) {
            val attributes = scheme.getAttributes(row.key)
            val foreground = attributes?.foregroundColor ?: scheme.defaultForeground
            row.color.text = "#%06X".format(foreground.rgb and 0xffffff)
            row.originalColor = foreground
            row.originalFont = attributes?.fontType ?: Font.PLAIN
            row.bold.isSelected = row.originalFont and Font.BOLD != 0
            row.italic.isSelected = row.originalFont and Font.ITALIC != 0
        }
    }

    fun apply() {
        val colors = rows.map { row ->
            row.foreground() ?: run {
                row.color.requestFocusInWindow()
                throw ConfigurationException("${row.label}: enter six hexadecimal digits, for example #C6B56B.", "Invalid Zig color")
            }
        }
        if (!isModified()) return
        val manager = EditorColorsManager.getInstance()
        val original = manager.globalScheme
        val scheme = if (original.isReadOnly) {
            (original.clone() as com.intellij.openapi.editor.colors.EditorColorsScheme).apply {
                val base = "${original.name} (Zig Rawr)"
                var candidate = base
                var suffix = 2
                while (manager.getScheme(candidate) != null) candidate = "$base ${suffix++}"
                name = candidate
            }
        } else original
        for ((index, row) in rows.withIndex()) {
            if (!row.isModified()) continue
            val attributes = scheme.getAttributes(row.key)?.clone() ?: com.intellij.openapi.editor.markup.TextAttributes()
            attributes.foregroundColor = colors[index]
            attributes.fontType = row.font()
            scheme.setAttributes(row.key, attributes)
        }
        (scheme as? AbstractColorsScheme)?.setSaveNeeded(true)
        if (scheme !== original) {
            manager.addColorScheme(scheme)
            manager.setGlobalScheme(scheme)
        }
        EditorColorsManagerImpl.fireGlobalSchemeChange(scheme)
        reset()
    }
}
