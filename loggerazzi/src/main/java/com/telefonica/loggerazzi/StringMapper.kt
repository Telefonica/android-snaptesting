package com.telefonica.loggerazzi

public interface StringMapper<LogType> {
    public fun fromLog(log: LogType): String

    public fun toLog(stringLog: String): LogType
}