import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    id("com.vanniktech.maven.publish") version "0.35.0"
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.dreammooncai", "ez-hook-library", "0.0.4")

    pom {
        name.set("EzHook")
        description.set("An AOP framework for KotlinMultiplatform, supporting Kotlin/Native and Kotlin/JS")
        url.set("https://github.com/DreamMoonCai/EzHook/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("DreamMoonCai")
                name.set("dreammoon")
                url.set("https://github.com/DreamMoonCai/")
            }
        }
        scm {
            url.set("https://github.com/DreamMoonCai/EzHook")
            connection.set("scm:git:git://github.com/DreamMoonCai/EzHook.git")
            developerConnection.set("scm:git:ssh://git@github.com/DreamMoonCai/EzHook.git")
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

    androidLibrary {
        namespace = "com.wulinpeng.ezhook"
        compileSdk = 36
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.valueOf("JVM_${JavaVersion.current().majorVersion}"))
    }

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