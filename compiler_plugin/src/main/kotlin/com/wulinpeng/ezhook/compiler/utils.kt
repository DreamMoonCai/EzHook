package com.wulinpeng.ezhook.compiler

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.InternalSymbolFinderAPI
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.setDeclarationsParent
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * author: wulinpeng
 * create: 2024/11/22 00:09
 * desc:
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrConstructorCall.defaultParamValue(index: Int): IrExpression? {
    return symbol.owner.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }[1].defaultValue?.expression
}

fun IrDeclarationBase.isClassMember(): Boolean {
    return parent is IrClass
}

inline fun <reified T> T.copyDeclarationToParent(newName: String, newParent: IrDeclarationParent = parent): T where T : IrDeclarationBase, T : IrDeclarationWithName {
    return deepCopyWithSymbols(newParent).apply {
        name = Name.identifier(newName)
        setDeclarationsParent(newParent)
        (newParent as IrDeclarationContainer).addChild(this)
    }
}

@OptIn(InternalSymbolFinderAPI::class, UnsafeDuringIrConstructionAPI::class)
private fun CommonBackendContext.getPairClass(): IrClass {
    val pairClassSymbol = irBuiltIns.symbolFinder.findClass(
        ClassId.topLevel(FqName("kotlin.Pair"))
    ) ?: error("Pair class not found")

    return pairClassSymbol.owner
}

fun CommonBackendContext.getPairType(firstType: IrType = irBuiltIns.anyType.makeNullable(), secondType: IrType = irBuiltIns.anyType.makeNullable()): IrType {
    val pairClass = getPairClass()
    return pairClass.typeWith(firstType, secondType)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun CommonBackendContext.getPairConstructor(): IrConstructor {
    val pairClass = getPairClass()
    return pairClass.constructors.first()
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun CommonBackendContext.createPair(
    builder: IrBuilder,
    value1: IrExpression?,
    value2: IrExpression?
): IrExpression {
    val constructor = getPairConstructor()

    return builder.run {
        irCallConstructor(constructor.symbol, listOf(value1?.type ?: irBuiltIns.anyType.makeNullable(), value2?.type ?: irBuiltIns.anyType.makeNullable())).apply {
            arguments[0] = value1
            arguments[1] = value2
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun CommonBackendContext.getPairComponent1(): IrSimpleFunction {
    val pairClass = getPairClass()
    return pairClass.functions.first { it.name.asString() == "component1" }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun CommonBackendContext.getPairComponent2(): IrSimpleFunction {
    val pairClass = getPairClass()
    return pairClass.functions.first { it.name.asString() == "component2" }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrStatementsBuilder<*>.getPairFirst(
    pluginContext: CommonBackendContext,
    pairVar: IrVariable
): IrVariable {
    val comp1 = pluginContext.getPairComponent1()

    return irTemporary(
        irCall(comp1.symbol).apply {
            dispatchReceiver = irGet(pairVar)
        },
        nameHint = "pair_first"
    )
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrStatementsBuilder<*>.getPairSecond(
    pluginContext: CommonBackendContext,
    pairVar: IrVariable
): IrVariable {
    val comp2 = pluginContext.getPairComponent2()

    return irTemporary(
        irCall(comp2.symbol).apply {
            dispatchReceiver = irGet(pairVar)
        },
        nameHint = "pair_second"
    )
}