package com.github.megatronking.stringfog.plugin

import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class StringFogRootPlugin : Plugin<Project> {

    companion object {
        private const val PLUGIN_NAME = "stringfog"
    }

    override fun apply(project: Project) {
        if (project != project.rootProject) {
            throw GradleException("stringfog-root must be applied to the root project")
        }

        val rootExtension = project.extensions.create(
            PLUGIN_NAME,
            StringFogRootExtension::class.java
        ).apply {
            kg.convention(RandomKeyGenerator())
            mode.convention(StringFogMode.base64)
            enable.convention(true)
            debug.convention(false)
            fogPackages.convention(emptyList())
            excludedProjects.convention(emptySet())
        }

        project.allprojects { target ->
            if (target != project) {
                configureAndroidProject(target, rootExtension)
            }
        }
    }

    private fun configureAndroidProject(
        project: Project,
        rootExtension: StringFogRootExtension
    ) {
        var configured = false
        STRING_FOG_ANDROID_PLUGIN_IDS.forEach { pluginId ->
            project.pluginManager.withPlugin(pluginId) {
                if (configured) {
                    return@withPlugin
                }
                configured = true
                val isExcluded = rootExtension.excludedProjects.get().contains(project.path)
                if (isExcluded || !rootExtension.enable.get()) {
                    return@withPlugin
                }

                project.pluginManager.apply(StringFogPlugin::class.java)
                val moduleExtension = project.extensions.getByType(StringFogExtension::class.java)
                moduleExtension.implementation.convention(rootExtension.implementation)
                moduleExtension.kg.convention(rootExtension.kg)
                moduleExtension.mode.convention(rootExtension.mode)
                moduleExtension.enable.convention(rootExtension.enable)
                moduleExtension.debug.convention(rootExtension.debug)
                moduleExtension.fogPackages.convention(rootExtension.fogPackages)

                val projectPath = project.path
                val dependency = rootExtension.runtimeDependency.orElse(
                    project.providers.provider<String> {
                        throw GradleException(
                            "Missing stringfog runtimeDependency config for $projectPath"
                        )
                    }
                )
                project.dependencies.addProvider("implementation", dependency)
            }
        }
    }
}
