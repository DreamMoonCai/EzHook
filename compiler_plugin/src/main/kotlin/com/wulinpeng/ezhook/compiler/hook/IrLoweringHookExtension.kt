package com.wulinpeng.ezhook.compiler.hook

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * author: wulinpeng
 * create: 2024/11/25 22:13
 * desc: Hook ir lowering phase to add custom lowering logic, compat for KN & KJS
 */
interface IrLoweringHookExtension {
    companion object {
        private val extensions = mutableListOf<IrLoweringHookExtension>()

        init {
            runHook({ context, module ->
                extensions.forEach {
                    it.traverse(context, module)
                }
            }, { context, module ->
                extensions.forEach {
                    it.transform(context, module)
                }
            })
        }

        fun runHook(traverser: (CommonBackendContext, IrModuleFragment) -> Unit,
                    transformer: (CommonBackendContext, IrModuleFragment) -> Unit) {
            NativeIrLoweringHook.runHook(traverser, transformer)
            JsIrLoweringHook.runHook(traverser, transformer)
        }

        fun registerExtension(extension: IrLoweringHookExtension) {
            extensions.add(extension)
        }
    }

    fun traverse(context: CommonBackendContext, module: IrModuleFragment)

    fun transform(context: CommonBackendContext, module: IrModuleFragment)
}