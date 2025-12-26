plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
}

buildscript {
    dependencies {
        classpath("io.github.xdmrwu:ez-hook-gradle-plugin:0.0.3")
    }
    repositories {
        mavenCentral()
        maven {
            url = uri("local-plugin-repository")
        }
        google()
        gradlePluginPortal()
    }
}