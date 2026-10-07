package com.example.servicebeat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ServiceLog {
    private val logs = mutableListOf<String>()
    private const val MAX_LOGS = 100
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(message: String) {
        val time = dateFormat.format(Date())
        val entry = "$time $message"
        logs.add(entry)
        if (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
    }

    fun getLogs(): String {
        return logs.joinToString("\n")
    }
}
