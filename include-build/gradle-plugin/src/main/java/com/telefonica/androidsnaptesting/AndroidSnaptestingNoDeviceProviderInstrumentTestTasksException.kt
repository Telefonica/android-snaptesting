package com.telefonica.androidsnaptesting

class AndroidSnaptestingNoDeviceProviderInstrumentTestTasksException : Exception(
    "No device provider instrument test tasks found. Make sure you are applying the Android Snaptesting plugin after the Android app/library plugin."
)