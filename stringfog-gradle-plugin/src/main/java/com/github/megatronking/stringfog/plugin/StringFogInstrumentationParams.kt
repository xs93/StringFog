package com.github.megatronking.stringfog.plugin

import com.android.build.api.instrumentation.InstrumentationParameters
import com.github.megatronking.stringfog.IKeyGenerator
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

abstract class StringFogInstrumentationParams : InstrumentationParameters {
    @get:Input
    abstract val implementation: Property<String>

    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val fogPackages: ListProperty<String>

    @get:Input
    abstract val keyGenerator: Property<IKeyGenerator>

    @get:Input
    abstract val mode: Property<StringFogMode>
}
