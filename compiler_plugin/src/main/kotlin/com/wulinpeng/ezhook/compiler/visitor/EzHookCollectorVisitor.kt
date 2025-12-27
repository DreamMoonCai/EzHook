@file:OptIn(UnsafeCastFunction::class)

package com.wulinpeng.ezhook.compiler.visitor

import com.wulinpeng.ezhook.compiler.EzHookInfo
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction

/**
 * collect functions with @EzHook annotation
 *
 * @author wulinpeng
 * @since 2024/11/22 23:15
 */
class EzHookCollectorVisitor(val collectInfo: EzHookInfo): IrVisitor<Unit, Nothing?>() {

    companion object {
        val EzHookClass = FqName("com.wulinpeng.ezhook.runtime.EzHook")

        val EzHookBeforeClass = FqName("com.wulinpeng.ezhook.runtime.EzHook.Before")

        val EzHookAfterClass = FqName("com.wulinpeng.ezhook.runtime.EzHook.After")

        val EzHookNullClass = FqName("com.wulinpeng.ezhook.runtime.EzHook.NULL")
    }

    override fun visitElement(element: IrElement, data: Nothing?) {
    }

    override fun visitModuleFragment(declaration: IrModuleFragment, data: Nothing?) {
        println("EzHook: visitModuleFragment ${declaration.name}")
        declaration.acceptChildren(this, data)
    }

    override fun visitFile(declaration: IrFile, data: Nothing?) {
        declaration.acceptChildren(this, data)
    }

    override fun visitClass(declaration: IrClass, data: Nothing?) {
        declaration.acceptChildren(this, data)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun <T> visitFunctionOrProperty(declaration: T) where T: IrDeclarationWithVisibility,T: IrDeclarationWithName {
        val isFunction = declaration is IrFunction

        val isReplace = declaration.hasAnnotation(EzHookClass)

        val isBefore = declaration.hasAnnotation(EzHookBeforeClass)

        val isAfter = declaration.hasAnnotation(EzHookAfterClass)

        val isNull = declaration.hasAnnotation(EzHookNullClass)

        if (isReplace || isBefore || isAfter || isNull) {
            if (!declaration.isTopLevel) {
                error("EzHook annotation can only be used on top level ${if (isFunction) "functions" else "property"}")
            }
            if (declaration.visibility != DescriptorVisibilities.PUBLIC && declaration.visibility != DescriptorVisibilities.INTERNAL) {
                error("EzHook annotation can only be used on public/internal ${if (isFunction) "functions" else "property"}")
            }
            val anno = declaration.getAnnotation(when {
                isBefore -> EzHookBeforeClass
                isAfter -> EzHookAfterClass
                isNull -> EzHookNullClass
                else -> EzHookClass
            })
            val parameters = anno.symbol.owner.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }
            val targetFqName = (anno.arguments[parameters[0]] as IrConst).value as String
            val inline = parameters[1].let { inlineParam ->
                (anno.arguments[inlineParam] as? IrConst)?.value as? Boolean ?: (inlineParam.defaultValue?.expression as? IrConst)?.value as? Boolean ?: false
            }
            val isInitializeProperty = parameters[2].let { isInitializePropertyParam ->
                (anno.arguments[isInitializePropertyParam] as? IrConst)?.value as? Boolean ?: (isInitializePropertyParam.defaultValue?.expression as? IrConst)?.value as? Boolean ?: false
            }
            println("EzHook: visit @EzHook ${if (isFunction) "Function" else "Property"} ${declaration.name} with target ${if (isFunction) "function" else "property"} $targetFqName")
            if (isFunction)
                collectInfo.functions.add(EzHookInfo.Function(declaration, targetFqName, inline,isInitializeProperty,isBefore, isAfter, isNull))
            else if (declaration is IrProperty)
                collectInfo.property.add(EzHookInfo.Property(declaration, targetFqName, inline, isBefore, isAfter, isNull))
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitFunction(declaration: IrFunction, data: Nothing?) {
        visitFunctionOrProperty(declaration)
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?) {
        visitFunctionOrProperty(declaration)
        listOfNotNull(declaration.getter,declaration.setter).forEach {
            if (it.hasAnnotation(EzHookNullClass)) visitFunctionOrProperty(it)
        }
    }

}