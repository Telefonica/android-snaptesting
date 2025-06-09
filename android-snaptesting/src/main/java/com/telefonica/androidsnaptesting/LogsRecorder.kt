package com.telefonica.androidsnaptesting

public interface LogsRecorder<LogType> {
    public fun clear()
    public fun getRecordedLogs(): List<LogType>
}
