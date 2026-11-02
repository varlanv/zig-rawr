package com.zigrawr.jbplugin.selection

import com.zigrawr.jbplugin.IdeaHarness
import com.zigrawr.jbplugin.runSelectionFixture
import io.kotest.matchers.shouldBe

class ZigSelectionHandlerSpec : IdeaHarness({ ->
    should("expand an expression and shrink back through editor actions") {
        runSelectionFixture({ fixture ->
            fixture.configureByText("selection.zig", "const result = sum(al<caret>pha + beta, other);")

            fixture.performEditorAction("EditorSelectWord")
            fixture.editor.selectionModel.selectedText shouldBe "alpha"

            fixture.performEditorAction("EditorSelectWord")
            fixture.editor.selectionModel.selectedText shouldBe "alpha + beta"

            fixture.performEditorAction("EditorUnSelectWord")
            fixture.editor.selectionModel.selectedText shouldBe "alpha"
        })
    }

})
