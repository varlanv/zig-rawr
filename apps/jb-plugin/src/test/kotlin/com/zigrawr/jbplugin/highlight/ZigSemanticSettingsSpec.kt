package com.zigrawr.jbplugin.highlight

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SchemeState
import com.intellij.testFramework.PlatformTestUtil
import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import com.zigrawr.jbplugin.toolchain.ZigToolchainConfigurable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jdom.Element
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Font
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JTextField

class ZigSemanticSettingsSpec : IdeaHarness({
    should("apply all semantic styles immediately and restore them from the persisted editor scheme") {
        runSelectionFixture({ fixture ->
            val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
            val original = manager.globalScheme
            val scheme = original.clone() as AbstractColorsScheme
            scheme.name = "Zig Rawr settings smoke"
            manager.addColorScheme(scheme)
            manager.setGlobalScheme(scheme, true)
            var restored: EditorColorsSchemeImpl? = null
            val configurable = ZigToolchainConfigurable(fixture.project)
            try {
                val field = scheme.getAttributes(ZigHighlightingColors.FIELD).clone().apply {
                    backgroundColor = Color(9, 8, 7)
                    effectColor = Color(1, 2, 3)
                    effectType = EffectType.LINE_UNDERSCORE
                }
                scheme.setAttributes(ZigHighlightingColors.FIELD, field)
                fixture.configureByText("styles.zig", "fn main() void { _ = @intCast(1); }")
                fixture.doHighlighting()
                val panel = configurable.createComponent()
                configurable.displayName shouldBe "Zig Rawr"
                configurable.isModified shouldBe false
                val values = listOf("#123456", "ABCDEF", "#A1B2C3", "#465768", "#B0C0D0")
                val fonts = listOf(Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC, Font.PLAIN, Font.BOLD)
                for ((index, style) in styles.withIndex()) {
                    control<JTextField>(panel, "${style.first} color").text = values[index]
                    control<JCheckBox>(panel, "${style.first} bold").isSelected = fonts[index] and Font.BOLD != 0
                    control<JCheckBox>(panel, "${style.first} italic").isSelected = fonts[index] and Font.ITALIC != 0
                }
                configurable.isModified shouldBe true
                configurable.apply()
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                manager.globalScheme shouldBe scheme
                configurable.isModified shouldBe false
                scheme.schemeState shouldBe SchemeState.POSSIBLY_CHANGED
                for ((index, style) in styles.withIndex()) {
                    val expected = Color(values[index].removePrefix("#").toInt(16))
                    scheme.getAttributes(style.second).foregroundColor shouldBe expected
                    scheme.getAttributes(style.second).fontType shouldBe fonts[index]
                    fixture.editor.colorsScheme.getAttributes(style.second).foregroundColor shouldBe expected
                    fixture.editor.colorsScheme.getAttributes(style.second).fontType shouldBe fonts[index]
                }
                val builtin = (fixture.editor as EditorEx).highlighter.createIterator(fixture.file.text.indexOf("@intCast"))
                builtin.textAttributes.foregroundColor shouldBe Color(0x465768)
                builtin.textAttributes.fontType shouldBe Font.PLAIN
                scheme.getAttributes(ZigHighlightingColors.FIELD).backgroundColor shouldBe field.backgroundColor
                scheme.getAttributes(ZigHighlightingColors.FIELD).effectColor shouldBe field.effectColor
                scheme.getAttributes(ZigHighlightingColors.FIELD).effectType shouldBe field.effectType

                val serialized = Element("scheme")
                scheme.writeExternal(serialized)
                restored = EditorColorsSchemeImpl(null).apply {
                    readExternal(serialized)
                    manager.resolveSchemeParent(this)
                    name = "Zig Rawr restored settings smoke"
                }
                manager.addColorScheme(restored)
                manager.setGlobalScheme(restored, true)
                configurable.disposeUIResources()
                val reopened = configurable.createComponent()
                for ((index, style) in styles.withIndex()) {
                    control<JTextField>(reopened, "${style.first} color").text shouldBe "#${values[index].removePrefix("#")}"
                    control<JCheckBox>(reopened, "${style.first} bold").isSelected shouldBe (fonts[index] and Font.BOLD != 0)
                    control<JCheckBox>(reopened, "${style.first} italic").isSelected shouldBe (fonts[index] and Font.ITALIC != 0)
                }
                configurable.isModified shouldBe false
            } finally {
                configurable.disposeUIResources()
                manager.setGlobalScheme(original, true)
                manager.removeScheme(scheme)
                restored?.let(manager::removeScheme)
            }
        }, "zig semantic style settings")
    }

    should("reject invalid colors atomically and offer plain and reset controls") {
        runSelectionFixture({ fixture ->
            val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
            val original = manager.globalScheme
            val scheme = original.clone() as AbstractColorsScheme
            scheme.name = "Zig Rawr color validation smoke"
            manager.addColorScheme(scheme)
            manager.setGlobalScheme(scheme, true)
            val configurable = ZigToolchainConfigurable(fixture.project)
            try {
                val panel = configurable.createComponent()
                val before = styles.map { scheme.getAttributes(it.second).clone() }
                control<JTextField>(panel, "Fields and enum members color").text = "#102030"
                for (invalid in listOf("", "12345", "1234567", "#GGGGGG", "12 3456")) {
                    control<JTextField>(panel, "Error names color").text = invalid
                    shouldThrow<ConfigurationException> { configurable.apply() }.message!! shouldContain "Error names"
                    styles.map { scheme.getAttributes(it.second) } shouldBe before
                }
                configurable.isModified shouldBe true
                configurable.reset()
                configurable.isModified shouldBe false
                val bold = control<JCheckBox>(panel, "Static function calls bold")
                val italic = control<JCheckBox>(panel, "Static function calls italic")
                bold.isSelected = true
                italic.isSelected = true
                control<JButton>(panel, "Static function calls plain").doClick()
                bold.isSelected shouldBe false
                italic.isSelected shouldBe false
                configurable.apply()
                scheme.getAttributes(ZigHighlightingColors.STATIC_FUNCTION_CALL).fontType shouldBe Font.PLAIN
            } finally {
                configurable.disposeUIResources()
                manager.setGlobalScheme(original, true)
                manager.removeScheme(scheme)
            }
        }, "zig semantic style validation")
    }
})

private val styles = listOf<Pair<String, TextAttributesKey>>(
    "Fields and enum members" to ZigHighlightingColors.FIELD,
    "Function declarations" to ZigHighlightingColors.FUNCTION_DECLARATION,
    "Static function calls" to ZigHighlightingColors.STATIC_FUNCTION_CALL,
    "Builtin functions" to ZigHighlightingColors.BUILTIN_FUNCTION,
    "Error names" to ZigHighlightingColors.ERROR_NAME,
)

private inline fun <reified T : Component> control(root: Component, name: String): T =
    components(root).filterIsInstance<T>().single { it.accessibleContext?.accessibleName == name }

private fun components(root: Component): Sequence<Component> = sequence {
    yield(root)
    if (root is Container) for (child in root.components) yieldAll(components(child))
}
