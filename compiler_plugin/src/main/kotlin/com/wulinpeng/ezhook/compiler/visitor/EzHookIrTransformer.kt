package com.wulinpeng.ezhook.compiler.visitor

import com.wulinpeng.ezhook.compiler.EzHookInfo
import com.wulinpeng.ezhook.compiler.copyDeclarationToParent
import com.wulinpeng.ezhook.compiler.createPair
import com.wulinpeng.ezhook.compiler.getPairFirst
import com.wulinpeng.ezhook.compiler.getPairSecond
import com.wulinpeng.ezhook.compiler.getPairType
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

/**
 * author: wulinpeng
 * create: 2024/11/22 23:16
 * desc: transform functions which need to be hooked
 */
class EzHookIrTransformer(val collectInfos: EzHookInfo, val pluginContext: CommonBackendContext): IrElementTransformerVoidWithContext() {

    companion object {
        val LAST_PARAM_NAME = Name.identifier("ez_hook_origin")
        private val BACKING_FIELD_NAME = Name.identifier("ez_hook_backing_field")
        private const val NEW_EZ_SUFFIX = "ez_hook"
        private const val INLINE_EZ_SUFFIX = "ez_hook_inline"

    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitPropertyNew(property: IrProperty): IrStatement {
        val hookInfo = findHookPropertyInfo(property) ?: return super.visitPropertyNew(property)
        val hookProperty = if (hookInfo.inline) {
            hookInfo.property.copyDeclarationToParent("${hookInfo.property.name.asString()}_property_$INLINE_EZ_SUFFIX", property.parent)
        } else {
            hookInfo.property
        }

        // 1. transform the origin property to call the hook property
        println("EzHook: Hooking-property ${property.fqNameWhenAvailable} with ${hookProperty.name}")

        val targetInitializer = property.backingField?.initializer?.expression

        property.backingField?.let { targetField ->
            val hookField = hookProperty.backingField!!
            targetField.initializer = pluginContext.createIrBuilder(targetField.symbol).run {
                hookField.initializer?.expression?.let { expr ->
                    irExprBody(expr)
                }
            }
        }

        listOf(
            property.getter to hookProperty.getter,
            property.setter to hookProperty.setter
        ).forEach { (target,hook) ->
            if (target == null || hook == null) return@forEach
            val hookField = hookProperty.backingField
            val targetField = property.backingField

            if (hookField != null && targetField != null) {
                hookGetterOrSetter(target, hook,targetField,hookField)
            } else hookFunction(target, EzHookInfo.Function(hook, hook.kotlinFqName.asString(), hookInfo.inline),false)
        }

        hookProperty.backingField?.let { field ->
            var isCallOrigin = false
            field.transform(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol.owner.fqNameWhenAvailable?.asString() == CALL_ORIGIN) {
                        isCallOrigin = true
                        if (targetInitializer == null) {
                            println("EzHook: callOrigin cannot be executed because there is no field behind it")
                            return super.visitCall(expression)
                        }
                        return targetInitializer
                    } else if (expression.symbol.owner.fqNameWhenAvailable?.asString() == GET_THIS_REF) {
                        isCallOrigin = true
                        return pluginContext.createIrBuilder(expression.symbol).run {
                            irGet(property.getter?.dispatchReceiverParameter ?: error("Property should have dispatchReceiver in member case"))
                        }
                    } else {
                        return super.visitCall(expression)
                    }
                }
            },null)

