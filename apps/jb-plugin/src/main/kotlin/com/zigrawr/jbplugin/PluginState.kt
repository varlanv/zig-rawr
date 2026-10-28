package com.zigrawr.jbplugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class IdeProcessState

@Service(Service.Level.PROJECT)
class IdeProjectState(private val project: Project)
