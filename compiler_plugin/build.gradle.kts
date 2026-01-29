
plugins {
    id("java-gradle-plugin")
    `maven-publish`
    kotlin("jvm")
    kotlin("kapt")
    id("com.vanniktech.maven.publish") version "0.35.0"
}

repositories {
    mavenCentral()
    maven {
        url = uri("../local-plugin-repository")
    }
    google()
    gradlePluginPortal()
}

dependencies {
    // gradle plugin
    implementation(kotlin("gradle-plugin-api"))

    // compiler plugin
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    kapt("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.dreammooncai", "ez-hook-compiler-plugin", "0.0.4")

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