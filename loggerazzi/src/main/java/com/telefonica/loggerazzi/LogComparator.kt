package com.telefonica.loggerazzi

import java.lang.StringBuilder

public interface LogComparator<LogType> {
    public fun compare(recorded: List<LogType>, golden: List<LogType>): String?
}

public class DefaultLogComparator<LogType> : LogComparator<LogType> {
    override fun compare(recorded: List<LogType>, golden: List<LogType>): String? {
        if (recorded.size != golden.size) {
            return "Different number of lines: recorded=${recorded.size}, golden=${golden.size}"
        }

        val compareResult = StringBuilder()
        for (i in recorded.indices) {
            if (recorded[i] != golden[i]) {
                compareResult.appendLine("Different line at index $i: recorded=${recorded[i]}, golden=${golden[i]}")
            }
        }

        return compareResult.toString().takeIf { it.isNotEmpty() }
    }
}