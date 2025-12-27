package com.wulinpeng.ezhook.demov2

abstract class B(text: String){
    init {
        println("B init $text")
    }
    val bValue = "b string"

    fun a() = "a"
}

class NormalTest(name: String): B(name) {

    constructor(): this("def")

    init {
        println("NormalTest init $name")
    }

    var testProperty = "testProperty"

    val testLazy by lazy { "testLazy" }

    fun test(name: String): String {
        return "$name-1"
    }

    fun testGetThis(name: String): String {
        return "$name-2"
    }

    fun testReParam(name: String, age: Int): String {
        return "$name-age:$age-3"
    }
}

var topLevelPropertyTest: String = "topLevelPropertyTest"

fun topLevelFunctionTest(name: String): String {
    return "$name-1"
}

fun Int.getStr(): String {
    return "Int value: $this"
}