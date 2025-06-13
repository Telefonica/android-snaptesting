package com.telefonica.androidsnaptesting.logs

public interface LogsRecorder<LogType> {
    public fun clear()
    public fun getRecordedLogs(): List<LogType>
}
