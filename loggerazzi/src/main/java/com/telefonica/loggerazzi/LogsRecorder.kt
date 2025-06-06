package com.telefonica.loggerazzi

public interface LogsRecorder<LogType> {
    public fun clear()
    public fun getRecordedLogs(): List<LogType>
}
