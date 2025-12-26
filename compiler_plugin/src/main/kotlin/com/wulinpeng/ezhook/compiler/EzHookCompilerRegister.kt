package com.wulinpeng.ezhook.compiler

import com.google.auto.service.AutoService
import com.wulinpeng.ezhook.compiler.hook.IrLoweringHookExtension
import com.wulinpeng.ezhook.compiler.visitor.EzHookCollectorVisitor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File
import java.net.JarURLConnection

@AutoService(CompilerPluginRegistrar::class)
@OptIn(ExperimentalCompilerApi::class)
class EzHookCompilerRegister: CompilerPluginRegistrar() {
    override val pluginId: String
        get() = "ez-hook-gradle-plugin"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        forceLoadPackage("com.wulinpeng.ezhook")
        IrLoweringHookExtension.registerExtension(EzHookExtension())
    }


    fun forceLoadPackage(pkg: String) {
        val path = pkg.replace('.', '/')
        val cl = this::class.java.classLoader
        val url = cl.getResource(path) ?: return

        when (url.protocol) {
            "file" -> {
                val dir = File(url.toURI())
                dir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".class")) {
                        val clsName = "$pkg.${file.name.removeSuffix(".class")}"
                        cl.tryLoadClass(clsName)
                    }
                }
            }
            "jar" -> {
                val jarPath = (url.openConnection() as JarURLConnection).jarFile
                for (entry in jarPath.entries()) {
                    if (entry.name.startsWith(path) && entry.name.endsWith(".class")) {
                        val clsName = entry.name
                            .removeSuffix(".class")
                            .replace('/', '.')
                        cl.tryLoadClass(clsName)
                    }
                }
            }
        }
    }

    private fun ClassLoader.tryLoadClass(fqName: String) =
        try {
            Class.forName(fqName, true, this)
        } catch (e: ClassNotFoundException) {
            null
        }

}