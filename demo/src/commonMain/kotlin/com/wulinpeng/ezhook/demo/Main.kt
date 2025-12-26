package com.wulinpeng.ezhook.demo

import com.wulinpeng.ezhook.demov2.NormalTest
import com.wulinpeng.ezhook.demov2.getStr
import com.wulinpeng.ezhook.demov2.topLevelFunctionTest
import com.wulinpeng.ezhook.demov2.topLevelPropertyTest
import kotlin.time.Duration
import kotlin.time.DurationUnit

fun main() {
    val test = NormalTest("origin name")
    testCase("NormalCase", test.test("origin name"))
    testCase("TopLevelCase", topLevelFunctionTest("origin name"))
    testCase("ExtendFunctionCase", 10.getStr())
    testCase("DurationHook", "${Duration.ZERO.toInt(DurationUnit.SECONDS)}")

    testCase("TopLevelCase2", topLevelPropertyTest)
    topLevelPropertyTest = "100"
    testCase("TopLevelCase2", topLevelPropertyTest)

    testCase("NormalCase2",test.testProperty)
    test.testProperty = "200"
    testCase("NormalCase2",test.testProperty)
}

fun testCase(caseName: String, result: String) {
    println("Test case $caseName: $result")
}
