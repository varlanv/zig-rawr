package com.varlanv.gradle.plugin

data class InternalEnvironment(val isCi: Boolean, val isTest: Boolean) {

    companion object {
        const val NAME = "__internal_environment__"
    }

    fun isLocal(): Boolean {
        return !isCi
    }
}
