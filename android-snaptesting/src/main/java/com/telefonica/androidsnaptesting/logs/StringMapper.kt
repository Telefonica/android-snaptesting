package com.telefonica.androidsnaptesting.logs

public interface StringMapper<LogType> {
    public fun fromLog(log: LogType): String

    public fun toLog(stringLog: String): LogType
}