            if (isCallOrigin) {
                field.initializer = null
            }
        }

        return property
    }

    private fun findHookPropertyInfo(property: IrProperty): EzHookInfo.Property? {
        val fqName = property.fqNameWhenAvailable?.asString()

        return collectInfos.property.filter { hookInfo ->
            hookInfo.targetPropertyFqName == fqName
        }.filter { hookInfo ->

            val hookProp = hookInfo.property

            val targetHasField = property.backingField != null
            val hookHasField = hookProp.backingField != null
            if (targetHasField != hookHasField) {
                println("EzHook: Property $fqName not matched (backingField mismatch: target=$targetHasField hook=$hookHasField)")
                return@filter false
            }

            val targetIsVar = property.isVar
            val hookHasIsVar = hookProp.isVar
            if (targetIsVar != hookHasIsVar) {
                println("EzHook: Property $fqName not matched (getter mismatch: target=${if (targetIsVar) "var" else "val"} hook=${if (hookHasIsVar) "var" else "val"})")
                return@filter false
            }

            true

        }.filter { hookInfo ->

            val targetType = property.backingField?.type ?: property.getter?.returnType
            val hookType = hookInfo.property.backingField?.type ?: hookInfo.property.getter?.returnType

            if (targetType == null) {
                println("EzHook: Property $fqName not matched (targetType null)")
                return@filter false
            }

            if (hookType == null) {
                println("EzHook: Property $fqName not matched (hookType null)")
                return@filter false
            }

            val targetTypeName = targetType.getClass()?.kotlinFqName?.asString()
            val hookTypeName = hookType.getClass()?.kotlinFqName?.asString()

            if (targetTypeName != hookTypeName) {
                println("EzHook: Property $fqName not matched (type mismatch: target=$targetTypeName hook=$hookTypeName)")
                return@filter false
            }

            true

        }.apply {

            if (size > 1) {
                println("EzHook: Property $fqName matched more than once!!! Candidates:")
                forEach { println(" - ${it.property.fqNameWhenAvailable}") }
            }

        }.firstOrNull()
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun hookGetterOrSetter(function: IrSimpleFunction, hookFunction: IrSimpleFunction, field: IrField, hookField: IrField) {
        // 1. copy the origin function for originCall
        val newFunction = function.copyDeclarationToParent("${function.name.asString()}_function_$NEW_EZ_SUFFIX")
        val hookFunctionImpl = hookFunction.copyDeclarationToParent(
            "${hookFunction.name}_impl",
            function.parent
        )
        if (function.isExternal) {
            function.isExternal = false
            println("EzHook: Trying to handle an external function ${function.name}, he cannot contain the function body, has canceled external but does not guarantee the running effect of callOrigin Note that it works")
        }
        // 2. transform the origin function to call the hook function
        val backing = hookFunctionImpl.addValueParameter(BACKING_FIELD_NAME, hookField.type)
        val oldStatements = hookFunctionImpl.body?.statements
        var backingVar: IrVariable? = null
        val isSet = hookFunctionImpl.returnType == pluginContext.irBuiltIns.unitType
        hookFunctionImpl.body = pluginContext.createIrBuilder(hookFunctionImpl.symbol).irBlockBody(hookFunctionImpl) {
            // *** 关键：把 backing 参数转成临时变量 ***
            backingVar = irTemporary(
                irGet(backing),
                nameHint = "backingVar",
                isMutable = true
            )
            if (oldStatements != null) +oldStatements
            if (isSet) {
                +pluginContext.createIrBuilder(hookFunctionImpl.symbol).run {
                    irReturn(pluginContext.createPair(this,irUnit(), irGet(backingVar)))
                }
            }
        }
        // 4. 替换 return → Pair(returnValue, backingVar)
        hookFunctionImpl.transform(
            object : IrElementTransformerVoid() {
                override fun visitReturn(expression: IrReturn): IrExpression {
                    // 只处理顶层 return
                    if (expression.returnTargetSymbol != hookFunctionImpl.symbol || isSet)
                        return super.visitReturn(expression)

                    return pluginContext.createIrBuilder(hookFunctionImpl.symbol).run {
                        irReturn(pluginContext.createPair(this,expression.value, irGet(backingVar!!)))
                    }
                }
            },
            null
        )

        // 5. 替换 GET/SET_FIELD
        hookFunctionImpl.transform(
            EzHookBackingFieldReplacer(
                field = hookField,
                backingVar = backingVar!!,
                context = pluginContext
            ),
            null
        )
        hookFunctionImpl.returnType = pluginContext.getPairType(hookFunctionImpl.returnType, backing.type)
        function.body = pluginContext.createIrBuilder(function.symbol).irBlockBody(function) {
            val receiver = function.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { oldReceiver ->
                hookFunctionImpl.addValueParameter(LAST_PARAM_NAME, oldReceiver.type)
            }
            val capturedField = irTemporary(
                irGetField(
                    function.dispatchReceiverParameter?.let { irGet(it) },
                    field
                )
            )
            val pair = createTmpVariable(
                irExpression = irCall(hookFunctionImpl).apply {
                    function.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }.forEachIndexed { index, param ->
                        arguments[index] = irGet(param)
                    }
                    // put dispatch receiver as the last param if exist
                    function.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { oldReceiver ->
                        arguments[receiver!!] = irGet(oldReceiver)
                    }

                    arguments[backing] = irGet(capturedField)
                },
                nameHint = "returnValue",
                origin = IrDeclarationOrigin.DEFINED
            )
            val result = getPairFirst(pluginContext,pair)
            val backingField = getPairSecond(pluginContext,pair)
            +irSetField(
                function.dispatchReceiverParameter?.let { irGet(it) },
                field,
                irGet(backingField)
            )
            +irReturn(irGet(result))
        }

        // 3. replace 'callOrigin()' of the hook function's body
        hookFunctionImpl.transform(EzHookCallOriginFunctionTransformer(hookFunctionImpl, newFunction, pluginContext), null)
    }
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    fun hookFunction(function: IrFunction, hookInfo: EzHookInfo.Function? = findHookFunctionInfo(function), isLogger: Boolean = true): IrStatement {
        val hookInfo = hookInfo ?: return super.visitFunctionNew(function)
        // 0. if hook function is set to inline, copy the function to the target function's parent
        val hookFunction = if (hookInfo.inline) {
            hookInfo.function.copyDeclarationToParent("${hookInfo.function.name.asString()}_function_$INLINE_EZ_SUFFIX", function.parent)
        } else {
            hookInfo.function
        }
        // 1. copy the origin function for originCall
        val newFunction = function.copyDeclarationToParent("${function.name.asString()}_function_$NEW_EZ_SUFFIX")
        if (function.isExternal) {
            function.isExternal = false
            println("EzHook: Trying to handle an external function ${function.name}, he cannot contain the function body, has canceled external but does not guarantee the running effect of callOrigin Note that it works")
        }
        // 2. transform the origin function to call the hook function
        if (isLogger) println("EzHook: Hooking-function ${function.kotlinFqName} with ${hookFunction.name}")

        function.body = pluginContext.createIrBuilder(function.symbol).irBlockBody(function) {
            val receiver = function.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { oldReceiver ->
                hookFunction.addValueParameter(LAST_PARAM_NAME, oldReceiver.type)
            }

            if (function is IrConstructor) {
                function.constructedClass.properties.forEach { property ->
                    if (property.backingField?.initializer != null) {
                        +irSetField(irGet(function.constructedClass.thisReceiver ?: error("not thisReceiver")), property.backingField!!,property.backingField!!.initializer!!.expression)
                    }
                }
            }

            val result = createTmpVariable(
                irExpression = irCall(hookFunction).apply {
                    function.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }.forEachIndexed { index, param ->
                        arguments[index] = irGet(param)
                    }
                    // put dispatch receiver as the last param if exist
                    function.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { oldReceiver ->
                        arguments[receiver!!] = irGet(oldReceiver)
                    }
                },
                nameHint = "returnValue",
                origin = IrDeclarationOrigin.DEFINED
            )
            +irReturn(irGet(result))
        }
        // 3. replace 'callOrigin()' of the hook function's body
        hookFunction.transform(EzHookCallOriginFunctionTransformer(hookFunction, newFunction, pluginContext), null)
        return function
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitFunctionNew(function: IrFunction): IrStatement = hookFunction(function)

    private fun findHookFunctionInfo(function: IrFunction): EzHookInfo.Function? {
        val fqName = function.kotlinFqName.asString()

        return collectInfos.functions.filter {
            it.targetFunctionFqName == fqName
        }.filter {

            val hookFunction = it.function
            val targetParams = function.parameters.filter { p -> p.kind != IrParameterKind.DispatchReceiver }
            val hookParams = hookFunction.parameters.filter { p -> p.kind != IrParameterKind.DispatchReceiver }

            if (hookParams.size != targetParams.size) {
                println("EzHook: Function $fqName not matched (parameter count mismatch: target=${targetParams.size} hook=${hookParams.size})")
                return@filter false
            }

            true

        }.filter {

            val hookFunction = it.function
            val targetParams = function.parameters.filter { p -> p.kind != IrParameterKind.DispatchReceiver }
            val hookParams = hookFunction.parameters.filter { p -> p.kind != IrParameterKind.DispatchReceiver }

            hookParams.zip(targetParams).all { (hookParam, param) ->
                val hookName = hookParam.type.getClass()!!.kotlinFqName.asString()
                val targetName = param.type.getClass()!!.kotlinFqName.asString()
                val ok = hookName == targetName
                if (!ok) {
                    println("EzHook: Function $fqName not matched (parameter type mismatch: hook=$hookName target=$targetName)")
                }
                ok
            }

        }.apply {

            if (size > 1) {
                println("EzHook: Function $fqName matched more than once!!! Candidates:")
                forEach { println(" - ${it.function.kotlinFqName}") }
            }

        }.firstOrNull()
    }
}

