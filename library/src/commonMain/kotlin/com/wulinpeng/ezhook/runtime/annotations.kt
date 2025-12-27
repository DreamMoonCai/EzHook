package com.wulinpeng.ezhook.runtime

/**
 * EzHook
 *
 * Declares that the annotated function or property is a hook for the specified target.
 *
 * Property‑related behavior:
 * - If the property initializer (backing field) uses any runtime‑related functions
 *   such as `getThisRef`, the initializer will be removed at compile time to avoid
 *   triggering [NotImplementedError] during class or file initialization.
 *
 * - If the target property is a delegated property, initializer logic (backing field)
 *   is not supported within the current property.
 *   To access the delegate of the target property, use [getField].
 *
 * Constructor behavior:
 * - `isInitializeProperty` applies **only to the primary constructor**.
 * - When applied to a primary‑constructor hook, this flag determines whether the class’s
 *   properties that declare initial values (for example: `val name = "abc"`)
 *   should be initialized before the hook runs.
 *
 * About property initialization:
 * - If `isInitializeProperty` is false, such properties will *not* be initialized and will
 *   remain `null` regardless of their Kotlin type declarations.
 * - Accessing these uninitialized properties in native causes an immediate crash instead
 *   of a Kotlin NullPointerException, unless the property type is nullable and Kotlin
 *   inserts null‑safety checks on usage.
 *
 * @property targetFunctionOrProperty The target function or property to hook.
 * @property inline Whether this hook function/property should be copied into the
 *                  class or file where the target member is located.
 * @property isInitializeProperty Whether class properties with initializers should be
 *                                automatically initialized when this hook is applied
 *                                to the primary constructor.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class EzHook(
    val targetFunctionOrProperty: String,
    val inline: Boolean = false,
    val isInitializeProperty: Boolean = true
) {

    /**
     * EzHook Before
     *
     * Declares that the annotated function or property is a hook that executes
     * before the target function or property.
     *
     * Constructor behavior:
     * - `isInitializeProperty` applies **only to primary constructors**.
     * - When used on the primary constructor, this flag determines whether class
     *   properties are automatically initialized before executing this hook.
     *
     * @see EzHook
     * @property targetFunctionOrProperty The target function or property to hook.
     * @property inline Whether this hook member should be copied into the target's
     *                  class or file.
     * @property isInitializeProperty Whether to automatically initialize class
     *                                properties when used on the primary constructor.
     */
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.BINARY)
    annotation class Before(
        val targetFunctionOrProperty: String,
        val inline: Boolean = false,
        val isInitializeProperty: Boolean = true
    )

    /**
     * EzHook After
     *
     * Declares that the annotated function or property is a hook that executes
     * after the target function or property.
     *
     * If this hook returns a value other than Unit, that value becomes the
     * final return result of the target function or property.
     *
     * Constructor behavior:
     * - `isInitializeProperty` applies **only to primary constructors**.
     * - When used on the primary constructor, this flag determines whether class
     *   properties are automatically initialized before executing this hook.
     *
     * @see EzHook
     * @property targetFunctionOrProperty The target function or property to hook.
     * @property inline Whether this hook member should be copied into the target's
     *                  class or file.
     * @property isInitializeProperty Whether to automatically initialize class
     *                                properties when used on the primary constructor.
     */
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.BINARY)
    annotation class After(
        val targetFunctionOrProperty: String,
        val inline: Boolean = false,
        val isInitializeProperty: Boolean = true
    )

    /**
     * EzHook NULL
     *
     * Declares that the annotated function or property is a hook that intercepts
     * the target and forces it to return null.
     *
     * Constructor behavior:
     * - `isInitializeProperty` applies **only to primary constructors**.
     * - The constructor body will not execute, and `init` blocks will never run.
     * - Controlled by `isInitializeProperty`:
     *   - If true, properties that declare initial values (e.g., `val name = "abc"`)
     *     will be initialized before returning null.
     *   - If false, these properties remain uninitialized and thus become null even
     *     if their Kotlin types are non‑nullable.
     *
     * Notes on null behavior:
     * - Accessing such uninitialized properties in native may cause a direct
     *   native crash rather than a Kotlin NullPointerException, unless the
     *   property type is nullable and Kotlin performs null checks.
     *
     * @see EzHook
     * @property targetFunctionOrProperty The target function or property to hook.
     * @property inline Whether this hook member should be copied into the target's
     *                  class or file.
     * @property isInitializeProperty Whether to initialize class properties with declared
     *                                initial values when applied to the primary constructor.
     */
    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER
    )
    @Retention(AnnotationRetention.BINARY)
    annotation class NULL(
        val targetFunctionOrProperty: String,
        val inline: Boolean = false,
        val isInitializeProperty: Boolean = true
    )
}