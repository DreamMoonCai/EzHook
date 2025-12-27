package com.wulinpeng.ezhook.demo.hook

import com.wulinpeng.ezhook.demov2.NormalTest
import com.wulinpeng.ezhook.runtime.EzHook
import com.wulinpeng.ezhook.runtime.callOrigin
import com.wulinpeng.ezhook.runtime.getField
import com.wulinpeng.ezhook.runtime.getThisProperty
import com.wulinpeng.ezhook.runtime.getThisRef
import com.wulinpeng.ezhook.runtime.setField
import com.wulinpeng.ezhook.runtime.setThisProperty
import kotlin.time.DurationUnit

// TODO support constructor
@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.<init>",true)
fun hookContructor(name: String) {
    callOrigin<Unit>("newName")
}

@EzHook("com.wulinpeng.ezhook.demov2.topLevelPropertyTest")
var topLevelPropertyTest = "6666666666"
    get() = field +"4444"
    set(value) {
        field = value + "5555"
    }

@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.testProperty")
var newTestProperty = "777777"
    get() = callOrigin<String?>() +"3333"
    set(value) {
        field = value + "22222"
    }

@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.test", true)
fun newTest(name: String): String {
    return "before hook: ${callOrigin<String>("newName")}, after hook: $name-2"
}

@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.testGetThis", true)
fun newTestGetThis(name: String): String {
    return "get hook in this: ${getThisRef<NormalTest>()}, to $name"
}

@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.testReParam", true)
fun newTestReParam(name: String,age: Int): String {
    return "get hook in this: ${callOrigin<String>(p1 = 800)}, after $name,age: $age"
}

@EzHook("com.wulinpeng.ezhook.demov2.topLevelFunctionTest", true)
fun topLevelFunctionTest(name: String): String {
    return "before hook: ${callOrigin<String>("newName")}, after hook: $name-2"
}

@EzHook("kotlin.time.Duration.toInt", true)
fun toInt(unit: DurationUnit): Int {
    println("Hook to int")
    return 10086
}


// TODO support extension function
@EzHook("com.wulinpeng.ezhook.demov2.getStr")
fun Int.getStr(): String {
    return "${callOrigin<String>()}-new2"
}

// TODO Delegated property
@EzHook.Before("com.wulinpeng.ezhook.demov2.NormalTest.testLazy", true)
val testLazy get() = run {
    println("thisRef:" + getThisRef())
    println("originalDelegated: " + getField<Any>()::class)
    println("originalGetter: " + callOrigin())
    println("getThisProperty:" + getThisProperty("testProperty"))
    setThisProperty("testProperty", "new hook testProperty")
    setField(lazy { "new hook testLazy" })
}