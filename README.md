
---

# EzHook
EzHook is an AOP (Aspect‑Oriented Programming) framework for Kotlin Multiplatform, supporting **Kotlin/Native** and **Kotlin/JS**.  
It replaces functions, constructors, and properties at **compile time**, with **zero runtime reflection** and **no performance cost**.

[中文](./README.zh.md)

---

## Project Configuration

EzHook consists of two components:

1. **Gradle Plugin**  
   Performs IR transformations and integrates hooks into target modules.
2. **Runtime Library**  
   Provides annotations, callOrigin(), getThisRef(), getField(), and other helper APIs.

---

## 1. Apply the Gradle Plugin(Need to publish to local repository by oneself)

```kotlin
buildscript {
    dependencies {
        classpath("io.github.xdmrwu:ez-hook-gradle-plugin:0.0.3")
    }
}

plugins {
    id("io.github.xdmrwu.ez-hook-gradle-plugin")
}
```

---

## 2. Add the Runtime Library(No need after using the plugin)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xdmrwu:ez-hook-library:0.0.3")
        }
    }
}
```

---

## 3. Disable Kotlin/Native caching

```properties
kotlin.native.cacheKind=none
```

---

# Usage Overview

EzHook works similarly to Lancet, but for Kotlin Multiplatform IR.

A hook is simply:

- A **top‑level function or property**
- Annotated with `@EzHook`, `@EzHook.Before`, `@EzHook.After`, or `@EzHook.NULL`
- The annotation specifies the fully qualified name of the target

---

# Hooking Functions

A hook function must:

- Be a **top‑level function**
- Have the **same parameter list** as the target
- Have the **same return type**
- Optionally override parameters (same name, same type)
- Optionally call the original via `callOrigin()`

---

## Basic Function Hook

```kotlin
@HiddenFromObjC
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    println("Hook to int")
    return 10086
}
```

---

## Overriding Parameters and Calling Origin

```kotlin
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS
    return callOrigin<Int>()   // now uses HOURS
}
```

`callOrigin()` always uses the **current overridden parameters**.

---

## Calling Origin with Custom Arguments

```kotlin
return callOrigin<Int>(null, 123, "xyz")
```

- Unspecified parameters → use original call args
- Specified parameters → forced override
- Passing `null` must be explicit

---

# Hooking Constructors

EzHook supports primary and secondary constructors.

```kotlin
@EzHook("com.example.MyClass.<init>")
fun hookConstructor(name: String) {
    val name = "Modified"
    callOrigin<Unit>()
}
```

### Important Notes

- Constructors have `this` → use `getThisRef<T>()`
- `isInitializeProperty` controls **whether Kotlin property initializers run before the hook**
- Only the **primary constructor** responds to `isInitializeProperty`
- `init {}` blocks run *only if* you call the original constructor

---

# Accessing `this`

```kotlin
val self = getThisRef<MyClass>()
```

Works for:

- Member functions
- Constructors
- Property getter/setter hooks

Not available for top‑level functions.

---

# Hooking Properties

EzHook can hook:

- getter
- setter
- full var property
- top‑level properties
- delegated properties

---

## Property Hook Example

```kotlin
@EzHook("com.example.MyClass.prop")
var newProp = "777777"
    get() = callOrigin<String>() + "3333"
    set(value) { setField(value + "22222") }
```

### Behaviour Notes

- `getField()` / `setField()` access the backing field
- For delegated properties, they return **the delegate object itself**
- Under property hooking rules, **initializers are removed** if they contain runtime calls (getThisRef, etc.)

---

# Hooking Getter Only

```kotlin
@EzHook.Before("com.example.MyClass.prop.get")
fun beforeGet() {
    println("before getter")
}
```

# Hooking Setter Only

```kotlin
@EzHook.After("com.example.MyClass.prop.set")
fun afterSet(value: String) {
    println("setter finished with $value")
}
```

---

# Before / After Hooks

### Before Hook
Runs before the target method/constructor/property.

```kotlin
@EzHook.Before("com.example.MyClass.test")
fun beforeTest(name: String) {
    println("before test")
}
```

### After Hook
Runs after the target.  
If it returns a non‑Unit value → overrides the target’s return value.

```kotlin
@EzHook.After("com.example.MyClass.test")
fun afterTest(name: String): String {
    return "hooked result"
}
```

---

# NULL Hooks

NULL hooks replace the target and force it to return null.

```kotlin
@EzHook.NULL("com.example.MyClass.loadData")
fun forceNull() = null
```

### Constructor NULL Hook Rules

- Constructor body **does not run**
- `init {}` blocks never run
- Controlled by `isInitializeProperty`:
   - true → Kotlin property initializers run
   - false → they remain **uninitialized null**, even if non‑nullable  
     (K/N may crash if accessed)

---

# Accessing Backing Field

### getField()

```kotlin
val old = getField<String>()
```

### setField()

```kotlin
setField(value + " modified")
```

Delegated property case:

- `getField()` returns the **delegate instance**, not its internal value.

---

# Accessing `this` Properties

### getThisProperty

```kotlin
val username = getThisProperty<String>("username")
```

### setThisProperty

```kotlin
setThisProperty("count", 5)
```

If isBackingField = true → operate on the backing field instead of the getter/setter.

---

# Top‑Level Hooking

Full support.

```kotlin
@EzHook("com.example.topLevelFunction")
fun topLevelFunction(name: String): String {
    val name = "override"
    return "origin: ${callOrigin<String>()}, new: $name"
}
```

Top‑level property:

```kotlin
@EzHook("com.example.topLevelProp")
var topLevelProp = "666"
    get() = getField<String>() + "444"
    set(value) { setField(value + "555") }
```

---

# Extension Function Hooking

```kotlin
@EzHook("com.example.getStr")
fun Int.getStr(): String {
    return callOrigin<String>() + "-new2"
}
```

---

# Inline Hooks (Recommended for Kotlin/JS)

Inline mode eliminates cross‑module linking:

```kotlin
@EzHook("kotlin.time.Duration.toInt", inline = true)
fun toInt(unit: DurationUnit): Int {
    return callOrigin<Int>()
}
```

Use inline = true when:

- Target is JS
- Circular deps might occur
- You need maximal compatibility

---

# Limitations

- Supported platforms: **Kotlin/Native**, **Kotlin/JS**
- JVM is not supported
- Requires Kotlin 2.3.0
- Hooks must be **top‑level**
- For iOS: use `@HiddenFromObjC`
- Property initializers that reference EzHook runtime APIs are removed
- Delegated property initializer logic cannot be executed inside hook
