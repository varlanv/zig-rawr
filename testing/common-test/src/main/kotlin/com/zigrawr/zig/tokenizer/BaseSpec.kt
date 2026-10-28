package com.zigrawr.zig.tokenizer

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.concurrency.TestExecutionMode
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/**
 * Base extension point for all tests in application.
 * All tests should use this parent instead of using Kotest classes directly.
 *
 * ### Test name convention
 *
 * Test names must read as a natural English sentence describing the behavior under test,
 * such that `should("...")` plus the description forms a coherent phrase.
 *
 * Concretely:
 *  - Start with a verb in the present tense that completes the implicit subject "it should ..."
 *    (e.g. `"return ..."`, `"throw ..."`, `"skip ..."`, `"treat ... as ..."`).
 *  - Describe the observable behavior and the condition that triggers it, not the implementation,
 *    the test mechanics, or the method name.
 *  - Avoid "test" / "tests" / "verify" / "check" framing — `should("...")` already implies it.
 *  - Avoid `methodName - X` prefixes; weave the method or component into the sentence when it
 *    actually clarifies what is being described.
 *
 * Good:
 *  - `should("return the same instance for repeated invocations with the same asset name")`
 *  - `should("skip streams already covered by an existing connection when subscribing")`
 *  - `should("throw an Error when interning an asset whose name exceeds forty characters")`
 *
 * Bad:
 *  - `should("test that intern works")` — uses "test" framing, vague behavior.
 *  - `should("intern - same string returns same instance")` — method-prefix shorthand, not a sentence.
 *  - `should("verify equals returns false for null")` — uses "verify", names the method instead of the behavior.
 */
abstract class BaseSpec(body: ShouldSpec.() -> Unit) : ShouldSpec({
    isolationMode = IsolationMode.SingleInstance
    testExecutionMode = TestExecutionMode.LimitedConcurrency(limitedConcurrency())

    body()
})

internal fun limitedConcurrency(): Int = Runtime.getRuntime().availableProcessors()

/**
 * A simple utility to run arbitrary code block with temporary directory.
 * Directory is deleted recursively after closure exits.
 */
fun useTempDir(block: (Path) -> Unit) {
    val tempDir = createTempDirectory()
    try {
        block(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
