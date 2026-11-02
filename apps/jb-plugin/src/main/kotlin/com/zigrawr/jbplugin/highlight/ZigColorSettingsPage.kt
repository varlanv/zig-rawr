package com.zigrawr.jbplugin.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.zigrawr.jbplugin.file.ZigIcons
import javax.swing.Icon

class ZigColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "Zig Rawr"

    override fun getIcon(): Icon = ZigIcons.file

    override fun getHighlighter(): SyntaxHighlighter = ZigSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tags

    override fun getDemoText(): String = """
        const std = @import("std");
        const Header = struct {
            <field>magic</field>: u32,
            <field>version</field>: u32,
        };

        fn <function>reserve</function>(size: usize) error{<error>TooLarge</error>}!Header {
            std.debug.<static>assert</static>(size > 0);
            if (size > 1024) return error.<error>TooLarge</error>;
            return .{ .<field>magic</field> = <builtin>@intCast</builtin>(size), .<field>version</field> = 1 };
        }
    """.trimIndent()

    companion object {
        private val descriptors = arrayOf(
            AttributesDescriptor("Fields and enum members", ZigHighlightingColors.FIELD),
            AttributesDescriptor("Function declarations", ZigHighlightingColors.FUNCTION_DECLARATION),
            AttributesDescriptor("Static function calls", ZigHighlightingColors.STATIC_FUNCTION_CALL),
            AttributesDescriptor("Builtin functions", ZigHighlightingColors.BUILTIN_FUNCTION),
            AttributesDescriptor("Error names", ZigHighlightingColors.ERROR_NAME),
        )
        private val tags = mapOf(
            "field" to ZigHighlightingColors.FIELD,
            "function" to ZigHighlightingColors.FUNCTION_DECLARATION,
            "static" to ZigHighlightingColors.STATIC_FUNCTION_CALL,
            "builtin" to ZigHighlightingColors.BUILTIN_FUNCTION,
            "error" to ZigHighlightingColors.ERROR_NAME,
        )
    }
}
