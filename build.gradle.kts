plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
}

buildscript {
    dependencies {
        classpath("io.github.dreammooncai:ez-hook-gradle-plugin:0.0.4")
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