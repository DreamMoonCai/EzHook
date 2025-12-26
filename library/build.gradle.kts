import org.gradle.kotlin.dsl.jvm
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.vanniktech.maven.publish.SonatypeHost

repositories {
    mavenCentral()
    maven {
        url = uri("../local-plugin-repository")
    }
    google()
    gradlePluginPortal()
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.30.0"
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("io.github.xdmrwu", "ez-hook-library", "0.0.3")

    pom {
        description.set("An AOP framework for KotlinMultiplatform, supporting Kotlin/Native and Kotlin/JS")
        url.set("https://github.com/XDMrWu/EzHook/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("XDMrWu")
                name.set("wulinpeng")
                url.set("https://github.com/XDMrWu/")
            }
        }
        scm {
            url.set("https://github.com/XDMrWu/EzHook")
            connection.set("scm:git:git://github.com/XDMrWu/EzHook.git")
            developerConnection.set("scm:git:ssh://git@github.com/XDMrWu/EzHook.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("../local-plugin-repository")
        }
    }
}

kotlin {

    androidTarget()

    androidNativeArm64()
    androidNativeArm32()
    androidNativeX86()
    androidNativeX64()

    // iOS Targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // macOS Target
    macosX64()
    macosArm64()

    // Native Targets
    tvosArm64()
    tvosX64()
    watchosArm64()
    watchosX64()

    // Linux Target
    linuxX64()

    // Windows Target
    mingwX64()

    jvm("desktop")
    // JS Target
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
        }
    }
}

android {
    namespace = "com.wulinpeng.ezhook"  // 替换为你的实际包名
    compileSdkVersion(34)          // 替换为你的编译 SDK 版本
}