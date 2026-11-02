package com.zigrawr.jbplugin.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import java.nio.file.Path

abstract class ZigContextConfigurationProducer(private val allTests: Boolean) : LazyRunConfigurationProducer<ZigRunConfiguration>(), DumbAware {
    override fun getConfigurationFactory(): ConfigurationFactory = ZigRunConfigurationType.getInstance().factory

    private fun target(context: ConfigurationContext): ZigRunTarget? {
        val element = context.psiLocation ?: return null
        return if (allTests) ZigRunTargets.fileTests(element.containingFile ?: return null) else ZigRunTargets.at(element)
    }

    override fun setupConfigurationFromContext(
        configuration: ZigRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val target = target(context) ?: return false
        val file = context.psiLocation?.containingFile ?: return false
        val path = file.virtualFile?.path ?: return false
        configuration.kind = target.kind
        configuration.filePath = path
        configuration.testName = target.testName
        configuration.name = target.displayName
        sourceElement.set(file.findElementAt(target.offset) ?: file)
        return true
    }

    override fun isConfigurationFromContext(configuration: ZigRunConfiguration, context: ConfigurationContext): Boolean {
        val target = target(context) ?: return false
        val path = context.psiLocation?.containingFile?.virtualFile?.path ?: return false
        val configured = Path.of(configuration.filePath)
        val absolute = if (configured.isAbsolute) configured else Path.of(configuration.project.basePath ?: ".").resolve(configured)
        return configuration.kind == target.kind && configuration.testName == target.testName &&
            absolute.normalize().toString() == path
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
        !allTests && (other.configuration as? ZigRunConfiguration)?.kind == ZigRunKind.TEST_FILE
}

class ZigRunConfigurationProducer : ZigContextConfigurationProducer(false)
class ZigFileTestsConfigurationProducer : ZigContextConfigurationProducer(true)
