
---

# EzHook
EzHook is an AOP (Aspect-Oriented Programming) framework for Kotlin Multiplatform, supporting **Kotlin/Native** and **Kotlin/JS**.  
It allows you to replace any function, constructor, or property behavior at compile time, with zero runtime reflection and no performance loss.

[中文](./README.zh.md)

---

## Project Configuration

---

EzHook consists of two components:

1. **Gradle Plugin**  
   Collects hook metadata and performs IR-level transformation.
2. **Runtime Library**  
   Provides annotations, callOrigin(), getThisRef(), and other utilities.

### 1. Apply the Gradle Plugin
Add it to your root `build.gradle.kts`:

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

### 2. Add the Runtime Library

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xdmrwu:ez-hook-library:0.0.3")
        }
    }
}
```

### 3. Disable Kotlin/Native caching
EzHook transforms IR modules, so K/N caching must be disabled:

```properties
kotlin.native.cacheKind=none
```

---

## Usage Overview

EzHook works similarly to [Lancet](https://github.com/eleme/lancet), but for Kotlin Multiplatform.

You create a hook function and annotate it with `@EzHook`, specifying the fully qualified name (FQN) of the target function, constructor, or property.

---

## Hooking Functions

A hook function must:

- Have the same parameter list as the target
- Have the same return type
- Be a **top‑level function**
- Optionally call the original function via `callOrigin<T>()`
- Optionally override parameters by creating a variable with the same name and type

### Example

```kotlin
@HiddenFromObjC
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    println("Hook to int")
    return 10086
}
```

This completely overrides `Duration.toInt()`.

---

## Calling the Original Method

EzHook supports calling the original function while modifying its parameters.

### Parameter Override + callOrigin()

```kotlin
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS   // override parameter
    return callOrigin<Int>()
}
```

`callOrigin()` will use the overridden parameter.

---

## Hooking Constructors

EzHook now supports full constructor hooking.  
To hook a constructor:

```kotlin
@EzHook("com.example.MyClass.<init>")
fun hookConstructor(name: String) {
    val name = "Modified"
    callOrigin<Unit>()  // invokes the original constructor with new args
}
```

### Notes

- Constructors have implicit `this`—use `getThisRef<T>()` to access it.
- Property initializers and `init {}` blocks will still run.
- Parameter override works the same as normal function hooks.

---

## getThisRef(): Accessing the Current Instance

You can access `this` inside hook functions (including constructors):

```kotlin
getThisRef<NormalTest>()
```

Example:

```kotlin
@EzHook("com.example.MyClass.test")
fun newTest(name: String): String {
    val self = getThisRef<MyClass>()
    return "value = ${self.someProp}"
}
```

---

## Hooking Properties

EzHook supports replacing:

- getter
- setter
- backing field initializer

### Example

```kotlin
@EzHook("com.example.MyClass.prop")
var newProp = "777777"
    get() = callOrigin<String>() + "3333"
    set(value) { field = value + "22222" }
```

### Supported behaviors

- Hook getter only
- Hook setter only
- Hook both
- Hook top‑level properties
- Inline hook properties for JS

---

## Inline Hooks (Recommended for Kotlin/JS)

Kotlin/JS is sensitive to circular dependencies.  
Inlining the hook avoids inter-module calls and is safer.

```kotlin
@EzHook("kotlin.time.Duration.toInt", true)
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS
    return callOrigin<Int>()
}
```

When inline = true:

- The hook function is copied into the target module
- No cross-module linking happens
- Greatly improved stability on JS

---

## Top‑Level Function and Property Hooking

Also fully supported:

```kotlin
@EzHook("com.example.topLevelFunctionTest")
fun topLevelFunctionTest(name: String): String {
    val name = "override"
    return "before: ${callOrigin<String>()}, after: $name"
}
```

Top-level property:

```kotlin
@EzHook("com.example.topLevelProp")
var topLevelProp = "666"
    get() = field + "444"
    set(value) { field = value + "555" }
```

---

## Extension Function Hooking

Example:

```kotlin
@EzHook("com.example.getStr")
fun Int.getStr(): String {
    return "${callOrigin<String>()}-new2"
}
```

---

## Limitations

Current technical limitations:

- Supported targets: **Kotlin/Native** and **Kotlin/JS**
- Kotlin/JVM is not supported
- Kotlin 2.3.0 only (IR APIs change frequently)
- Hook methods must be **top-level**
- Must include `@HiddenFromObjC` when hooking in K/N + iOS
- Inline mode recommended for Kotlin/JS

---