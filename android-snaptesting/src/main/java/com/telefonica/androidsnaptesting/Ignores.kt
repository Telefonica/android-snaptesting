package com.telefonica.androidsnaptesting


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class IgnoreLogs(
    val reason: String = ""
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class IgnoreScreenshots(
    val reason: String = ""
)
