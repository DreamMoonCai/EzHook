package com.wulinpeng.ezhook.runtime

/**
 * author: wulinpeng
 * create: 2024/11/21 22:55
 */
@Target(AnnotationTarget.FUNCTION,AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class EzHook(val targetFunctionOrProperty: String, val inline: Boolean = false)