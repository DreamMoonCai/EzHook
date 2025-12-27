package com.wulinpeng.ezhook.runtime


/**
 * Retrieve the backing field of the target property.
 *
 * This works the same as accessing `field` inside the property accessor.
 * It can be used in situations where `field` cannot be referenced directly
 * (for example, when there is no initializer block).
 *
 * For delegated properties, this method returns the delegate object instead
 * (e.g., for `by lazy`, it returns the corresponding `Lazy` instance).
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun <T> getField(): T {
    throw NotImplementedError("Get field not implemented")
}

/**
 * Set the backing field of the target property.
 *
 * This works the same as assigning to `field` inside the property accessor.
 * It can be used in situations where `field` cannot be referenced directly
 * (for example, when there is no initializer block).
 *
 * Note: For delegated properties, this will set the delegate object itself,
 * not the delegate’s internal value.
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun setField(value: Any?) {
    throw NotImplementedError("Set field not implemented")
}


/**
 * Get the `this` object of the current property or function.
 *
 * Only available when the target property or function actually has a `this`
 * reference. For example, top‑level functions are not supported.
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun <T> getThisRef(): T {
    throw NotImplementedError("Get this ref not implemented")
}

/**
 * Get the value of property [name] on the `this` object of the current
 * property/function.
 *
 * Only available when the target property or function has a `this` reference.
 * Top‑level functions are not supported.
 *
 * This behaves the same as accessing `this.name`, but can be used when visibility
 * restrictions prevent the property from being accessed explicitly.
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun <T> getThisProperty(name: String,isBackingField: Boolean = false): T {
    throw NotImplementedError("Get this property not implemented")
}

/**
 * Set the value of property [name] on the `this` object of the current
 * property/function.
 *
 * Only available when the target property or function has a `this` reference.
 * Top‑level functions are not supported.
 *
 * This behaves the same as writing `this.name = value`, but can be used when
 * visibility restrictions prevent the property from being modified explicitly.
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun setThisProperty(name: String, value: Any?,isBackingField: Boolean = false) {
    throw NotImplementedError("Set this property not implemented")
}

/**
 * Call the target function using the original arguments of the current call.
 *
 * This is equivalent to invoking the target function without modifying any
 * parameters.
 *
 * @author wulinpeng
 * @since 2024/11/21 22:55
 */
fun <T> callOrigin(): T {
    throw NotImplementedError("Call origin not implemented")
}

/**
 * Call the target function with optional custom arguments.
 *
 * Arguments that are not explicitly provided will use the original arguments of
 * the current call.
 * If you need to pass `null`, you must explicitly pass it.
 * Default parameter values of this method do NOT affect the target function.
 *
 * @author dreammooncai
 * @since 2025/12/27 19:09
 */
fun <T> callOrigin(
    p1: Any?, p2: Any? = null, p3: Any? = null, p4: Any? = null, p5: Any? = null, p6: Any? = null, p7: Any? = null, p8: Any? = null, p9: Any? = null, p10: Any? = null,
    p11: Any? = null, p12: Any? = null, p13: Any? = null, p14: Any? = null, p15: Any? = null, p16: Any? = null, p17: Any? = null, p18: Any? = null, p19: Any? = null,
    p20: Any? = null, p21: Any? = null, p22: Any? = null
): T {
    throw NotImplementedError("Call origin not implemented")
}