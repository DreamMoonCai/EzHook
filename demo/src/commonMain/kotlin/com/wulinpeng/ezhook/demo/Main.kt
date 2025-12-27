package com.wulinpeng.ezhook.demo

import com.wulinpeng.ezhook.demov2.NormalTest
import com.wulinpeng.ezhook.demov2.getStr
import com.wulinpeng.ezhook.demov2.topLevelFunctionTest
import com.wulinpeng.ezhook.demov2.topLevelPropertyTest
import com.wulinpeng.ezhook.runtime.EzHook
import com.wulinpeng.ezhook.runtime.callOrigin
import com.wulinpeng.ezhook.runtime.getField
import com.wulinpeng.ezhook.runtime.getThisProperty
import com.wulinpeng.ezhook.runtime.getThisRef
import com.wulinpeng.ezhook.runtime.setField
import com.wulinpeng.ezhook.runtime.setThisProperty
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Duration
import kotlin.time.DurationUnit

@OptIn(ExperimentalNativeApi::class)
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

    testCase("NormalCase3",test.testReParam("origin name",18))

    testCase("NormalCase4",test.testLazy)

}

fun testCase(caseName: String, result: String) {
    println("Test case $caseName: $result")
}