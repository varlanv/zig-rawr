package com.zigrawr.jbplugin.log

import com.intellij.openapi.diagnostic.logger
import com.zigrawr.lang.log.Log

object IntellijLog {

    fun asInternalLog(): Log {
        val log = logger<IntellijLog>()
        return Log(enabled = true, sink = object : Log.Sink {
            override fun print(message: String) {
                log.error(message)
            }
        })
    }
}
