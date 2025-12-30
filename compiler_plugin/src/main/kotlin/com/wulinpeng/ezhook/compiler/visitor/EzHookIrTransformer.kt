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
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

/**
 * transform functions which need to be hooked
 *
 * @author wulinpeng
 * @since 2024/11/22 23:16
 */
class EzHookIrTransformer(val collectInfos: EzHookInfo, val pluginContext: CommonBackendContext) :
    IrElementTransformerVoidWithContext() {

    companion object {
        val THIS_REF_PARAM_NAME = Name.identifier("ez_hook_this_ref")
        val BACKING_FIELD_PARAM_NAME = Name.identifier("ez_hook_backing_field")
        private const val NEW_EZ_SUFFIX = "ez_hook"
        private const val INLINE_EZ_SUFFIX = "ez_hook_inline"

    }
    val processedDeclaration = mutableListOf<IrDeclarationBase>()

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitPropertyNew(property: IrProperty): IrStatement {
        val hookInfo = findHookPropertyInfo(property) ?: return super.visitPropertyNew(property)
        if (processedDeclaration.contains(property)) return super.visitPropertyNew(property)
        processedDeclaration.add(property)
        val hookProperty = if (hookInfo.inline) {
            hookInfo.property.copyDeclarationToParent(
                "${hookInfo.property.fqNameWhenAvailable?.asString()?.replace(".", "_") ?: hookInfo.property.name.asString()}_property_$INLINE_EZ_SUFFIX",
                property.parent
            )
        } else {
            hookInfo.property
        }

        // 1. transform the origin property to call the hook property
        println("EzHook: Hooking-property ${property.fqNameWhenAvailable} with ${hookProperty.name}")

        val targetInitializer = property.backingField?.initializer?.expression

        if (!property.isDelegated) {
            if (hookInfo.isReplace)
                property.backingField?.let { targetField ->
                    val hookField = hookProperty.backingField!!
                    targetField.initializer = pluginContext.createIrBuilder(targetField.symbol).run {
                        hookField.initializer?.expression?.let { expr ->
                            irExprBody(expr)
                        }
                    }
                } else if (hookInfo.isNull)
                property.backingField?.initializer =
                    pluginContext.createIrBuilder(property.symbol).run { irExprBody(irNull()) }
        }

        listOf(
            property.getter to hookProperty.getter,
            property.setter to hookProperty.setter
        ).forEach { (target, hook) ->
            if (target == null || hook == null) return@forEach
            val hookField = hookProperty.backingField
            val targetField = property.backingField

            if (targetField != null) {
                hookGetterOrSetter(target, hook, hookInfo, targetField, hookField)
            } else hookFunction(
                target,
                EzHookInfo.Function(
                    hook,
                    hook.kotlinFqName.asString(),
                    hookInfo.inline,
                    false,
                    hookInfo.isBefore,
                    hookInfo.isAfter,
                    hookInfo.isNull
                ),
                false
            )
        }

        hookProperty.backingField?.let { field ->
            var isCallOrigin = false
            field.transform(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression =
                    when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {
                        CALL_ORIGIN -> {
                            isCallOrigin = true
                            if (targetInitializer == null) {
                                println("EzHook: callOrigin cannot be executed because there is no field behind it")
                                super.visitCall(expression)
                            } else targetInitializer
                        }

                        GET_THIS_REF -> {
                            isCallOrigin = true
                            pluginContext.createIrBuilder(expression.symbol).run {
                                irGet(
                                    property.getter?.dispatchReceiverParameter
                                        ?: error("Property should have dispatchReceiver in member case in ${property.fqNameWhenAvailable}")
                                )
                            }
                        }

                        GET_FIELD, SET_FIELD -> error("EzHook: Trying to get the back field in the back field? in ${hookProperty.fqNameWhenAvailable}")

                        else -> EzHookCallOriginTransformer.visitThisRefProperty(
                            expression,
                            property.getter?.dispatchReceiverParameter,
                            pluginContext
                        )?.also { isCallOrigin = true }
                            ?: super.visitCall(expression)
                    }
            }, null)

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
            if (targetHasField != hookHasField && !property.isDelegated) {
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

            val targetType = property.getter?.returnType ?: property.backingField?.type
            val hookType = hookInfo.property.getter?.returnType ?: hookInfo.property.backingField?.type

            if (hookInfo.property.backingField != null && property.isDelegated) {
                println("EzHook: Property $fqName does not support modifying the back field of by")
                return@filter false
            }

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

            if (targetTypeName != hookTypeName && hookInfo.isReplace) {
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
    fun hookGetterOrSetter(
        function: IrSimpleFunction,
        hookFunction: IrSimpleFunction,
        hookInfo: EzHookInfo.Property,
        field: IrField,
        hookField: IrField?
    ) {
        if (processedDeclaration.contains(function)) return
        processedDeclaration.add(function)
        // 1. copy the origin function for originCall
        val newFunction = if (!function.name.asString().endsWith("_function_$NEW_EZ_SUFFIX"))
            function.copyDeclarationToParent("${function.name.asString()}_function_$NEW_EZ_SUFFIX")
        else function
        val hookFunctionImpl = if (hookInfo.inline && hookFunction.name.asString() != "${hookFunction.fqNameWhenAvailable?.asString()?.replace(".","_") ?: hookFunction.name.asString()}_function_$INLINE_EZ_SUFFIX")
            hookFunction.copyDeclarationToParent(
                "${hookFunction.fqNameWhenAvailable?.asString()?.replace(".","_") ?: hookFunction.name.asString()}_function_$INLINE_EZ_SUFFIX",
                function.parent
            )
        else hookFunction
        if (function.isExternal) {
            function.isExternal = false
            println("EzHook: Trying to handle an external function ${function.name}, he cannot contain the function body, has canceled external but does not guarantee the running effect of callOrigin Note that it works")
        }
        // 2. transform the origin function to call the hook function
        val backing = hookFunctionImpl.addValueParameter(BACKING_FIELD_PARAM_NAME, field.type)
        val oldStatements = hookFunctionImpl.body?.statements
        var backingVar: IrVariable? = null
        val isSet = function.returnType == pluginContext.irBuiltIns.unitType
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
                    irReturn(pluginContext.createPair(this, irUnit(), irGet(backingVar)))
                }
            }
        }

        requireNotNull(backingVar)

        // 4. 替换 return → Pair(returnValue, backingVar)
        hookFunctionImpl.transform(
            object : IrElementTransformerVoid() {
                override fun visitReturn(expression: IrReturn): IrExpression {
                    // 只处理顶层 return
                    if (expression.returnTargetSymbol != hookFunctionImpl.symbol || isSet)
                        return super.visitReturn(expression)

                    return pluginContext.createIrBuilder(hookFunctionImpl.symbol).run {
                        irReturn(pluginContext.createPair(this, expression.value, irGet(backingVar)))
                    }
                }
            },
            null
        )

        // 5. 替换 GET/SET_FIELD
        if (hookField != null) {
            hookFunctionImpl.transform(
                EzHookBackingFieldReplacer(
                    field = hookField,
                    backingVar = backingVar,
                    context = pluginContext
                ),
                null
            )
        }
        hookFunctionImpl.returnType = pluginContext.getPairType(function.returnType, backing.type)
        fun IrBuilder.callGetterSetter(capturedField: IrVariable) = callHookFunction(function, hookFunctionImpl).apply {
            arguments[backing] = irGet(capturedField)
        }

        function.body = pluginContext.createIrBuilder(function.symbol).irBlockBody(function) {
            if (hookInfo.isNull) {
                +irReturn(irNull())
                return@irBlockBody
            }
            val capturedField = irTemporary(
                irGetField(
                    function.dispatchReceiverParameter?.let { irGet(it) },
                    field
                )
            )
            if (hookInfo.isBefore) {
                val pair = createTmpVariable(
                    irExpression = callGetterSetter(capturedField),
                    nameHint = "returnValue",
                    origin = IrDeclarationOrigin.DEFINED
                )
                val backingField = getPairSecond(pluginContext, pair)
                +irSetField(
                    function.dispatchReceiverParameter?.let { irGet(it) },
                    field,
                    irGet(backingField)
                )
            }
            if (hookInfo.isReplace) {
                val pair = createTmpVariable(
                    irExpression = callGetterSetter(capturedField),
                    nameHint = "returnValue",
                    origin = IrDeclarationOrigin.DEFINED
                )
                val result = getPairFirst(pluginContext, pair)
                val backingField = getPairSecond(pluginContext, pair)
                +irSetField(
                    function.dispatchReceiverParameter?.let { irGet(it) },
                    field,
                    irGet(backingField)
                )
                +irReturn(irGet(result))
                return@irBlockBody
            }
            val result = createTmpVariable(
                irExpression = irBlock(resultType = function.returnType) { +irCall(newFunction).apply {
                    function.parameters.forEachIndexed { index, parameter ->
                        arguments[index] = irGet(parameter)
                    }
                } },
                nameHint = "originalReturnValue",
                origin = IrDeclarationOrigin.DEFINED
            )
            if (hookInfo.isAfter) {
                val pair = createTmpVariable(
                    irExpression = callGetterSetter(capturedField),
                    nameHint = "returnValue",
                    origin = IrDeclarationOrigin.DEFINED
                )
                if (pair.type != pluginContext.irBuiltIns.unitType) {
                    +irReturn(irGet(pair))
                    return@irBlockBody
                }
            }
            +irReturn(irGet(result))
        }

        // 3. replace 'callOrigin()' of the hook function's body
        hookFunctionImpl.transform(
            EzHookCallOriginTransformer(hookFunctionImpl, newFunction, true, pluginContext),
            null
        )

        hookFunctionImpl.transform(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression =
                pluginContext.createIrBuilder(expression.symbol).run {
                    when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {

                        GET_FIELD -> irGet(backingVar)

                        SET_FIELD -> expression.arguments.getOrNull(0)?.let { irSet(backingVar, it) }

                        else -> null
                    }
                } ?: super.visitCall(expression)
        }, null)
    }

    fun getThisReceiver(function: IrFunction) = if (function is IrConstructor)
        function.constructedClass.thisReceiver
    else
        function.dispatchReceiverParameter

    fun IrBuilder.callHookFunction(function: IrFunction, hookFunction: IrFunction): IrFunctionAccessExpression {
        val receiver = getThisReceiver(function)?.let { oldReceiver ->
            hookFunction.parameters.find { it.name == THIS_REF_PARAM_NAME } ?: hookFunction.addValueParameter(
                THIS_REF_PARAM_NAME,
                oldReceiver.type
            )
        }
        return irCall(hookFunction).apply {
            function.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }
                .forEachIndexed { index, param ->
                    arguments[index] = irGet(param)
                }
            // put dispatch receiver as the last param if exist
            if (function is IrConstructor)
                arguments[receiver!!] =
                    irGet(function.constructedClass.thisReceiver ?: error("not thisReceiver in $function"))
            else function.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { oldReceiver ->
                arguments[receiver!!] = irGet(oldReceiver)
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    fun hookFunction(
        function: IrFunction,
        hookInfo: EzHookInfo.Function? = findHookFunctionInfo(function),
        isLogger: Boolean = true
    ): IrStatement? {
        if ((function as? IrSimpleFunction)?.correspondingPropertySymbol != null) return null
        val hookInfo = hookInfo ?: return null
        if (processedDeclaration.contains(function)) return null
        processedDeclaration.add(function)
        // 0. if hook function is set to inline, copy the function to the target function's parent
        val hookFunction = if (hookInfo.inline && hookInfo.function.name.asString() != "${hookInfo.function.fqNameWhenAvailable?.asString()?.replace(".","_") ?: hookInfo.function.name.asString()}_function_$INLINE_EZ_SUFFIX") {
            hookInfo.function.copyDeclarationToParent(
                "${hookInfo.function.fqNameWhenAvailable?.asString()?.replace(".","_") ?: hookInfo.function.name.asString()}_function_$INLINE_EZ_SUFFIX",
                function.parent
            )
        } else {
            hookInfo.function
        }
        // 1. copy the origin function for originCall
        val newFunction = if (!function.name.asString().endsWith("_function_$NEW_EZ_SUFFIX"))
            function.copyDeclarationToParent("${function.name.asString()}_function_$NEW_EZ_SUFFIX")
        else function
        if (function.isExternal) {
            function.isExternal = false
            println("EzHook: Trying to handle an external function ${function.name}, he cannot contain the function body, has canceled external but does not guarantee the running effect of callOrigin Note that it works")
        }
        // 2. transform the origin function to call the hook function
        if (isLogger) println("EzHook: Hooking-function ${function.kotlinFqName} with ${hookFunction.name}")

        function.body = pluginContext.createIrBuilder(function.symbol).irBlockBody(function) {
            if (function is IrConstructor && hookInfo.isInitializeProperty && function.isPrimary) {
                (function.constructedClass.properties + function.constructedClass.getAllSuperclasses()
                    .flatMap { it.properties }).forEach { property ->
                    val backingField = property.backingField ?: return@forEach
                    val initializer = backingField.initializer
                    if (initializer != null) {
                        val thisRefParam =
                            function.constructedClass.thisReceiver ?: error("not thisReceiver in $function")
                        var isRefThis = false
                        initializer.transform(object : IrElementTransformerVoid() {

                            override fun visitGetValue(expression: IrGetValue): IrExpression {
                                if (expression.symbol.owner == thisRefParam) {
                                    isRefThis = true
                                }
                                return super.visitGetValue(expression)
                            }
                        },null)
                        if (property.isDelegated && isRefThis) {
                            println("EzHook: It is not possible to set an initial value for a delegate property, you may need to manually set the initial value in ${property.fqNameWhenAvailable}")
                        } else {
                            +irSetField(
                                irGet(thisRefParam),
                                backingField,
                                initializer.expression
                            )
                        }
                    }
                }
            }
            fun returnValue(value: IrExpression) {
                if (function is IrConstructor)
                    +value
                else
                    +irReturn(value)
            }
            if (hookInfo.isNull) {
                returnValue(irNull())
                return@irBlockBody
            }
            if (hookInfo.isBefore) {
                +callHookFunction(function, hookFunction)
            }
            if (hookInfo.isReplace) {
                returnValue(
                    irGet(
                        createTmpVariable(
                            irExpression = callHookFunction(function, hookFunction),
                            nameHint = "returnValue",
                            origin = IrDeclarationOrigin.DEFINED
                        )
                    )
                )
                return@irBlockBody
            }
            val result = createTmpVariable(
                irExpression = irBlock(resultType = function.returnType) { +irCall(newFunction).apply {
                    function.parameters.forEachIndexed { index, parameter ->
                        arguments[index] = irGet(parameter)
                    }
                } },
                nameHint = "originalReturnValue",
                origin = IrDeclarationOrigin.DEFINED
            )
            if (hookInfo.isAfter) {
                val result = createTmpVariable(
                    irExpression = callHookFunction(function, hookFunction),
                    nameHint = "returnValue",
                    origin = IrDeclarationOrigin.DEFINED
                )
                if (result.type != pluginContext.irBuiltIns.unitType) {
                    returnValue(irGet(result))
                    return@irBlockBody
                }
            }
            returnValue(irGet(result))
        }
        // 3. replace 'callOrigin()' of the hook function's body
        hookFunction.transform(EzHookCallOriginTransformer(hookFunction, newFunction, false, pluginContext), null)
        return function
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitFunctionNew(function: IrFunction): IrStatement = hookFunction(function) ?: super.visitFunctionNew(function)

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

private const val GET_FIELD = "com.wulinpeng.ezhook.runtime.getField"

private const val SET_FIELD = "com.wulinpeng.ezhook.runtime.setField"

private const val SET_THIS_PROPERTY = "com.wulinpeng.ezhook.runtime.setThisProperty"

private const val GET_THIS_PROPERTY = "com.wulinpeng.ezhook.runtime.getThisProperty"

class EzHookCallOriginTransformer(
    val hookFunction: IrFunction,
    val targetFunction: IrFunction,
    val isGetterSetter: Boolean,
    val context: CommonBackendContext,
) : IrElementTransformerVoidWithContext() {

    companion object {
        @OptIn(UnsafeDuringIrConstructionAPI::class)
        fun visitThisRefProperty(
            expression: IrCall,
            thisRef: IrValueParameter?,
            context: CommonBackendContext
        ): IrExpression? = context.createIrBuilder(expression.symbol).run {
            val parameters = expression.symbol.owner.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }
            when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {
                SET_THIS_PROPERTY -> {
                    val setThisProperty =
                        thisRef
                            ?.let { thisRef ->
                                val name = parameters.getOrNull(0)
                                    ?.let { name -> (expression.arguments[name] as? IrConst)?.value as String? }
                                    ?: return@let null
                                val value = expression.arguments.getOrNull(1) ?: return@let null
                                val isBackingField = parameters.getOrNull(2)
                                    ?.let { isBackingField ->
                                        (expression.arguments[isBackingField] as? IrConst)?.value as Boolean?
                                            ?: (isBackingField.defaultValue?.expression as? IrConst)?.value as Boolean?
                                            ?: false
                                    }
                                    ?: return@let null
                                val property = thisRef.type.getClass()?.properties?.find { it.name.asString() == name }
                                    ?: return@let null
                                val setter = property.setter
                                val setterParam = setter?.parameters?.find { it.kind == IrParameterKind.Regular }
                                val field = property.backingField
                                if (setter != null && setterParam != null && (!isBackingField || field == null)) {
                                    irCall(setter.symbol).apply {
                                        dispatchReceiver = irGet(thisRef)
                                        arguments[setterParam] = value
                                    }
                                } else if (field != null) {
                                    irSetField(
                                        receiver = irGet(thisRef),
                                        field = field,
                                        value = value
                                    )
                                } else {
                                    null
                                }
                            }
                    if (setThisProperty == null) {
                        println("EzHook: This function/property has no backing field / setter and cannot use setThisProperty")
                        null
                    } else setThisProperty
                }

                GET_THIS_PROPERTY -> {
                    val setThisProperty =
                        thisRef?.let { thisRef ->
                            val name = parameters.getOrNull(0)
                                ?.let { name -> (expression.arguments[name] as? IrConst)?.value as String? }
                                ?: return@let null
                            val isBackingField = parameters.getOrNull(1)
                                ?.let { isBackingField ->
                                    (expression.arguments[isBackingField] as? IrConst)?.value as Boolean?
                                        ?: (isBackingField.defaultValue?.expression as? IrConst)?.value as Boolean?
                                        ?: false
                                }
                                ?: return@let null
                            val property = thisRef.type.getClass()?.properties?.find { it.name.asString() == name }
                                ?: return@let null
                            val getter = property.getter
                            val field = property.backingField
                            if (getter != null && (!isBackingField || field == null)) {
                                irCall(getter.symbol).apply {
                                    dispatchReceiver = irGet(thisRef)
                                }
                            } else if (field != null) {
                                irGetField(
                                    receiver = irGet(thisRef),
                                    field = field
                                )
                            } else {
                                null
                            }
                        }
                    if (setThisProperty == null) {
                        println("EzHook: This function/property has no backing field / getter and cannot use getThisProperty")
                        null
                    } else setThisProperty
                }

                else -> null
            }
        }

        @OptIn(UnsafeDuringIrConstructionAPI::class)
        fun buildCallOrigin(builder: IrBuilderWithScope,targetFunction: IrFunction, hookFunction: IrFunction, argument: IrMemberAccessExpression<*>.ValueArgumentsList) = builder.run {
            irCall(targetFunction.symbol).apply {
                // for class member function, make the last param as dispatch receiver
                hookFunction.parameters.find { it.name == EzHookIrTransformer.THIS_REF_PARAM_NAME }?.let { thisRef ->
                    targetFunction.parameters.find { it.kind == IrParameterKind.DispatchReceiver }?.let { thisParam ->
                        arguments[thisParam] = irGet(thisRef)
                    }
                }
                targetFunction.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }
                    .forEachIndexed { index, parameter ->
                        if (parameter.kind == IrParameterKind.ExtensionReceiver) {
                            val extension =
                                hookFunction.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
                            if (extension == null) {
                                println("ExHook: Didn't find a match for the available extension parameters, didn't write an extension receiver?")
                                return@forEachIndexed
                            }
                            arguments[parameter] = irGet(extension)
                        } else {
                            // case 2：callOrigin(xxx) 有参调用
                            val providedArg = argument.getOrNull(index)

                            // 如果参数是 var 覆盖 shadow variable，就用 overrideParams

                            if (targetFunction is IrConstructor) {
                                val field = (targetFunction.constructedClass.properties + targetFunction.constructedClass.getAllSuperclasses()
                                    .flatMap { it.properties }).find { it.name == parameter.name }?.backingField
                                if (field != null) {
                                    arguments[parameter] = irBlock {
                                        // 1. 先执行 setField
                                        +irSetField(
                                            irGet(hookFunction.parameters.first { it.name == EzHookIrTransformer.THIS_REF_PARAM_NAME }),
                                            field,
                                            providedArg ?: irGet(hookFunction.parameters[index])
                                        )

                                        // 2. 最后一个表达式作为整个 block 的值
                                        +(providedArg ?: irGet(hookFunction.parameters[index]))
                                    }
                                } else arguments[parameter] = providedArg ?: irGet(hookFunction.parameters[index])
                            } else arguments[parameter] = providedArg ?: irGet(hookFunction.parameters[index])
                        }
                    }
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression = context.createIrBuilder(hookFunction.symbol).run {
        when (expression.symbol.owner.fqNameWhenAvailable?.asString()) {
            CALL_ORIGIN -> buildCallOrigin(this,targetFunction, hookFunction, expression.arguments)

            GET_THIS_REF -> {
                val getThis = hookFunction.parameters.find { it.name == EzHookIrTransformer.THIS_REF_PARAM_NAME }
                    ?.let { thisRef ->
                        irGet(thisRef)
                    }
                if (getThis == null) {
                    println("EzHook: getThisRef called in non-class member function")
                    super.visitCall(expression)
                } else getThis
            }

            GET_FIELD, SET_FIELD -> {
                if (!isGetterSetter) error("EzHook: getField/setField cannot be used in function ${hookFunction.fqNameWhenAvailable}") else super.visitCall(
                    expression
                )
            }

            else -> visitThisRefProperty(
                expression,
                hookFunction.parameters.find { it.name == EzHookIrTransformer.THIS_REF_PARAM_NAME },
                this@EzHookCallOriginTransformer.context
            ) ?: super.visitCall(expression)
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