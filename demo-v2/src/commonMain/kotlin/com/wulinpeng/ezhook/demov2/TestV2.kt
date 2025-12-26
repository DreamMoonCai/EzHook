package com.wulinpeng.ezhook.demov2

abstract class B(text: String){
    init {
        println("B init $text")
    }
    fun a() = "a"
}

class NormalTest(name: String): B(name) {

    init {
        println("NormalTest init $name")
    }

    var testProperty = "testProperty"

    fun test(name: String): String {
        return "$name-1"
    }

    fun testGetThis(name: String): String {
        return "$name-2"
    }
}

var topLevelPropertyTest: String = "topLevelPropertyTest"

fun topLevelFunctionTest(name: String): String {
    return "$name-1"
}

fun Int.getStr(): String {
    return "Int value: $this"
}