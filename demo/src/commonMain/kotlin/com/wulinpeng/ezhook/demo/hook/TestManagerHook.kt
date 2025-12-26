package com.wulinpeng.ezhook.demo.hook

import com.wulinpeng.ezhook.demov2.NormalTest
import com.wulinpeng.ezhook.runtime.EzHook
import com.wulinpeng.ezhook.runtime.callOrigin
import com.wulinpeng.ezhook.runtime.getThisRef
import kotlin.time.DurationUnit

// TODO support constructor
@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.<init>")
fun hookContructor(name: String) {
    var name = "newName"
    callOrigin<Unit>()
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
    var name = "newName"
    return "before hook: ${callOrigin<String>()}, after hook: $name-2"
}

@EzHook("com.wulinpeng.ezhook.demov2.NormalTest.testGetThis", true)
fun newTestGetThis(name: String): String {
    var name = "newName"
    return "get hook in this: ${getThisRef<NormalTest>()}, to $name"
}

@EzHook("com.wulinpeng.ezhook.demov2.topLevelFunctionTest", true)
fun topLevelFunctionTest(name: String): String {
    var name = "newName"
    return "before hook: ${callOrigin<String>()}, after hook: $name-2"
}

@EzHook("kotlin.time.Duration.toInt", true)
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS
    println("Hook to int")
    return 10086
}


// TODO support extension function
@EzHook("com.wulinpeng.ezhook.demov2.getStr")
fun Int.getStr(): String {
    return "${callOrigin<String>()}-new2"
}