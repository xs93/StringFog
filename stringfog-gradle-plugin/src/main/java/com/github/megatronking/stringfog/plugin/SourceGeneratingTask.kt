package com.github.megatronking.stringfog.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class SourceGeneratingTask @Inject constructor() : DefaultTask() {
    companion object {
        const val FOG_CLASS_NAME = "StringFog"
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val implementation: Property<String>

    @get:Input
    abstract val mode: Property<StringFogMode>

    @TaskAction
    fun injectSource() {
        val packagePath = packageName.get().replace('.', File.separatorChar)
        val outputFile = outputDirectory.file(
            packagePath + File.separator + "$FOG_CLASS_NAME.java"
        ).get().asFile
        StringFogClassGenerator.generate(
            outputFile,
            packageName.get(),
            FOG_CLASS_NAME,
            implementation.get(),
            mode.get()
        )
    }
}
