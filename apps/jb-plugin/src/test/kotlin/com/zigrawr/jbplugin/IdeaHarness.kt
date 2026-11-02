package com.zigrawr.jbplugin

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.zigrawr.zig.tokenizer.BaseSpec
import io.kotest.core.spec.style.ShouldSpec

abstract class IdeaHarness(body: ShouldSpec.() -> Unit) : BaseSpec(body)

fun runSelectionFixture(
    block: (fixture: CodeInsightTestFixture) -> Unit,
    projectName: String = "zig selection",
) {
    runInEdtAndWait {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val project = factory.createLightFixtureBuilder(projectName).fixture
        val fixture = factory.createCodeInsightFixture(project)
        fixture.setUp()
        try {
            block(fixture)
        } finally {
            fixture.tearDown()
        }
    }
}
