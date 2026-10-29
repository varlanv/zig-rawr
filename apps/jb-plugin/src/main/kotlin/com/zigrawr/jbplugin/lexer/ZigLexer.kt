package com.zigrawr.jbplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.zigrawr.jbplugin.IdeProcessState

class ZigLexer(state: IdeProcessState) : LexerBase() {
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        TODO("Not yet implemented")
    }

    override fun getState(): Int {
        TODO("Not yet implemented")
    }

    override fun getTokenType(): IElementType? {
        TODO("Not yet implemented")
    }

    override fun getTokenStart(): Int {
        TODO("Not yet implemented")
    }

    override fun getTokenEnd(): Int {
        TODO("Not yet implemented")
    }

    override fun advance() {
        TODO("Not yet implemented")
    }

    override fun getBufferSequence(): CharSequence {
        TODO("Not yet implemented")
    }

    override fun getBufferEnd(): Int {
        TODO("Not yet implemented")
    }
}
