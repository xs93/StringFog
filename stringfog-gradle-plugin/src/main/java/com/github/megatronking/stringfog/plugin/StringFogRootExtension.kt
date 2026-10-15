package com.github.megatronking.stringfog.plugin

import com.github.megatronking.stringfog.IKeyGenerator
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class StringFogRootExtension {

    /**
     * The algorithm implementation for String encryption and decryption.
     */
    abstract val implementation: Property<String>

    /**
     * The external module dependency added to every target Android module.
     */
    abstract val runtimeDependency: Property<String>

    /**
     * A generator shared as the default configuration of every target module.
     */
    abstract val kg: Property<IKeyGenerator>

    /**
     * How the encrypted string presents in java class, default is base64.
     */
    abstract val mode: Property<StringFogMode>

    /**
     * Enable or disable StringFog for all target modules. Default is enabled.
     */
    abstract val enable: Property<Boolean>

    /**
     * Enable or disable StringFog debug messages. Default is disabled.
     */
    abstract val debug: Property<Boolean>

    /**
     * Java package prefixes encrypted in every target module.
     */
    abstract val fogPackages: ListProperty<String>

    /**
     * Gradle project paths excluded from automatic StringFog configuration.
     */
    abstract val excludedProjects: SetProperty<String>
}
