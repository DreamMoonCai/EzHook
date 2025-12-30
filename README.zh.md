
---

# EzHook
EzHook 是一个适用于 Kotlin Mult平台（Kotlin Multiplatform）的 **AOP（面向切面编程）框架**，支持 **Kotlin/Native** 和 **Kotlin/JS**。  
EzHook 能在 **编译期** 替换任意函数、构造方法、属性的行为，无需运行时反射，性能零损耗。

---

## 工程配置

EzHook 由两部分组成：

1. **Gradle 插件**  
   收集 Hook 元数据并执行 IR 层的代码注入。
2. **Runtime 运行时库**  
   提供注解、callOrigin()、getThisRef()、getField() 等工具方法。

---

## 1. 添加 Gradle 插件(需要自己发布到本地仓库)

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

## 2. 添加运行时库(使用插件后不需要)

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

## 3. 禁用 Kotlin/Native 缓存

EzHook 对 IR 模块进行变换，Native 模式必须关闭缓存。

```properties
kotlin.native.cacheKind=none
```

---

# 使用概览

EzHook 的使用方式与 Lancet 类似，但面向 Kotlin Multiplatform IR。

你需要：

- 编写一个 **顶层函数（top‑level）或属性**
- 使用 `@EzHook` / `@EzHook.Before` / `@EzHook.After` / `@EzHook.NULL`
- 在注解中提供目标方法、构造、属性的 **完整 FQN 名称**

---

# Hook 普通函数

Hook 方法要求：

- 必须是 **顶层函数**
- 参数列表必须与目标一致
- 可以按同名同类型变量覆盖原参数
- 可使用 `callOrigin()` 调用原方法

---

## 示例：完全替换方法

```kotlin
@HiddenFromObjC
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    println("Hook to int")
    return 10086
}
```

---

## 覆盖参数 + 调用原方法

```kotlin
@EzHook("kotlin.time.Duration.toInt")
fun toInt(unit: DurationUnit): Int {
    val unit = DurationUnit.HOURS   // 覆盖参数
    return callOrigin<Int>()        // callOrigin() 会使用覆盖后的参数
}
```

---

## 使用 callOrigin 传入自定义参数

```kotlin
return callOrigin<Int>(null, 123, "xyz")
```

行为规则：

- 未显式传参 → 使用原始参数
- 显式传参 → 覆盖原参数
- 若需传 null → 必须明确写 null

---

# Hook 构造方法（Constructor Hook）

EzHook 支持主构造与次构造。

```kotlin
@EzHook("com.example.MyClass.<init>")
fun hookConstructor(name: String) {
    val name = "Modified"
    callOrigin<Unit>()  // 调用原构造方法
}
```

### 构造方法 Hook 重要规则

- 构造方法有 `this` → 可用 `getThisRef<T>()`
- **isInitializeProperty 仅作用于主构造方法**
- isInitializeProperty 控制 **属性初始化器是否在 Hook 之前运行**
- `init {}` 会在你调用 callOrigin 时运行  
  若不调用 callOrigin → init 与构造体内部逻辑都不会执行

---

# 获取 this 引用

```kotlin
val self = getThisRef<MyClass>()
```

可用于：

- 成员函数 Hook
- 构造方法 Hook
- 属性 getter / setter Hook

不可用于：

- 顶层函数 Hook

---

# Hook 属性（Property Hook）

EzHook 可 Hook：

- getter
- setter
- 整体 var 属性
- 顶层属性
- 委托属性（delegate）

---

## 属性 Hook 示例

```kotlin
@EzHook("com.example.MyClass.prop")
var newProp = "777777"
    get() = callOrigin<String>() + "3333"
    set(value) { setField(value + "22222") }
```

### 属性相关行为说明

- `getField()` / `setField()` 操作 backing field
- 若目标是委托属性 → `getField()` 返回 **委托对象本身**
- 若该属性初始化器中调用了 EzHook runtime 方法（如 getThisRef）  
  → **该初始化器将在编译期被移除**（避免构造期触发 NotImplementedError）

---

# Hook Getter

```kotlin
@EzHook.Before("com.example.MyClass.prop.get")
fun beforeGet() {
    println("before getter")
}
```

# Hook Setter

```kotlin
@EzHook.After("com.example.MyClass.prop.set")
fun afterSet(value: String) {
    println("setter finished with $value")
}
```

---

# Before / After Hook

### Before：在目标前执行

```kotlin
@EzHook.Before("com.example.MyClass.test")
fun beforeTest(name: String) {
    println("before test")
}
```

### After：在目标后执行
若返回类型非 Unit → 覆盖目标的返回值

```kotlin
@EzHook.After("com.example.MyClass.test")
fun afterTest(name: String): String {
    return "hooked result"
}
```

---

# NULL Hook（强制返回 null）

NULL Hook 将目标替换成直接返回 null。

```kotlin
@EzHook.NULL("com.example.MyClass.loadData")
fun forceNull() = null
```

### 构造方法的 NULL Hook 特殊规则

- 原构造体内部逻辑 **不执行**
- 所有 init {...} 块 **永远不会执行**
- isInitializeProperty 控制是否要初始化属性：
   - true → Kotlin 属性初始化器依然运行
   - false → 所有属性保持未初始化状态（即使类型为非空）  
     Kotlin/Native 访问未初始化属性可能**直接崩溃（非 NPE）**

---

# 访问 Backing Field

### getField()

```kotlin
val oldValue = getField<String>()
```

### setField()

```kotlin
setField(value + " modified")
```

委托属性注意：

- getField 返回的是 **委托对象**，不是内部 value

---

# 访问 this 的属性

### getThisProperty()

```kotlin
val username = getThisProperty<String>("username")
```

### setThisProperty()

```kotlin
setThisProperty("count", 5)
```

若 `isBackingField = true` → 直接操作 backing field  
否则按 getter/setter 逻辑执行。

---

# Hook 顶层函数

```kotlin
@EzHook("com.example.topLevelFunction")
fun topLevelFunction(name: String): String {
    val name = "override"
    return "origin: ${callOrigin<String>()}, new: $name"
}
```

---

# Hook 顶层属性

```kotlin
@EzHook("com.example.topLevelProp")
var topLevelProp = "666"
    get() = getField<String>() + "444"
    set(value) { setField(value + "555") }
```

---

# Hook 扩展函数

```kotlin
@EzHook("com.example.getStr")
fun Int.getStr(): String {
    return callOrigin<String>() + "-new2"
}
```

---

# Inline Hook（推荐 JS 使用）

JS 对模块引用较敏感，Inline Hook 将 Hook 逻辑直接复制到目标模块内，避免循环依赖。

```kotlin
@EzHook("kotlin.time.Duration.toInt", inline = true)
fun toInt(unit: DurationUnit): Int {
    return callOrigin<Int>()
}
```

适合：

- Kotlin/JS
- 避免跨模块依赖
- 提升兼容性

---

# 限制

- 支持平台：**Kotlin/Native**、**Kotlin/JS**
- 尚不支持 JVM
- 需要 Kotlin 2.3.0
- Hook 方法必须为 **顶层函数/属性**
- iOS 环境 Hook 须添加 `@HiddenFromObjC`
- 当属性 initializer 使用 EzHook runtime API 时 → initializer 会被移除
- 对委托属性：无法在 Hook 内执行初始化逻辑，只能通过 getField/setField 操作委托对象本身
