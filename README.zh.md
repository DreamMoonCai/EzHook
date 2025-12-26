
---

# EzHook
EzHook 是一个面向 Kotlin Multiplatform 的 AOP（面向切面编程）框架，支持 **Kotlin/Native** 和 **Kotlin/JS**。  
它允许你在 **编译期** 替换任意函数、constructor 或 property 的行为，无需运行时反射，并且没有性能损耗。

---

## 项目配置

EzHook 包含两个核心部分：

1. **Gradle Plugin**  
   负责收集 hook 元数据与执行 IR transform。
2. **Runtime Library**  
   提供注解、callOrigin()、getThisRef() 等运行时辅助方法。

### 1. 在根项目中应用 Gradle Plugin

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

### 2. 在使用 EzHook 的模块中加入 runtime 依赖

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xdmrwu:ez-hook-library:0.0.3")
        }
    }
}
```

### 3. 禁用 Kotlin/Native 缓存

EzHook 会修改 IR，因此 K/N 缓存必须关闭：

```properties
kotlin.native.cacheKind=none
```

---

## 使用方式概览

EzHook 的使用方式类似 [Lancet](https://github.com/eleme/lancet)，但它适用于 Kotlin Multiplatform。

你只需要：

- 创建一个 hook 函数
- 使用 `@EzHook` 注解
- 指定目标方法 / 构造函数 / 属性的 FQN（Fully Qualified Name）

EzHook 会在编译期替换对应逻辑。

---

## Hook 普通函数

Hook 函数必须满足：

- 参数列表与目标函数一致
- 返回类型一致
- 必须是 top‑level 函数
- 可以使用 `callOrigin<T>()` 调用原函数
- 可以创建同名局部变量覆盖参数

### 示例

```kotlin
@HiddenFromObjC
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    println("Hook to int")
    return 10086
}
```

此 hook 会完全替换 `Duration.toInt()`。

---

## 调用原函数：callOrigin()

EzHook 支持调用原方法，并可在 callOrigin 前修改参数。

### 参数覆盖 + callOrigin()

```kotlin
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS   // 覆盖原参数
    return callOrigin<Int>()
}
```

callOrigin() 会使用你覆盖后的参数。

---

## Hook constructor

EzHook 支持对 constructor 进行完整 hook。

### 示例

```kotlin
@EzHook("com.example.MyClass.<init>")
fun hookConstructor(name: String) {
    val name = "Modified"
    callOrigin<Unit>()
}
```

### 特性说明

- constructor 会保留原有的 property 初始化逻辑
- init {} 块会照常执行
- 参数覆盖与 callOrigin() 完整可用
- 可使用 getThisRef<T>() 获取 constructor 内部的 this

---

## 使用 getThisRef<T>() 获取 this

在 hook 方法（包括 constructor）内部，可以通过 getThisRef<T>() 获取当前实例：

```kotlin
getThisRef<MyClass>()
```

示例：

```kotlin
@EzHook("com.example.MyClass.test")
fun newTest(name: String): String {
    val self = getThisRef<MyClass>()
    return "value = ${self.someProp}"
}
```

---

## Hook property（包括 getter / setter / backing field）

EzHook 支持：

- hook getter
- hook setter
- hook backing field initializer
- hook top‑level property

### 示例

```kotlin
@EzHook("com.example.MyClass.prop")
var newProp = "777777"
    get() = callOrigin<String>() + "3333"
    set(value) { field = value + "22222" }
```

EzHook 会将 target property 的 getter/setter/backingField 重定向到你的 hook property。

---

## Inline Hook（特别适用于 Kotlin/JS）

Kotlin/JS 很容易出现模块循环依赖问题，使用 inline hook 可以避免此问题。

当 inline = true 时：

- hook 函数会复制到目标模块
- 不会产生跨模块依赖
- 大幅提升 JS 平台的稳定性

示例：

```kotlin
@EzHook("kotlin.time.Duration.toInt", true)
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS
    return callOrigin<Int>()
}
```

---

## Hook 顶层函数与顶层属性

EzHook 同样支持 top‑level 函数和 property：

### 顶层函数：

```kotlin
@EzHook("com.example.topLevelFunctionTest")
fun topLevelFunctionTest(name: String): String {
    val name = "override"
    return "before: ${callOrigin<String>()}, after: $name"
}
```

### 顶层属性：

```kotlin
@EzHook("com.example.topLevelProp")
var topLevelProp = "666"
    get() = field + "444"
    set(value) { field = value + "555" }
```

---

## Hook 扩展函数（Extension Function）

EzHook 支持扩展函数 hook：

```kotlin
@EzHook("com.example.getStr")
fun Int.getStr(): String {
    return "${callOrigin<String>()}-new2"
}
```

---

## 限制

当前限制包括：

- 仅支持 **Kotlin/Native** 和 **Kotlin/JS**
- 不支持 Kotlin/JVM
- 要求 Kotlin 版本 **2.3.0**
- hook 方法必须为 top‑level
- iOS 平台需要添加 `@HiddenFromObjC`
- Kotlin/JS 强烈建议使用 inline 模式
- 目前不支持 hook extension constructor（Kotlin 不允许）
