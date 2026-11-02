package com.zigrawr.jbplugin.toolchain

import com.intellij.openapi.components.*

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
