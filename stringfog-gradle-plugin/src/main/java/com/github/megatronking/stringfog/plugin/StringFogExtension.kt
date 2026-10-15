package com.github.megatronking.stringfog.plugin

import com.github.megatronking.stringfog.IKeyGenerator
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class StringFogExtension {

    companion object {
        val base64 = StringFogMode.base64
        val bytes = StringFogMode.bytes
    }

    /**
     * The package name of the application or the library.
     */
    abstract val packageName: Property<String>

    /**
     * The algorithm implementation for String encryption and decryption.
     * It is required.
     */
    abstract val implementation: Property<String>

    /**
     * A generator to generate a security key for the encryption and decryption.
     */
    abstract val kg: Property<IKeyGenerator>

    /**
     * How the encrypted string presents in java class, default is base64.
     */
    abstract val mode: Property<StringFogMode>

    /**
     * Enable or disable the StringFog plugin. Default is enabled.
     */
    abstract val enable: Property<Boolean>

    /**
     * Enable or disable the StringFog debug message print. Default is disabled.
     */
    abstract val debug: Property<Boolean>

    /**
     * The java packages will be applied. Default is effect on all packages.
     */
    abstract val fogPackages: ListProperty<String>
}
