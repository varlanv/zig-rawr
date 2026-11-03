package com.zigrawr.jbplugin.highlight

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.util.JDOMUtil

class ZigColorSchemeListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString != "com.varlanv.zigrawr") return

        val manager = EditorColorsManager.getInstance()
        for (extension in EditorColorsManagerImpl.ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME.extensionList) {
            if (extension.getPluginDescriptor().pluginId != pluginDescriptor.pluginId) continue
            val scheme = manager.getScheme(extension.scheme) as? AbstractColorsScheme ?: continue
            val resource = requireNotNull(javaClass.getResourceAsStream("/${extension.file}")) {
                "Missing color defaults: ${extension.file}"
            }
            val root = resource.use { JDOMUtil.load(it) }
            scheme.readAttributes(root.getChild("attributes") ?: root)
        }
        EditorColorsManagerImpl.fireGlobalSchemeChange(manager.globalScheme)
    }
}
