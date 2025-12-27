package com.wulinpeng.ezhook.compiler.hook

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.phaser.IrValidationBeforeLoweringPhase
import org.jetbrains.kotlin.backend.common.phaser.KlibIrValidationBeforeLoweringPhase
import org.jetbrains.kotlin.config.LoggingContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * desc: Hook ir lowering phase to add custom lowering logic
 *
 * @author wulinpeng
 * @since 2024/11/21 22:13
 */
object NativeIrLoweringHook {
    private const val NATIVE_LOWERING_PHASES_CLASS = "org.jetbrains.kotlin.backend.konan.driver.phases.NativeLoweringPhasesKt"

    fun runHook(traverser: (CommonBackendContext, IrModuleFragment) -> Unit,
                        transformer: (CommonBackendContext, IrModuleFragment) -> Unit,
                        beforeGenLLVMIR: (CommonBackendContext, IrFile) -> Unit = { _, _ -> }) {
        runCatching {
            val allModules = mutableListOf<IrModuleFragment>()
            hookValidateIrBeforeLowering(allModules, traverser)
            hookLowerBeforeInlinePhase {
                allModules.forEach { module ->
                    transformer(it, module)
                }
            }
            hookReturnsInsertionPhase { commonBackendContext, irFile ->
                beforeGenLLVMIR(commonBackendContext, irFile)
            }
        }.getOrElse { println(it.stackTraceToString()) }
    }

    private fun hookValidateIrBeforeLowering(allModules: MutableList<IrModuleFragment>, transformer: (CommonBackendContext, IrModuleFragment) -> Unit) {
        val clazz = Class.forName(NATIVE_LOWERING_PHASES_CLASS)
        val lower = clazz.declaredFields.firstOrNull { it.name == "validateIrBeforeLowering" }?.apply {
            isAccessible = true
        }!!.get(null)
        lower.javaClass.getDeclaredField("\$op").apply {
            isAccessible = true
        }.set(lower, {context: LoggingContext, module: IrModuleFragment ->
            val innerContext = context.javaClass.getDeclaredField("context").apply {
                isAccessible = true
            }.get(context) as CommonBackendContext
            transformer(innerContext, module)
            allModules.add(module)
            // call origin
            KlibIrValidationBeforeLoweringPhase(innerContext).lower(module)
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookLowerBeforeInlinePhase(onStart: (context: CommonBackendContext) -> Unit){
        val clazz = Class.forName(NATIVE_LOWERING_PHASES_CLASS)
        val lower = clazz.declaredFields.firstOrNull { it.name == "upgradeCallableReferencesPhase" }?.apply {
            isAccessible = true
        }!!.get(null)
        val opField = lower.javaClass.declaredFields.firstOrNull { it.name == "\$op" }?.apply {
            isAccessible = true
        }!!
        val originOp = opField.get(lower)
        var hasStart = false
        opField.set(lower, {context: LoggingContext, irFile: IrFile ->
            val innerContext = context.javaClass.getDeclaredField("context").apply {
                isAccessible = true
            }.get(context)
            if (!hasStart) {
                onStart(innerContext as CommonBackendContext)
                hasStart = true
            }
            // call origin
            (originOp as (Any, IrFile) -> IrFile).invoke(context, irFile)
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookReturnsInsertionPhase(transformer: (CommonBackendContext, IrFile) -> Unit){
        val clazz = Class.forName(NATIVE_LOWERING_PHASES_CLASS)
        val lower = clazz.declaredFields.firstOrNull { it.name == "ReturnsInsertionPhase" }?.apply {
            isAccessible = true
        }!!.get(null)
        val opField = lower.javaClass.declaredFields.firstOrNull { it.name == "\$op" }?.apply {
            isAccessible = true
        }!!
        val originOp = opField.get(lower)
        opField.set(lower, { context: LoggingContext, irFile: IrFile ->
            val innerContext = context.javaClass.getDeclaredField("context").apply {
                isAccessible = true
            }.get(context)
            transformer(innerContext as CommonBackendContext, irFile)
            // call origin
            (originOp as (Any, IrFile) -> IrFile).invoke(context, irFile)
        })
    }
}