package com.zigrawr.jbplugin.toolchain

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "ZigRawrToolchain", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ZigToolchainSettings : PersistentStateComponent<ZigToolchainSettings.SettingsState> {
    class SettingsState {
        var executable: String = ""
    }

    @Volatile
    var executable: String = ""

    override fun getState(): SettingsState = SettingsState().also { it.executable = executable }

    override fun loadState(state: SettingsState) {
        executable = state.executable.trim()
    }
}
