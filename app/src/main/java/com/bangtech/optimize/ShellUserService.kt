package com.bangtech.optimize

import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject
import kotlin.system.exitProcess

class ShellUserService : IShellService.Stub() {
    override fun runCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val code = process.waitFor()
            JSONObject().apply {
                put("exitCode", code)
                put("out", out)
                put("err", err)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("exitCode", -1)
                put("out", "")
                put("err", e.message ?: "Unknown error")
            }.toString()
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
