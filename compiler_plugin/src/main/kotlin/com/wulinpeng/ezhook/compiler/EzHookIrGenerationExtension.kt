@file:OptIn(UnsafeCastFunction::class)

package com.wulinpeng.ezhook.compiler

import com.wulinpeng.ezhook.compiler.hook.IrLoweringHookExtension
import com.wulinpeng.ezhook.compiler.visitor.EzHookCollectorVisitor
import com.wulinpeng.ezhook.compiler.visitor.EzHookIrTransformer
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction

class EzHookExtension: IrLoweringHookExtension {
    val collectInfo = EzHookInfo()

    override fun traverse(context: CommonBackendContext, module: IrModuleFragment) {
        module.accept(EzHookCollectorVisitor(collectInfo), null)
    }

    override fun transform(context: CommonBackendContext, module: IrModuleFragment) {
        module.transform(EzHookIrTransformer(collectInfo, context), null)
    }
}

data class EzHookInfo(val functions: MutableList<Function> = mutableListOf(),val property: MutableList<Property> = mutableListOf()) {
    data class Function(val function: IrFunction, val targetFunctionFqName: String, val inline: Boolean)

    data class Property(val property: IrProperty, val targetPropertyFqName: String, val inline: Boolean)
}