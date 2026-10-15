package com.github.megatronking.stringfog.plugin

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

internal val STRING_FOG_ANDROID_PLUGIN_IDS = arrayOf(
    "com.android.application",
    "com.android.library",
    "com.android.dynamic-feature"
)

class StringFogPlugin : Plugin<Project> {

    companion object {
        private const val PLUGIN_NAME = "stringfog"
    }

    override fun apply(project: Project) {
        val stringFog = project.extensions.create(
            PLUGIN_NAME,
            StringFogExtension::class.java
        ).apply {
            kg.convention(RandomKeyGenerator())
            mode.convention(StringFogMode.base64)
            enable.convention(true)
            debug.convention(false)
            fogPackages.convention(emptyList())
        }
        var configured = false

        STRING_FOG_ANDROID_PLUGIN_IDS.forEach { pluginId ->
            project.pluginManager.withPlugin(pluginId) {
                if (!configured) {
                    configured = true
                    configureAndroid(project, stringFog)
                }
            }
        }
    }

    private fun configureAndroid(project: Project, stringFog: StringFogExtension) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            if (!stringFog.enable.get()) {
                return@onVariants
            }

            val implementation = stringFog.implementation.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException(
                    "Missing stringfog implementation config for ${project.path}"
                )
            val configuredPackageName = stringFog.packageName.orNull?.takeIf { it.isNotBlank() }
            val packageName = configuredPackageName?.let { project.providers.provider { it } }
                ?: variant.namespace
            val className = packageName.map { "$it.${SourceGeneratingTask.FOG_CLASS_NAME}" }
            val fogPackages = stringFog.fogPackages.get()
            val keyGenerator = stringFog.kg.get()
            val mode = stringFog.mode.get()

            variant.instrumentation.transformClassesWith(
                StringFogTransform::class.java,
                InstrumentationScope.PROJECT
            ) { params ->
                params.implementation.set(implementation)
                params.className.set(className)
                params.fogPackages.set(fogPackages)
                params.keyGenerator.set(keyGenerator)
                params.mode.set(mode)
            }
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )

            val taskName = "generateStringFog${variant.name.replaceFirstChar { it.uppercaseChar() }}"
            val outputDirectory = project.layout.buildDirectory.dir(
                "generated/source/stringFog/${variant.name}"
            )
            val provider = project.tasks.register(
                taskName,
                SourceGeneratingTask::class.java
            ) { task ->
                task.outputDirectory.set(outputDirectory)
                task.packageName.set(packageName)
                task.implementation.set(implementation)
                task.mode.set(mode)
            }
            variant.sources.java?.addGeneratedSourceDirectory(
                provider,
                SourceGeneratingTask::outputDirectory
            )
        }
    }
}
