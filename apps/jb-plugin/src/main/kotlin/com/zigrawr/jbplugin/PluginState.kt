package com.zigrawr.jbplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.zigrawr.zig.native.Bindings
import com.zigrawr.zig.native.BundledBindingsLoader

@Service(Service.Level.APP)
class IdeProcessState : Disposable {

    val bindings = Bindings(BundledBindingsLoader.load())

    override fun dispose() {
        bindings.close()
    }

}

@Service(Service.Level.PROJECT)
class IdeProjectState(private val project: Project)