private const val CALL_ORIGIN = "com.wulinpeng.ezhook.runtime.callOrigin"

private const val GET_THIS_REF = "com.wulinpeng.ezhook.runtime.getThisRef"

class EzHookCallOriginFunctionTransformer(val hookFunction: IrFunction, val targetFunction: IrFunction, val context: CommonBackendContext): IrElementTransformerVoidWithContext() {

    /**
     * variables to override this function params
     */
    private val overrideParams = mutableListOf<IrVariable>()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        val name = declaration.name.asString()
        val type = declaration.type.getClass()!!.kotlinFqName.asString()
        if (hookFunction.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }.any {
            it.name.asString() == name && it.type.getClass()!!.kotlinFqName.asString() == type
        }) {
            overrideParams.add(declaration)
        }
        return super.visitVariable(declaration)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol.owner.fqNameWhenAvailable?.asString() == CALL_ORIGIN) {
            context.createIrBuilder(hookFunction.symbol).apply {
                return irCall(targetFunction.symbol).apply {
                    // for class member function, make the last param as dispatch receiver
                    hookFunction.parameters.find { it.name == EzHookIrTransformer.LAST_PARAM_NAME }?.let { thisRef  ->
                        arguments[targetFunction.parameters.first { it.kind == IrParameterKind.DispatchReceiver }] = irGet(thisRef)
                    }
                    targetFunction.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }.forEachIndexed { index, parameter ->
                        if (parameter.kind == IrParameterKind.ExtensionReceiver) {
                            val extension = hookFunction.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
                            if (extension == null) {
                                println("ExHook: Didn't find a match for the available extension parameters, didn't write an extension receiver?")
                                return@forEachIndexed
                            }
                            arguments[parameter] = irGet(extension)
                        } else {
                            val providedArgs = expression.arguments // 使用 IR API 拿传入的实际参数

                            if (providedArgs.isEmpty()) {
                                // case 1：callOrigin() 无参调用
                                // 用 hookFunction 的原始参数
                                arguments[parameter] = irGet(hookFunction.parameters[index])
                            } else {
                                // case 2：callOrigin(xxx) 有参调用
                                val providedArg = providedArgs.getOrNull(index)

                                // 如果参数是 var 覆盖 shadow variable，就用 overrideParams
                                val irVariable = overrideParams.find { it.name == parameter.name }

                                arguments[parameter] = when {
                                    providedArg != null -> providedArg
                                    irVariable != null -> irGet(irVariable)
                                    else -> irGet(hookFunction.parameters[index])
                                }
                            }
                        }
                    }
                }
            }
        } else if (expression.symbol.owner.fqNameWhenAvailable?.asString() == GET_THIS_REF) {
            context.createIrBuilder(hookFunction.symbol).apply {
                val getThis = hookFunction.parameters.find { it.name == EzHookIrTransformer.LAST_PARAM_NAME }?.let { thisRef ->
                    irGet(thisRef)
                }
                if (getThis == null) {
                    println("EzHook: getThisRef called in non-class member function")
                    return super.visitCall(expression)
                }
                return getThis
            }
        } else {
            return super.visitCall(expression)
        }
    }
}

class EzHookBackingFieldReplacer(
    val field: IrField,
    val backingVar: IrVariable,
    val context: CommonBackendContext
) : IrElementTransformerVoid() {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitGetField(expression: IrGetField): IrExpression {
        return if (expression.symbol.owner == field) {
            context.createIrBuilder(expression.symbol).irGet(backingVar)
        } else super.visitGetField(expression)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitSetField(expression: IrSetField): IrExpression {
        return if (expression.symbol.owner == field) {
            context.createIrBuilder(expression.symbol).irSet(backingVar, expression.value)
        } else super.visitSetField(expression)
    }
}