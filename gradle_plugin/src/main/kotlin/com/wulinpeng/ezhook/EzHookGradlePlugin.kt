package com.wulinpeng.ezhook

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.internal.configuration.problems.taskPathFrom
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.util.Logger
import kotlin.Nothing

class EzHookGradlePlugin: KotlinCompilerPluginSupportPlugin {

    private lateinit var project: Project

    override fun apply(target: Project) {
        super.apply(target)
        project = target
        target.extensions.configure(KotlinMultiplatformExtension::class.java) {
            it.sourceSets.getByName("commonMain").dependencies {
                implementation("io.github.dreammooncai:ez-hook-library:0.0.3")
            }
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            emptyList()
        }
    }

    override fun getCompilerPluginId() = Constants.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = Constants.KOTLIN_PLUGIN_GROUP,
        artifactId = Constants.KOTLIN_PLUGIN_NAME,
        version = Constants.KOTLIN_PLUGIN_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target is KotlinNativeTarget
                || kotlinCompilation.target is KotlinJsIrTarget
    }
}