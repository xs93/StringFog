package com.github.megatronking.stringfog.plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.github.megatronking.stringfog.StringFogWrapper
import org.objectweb.asm.ClassVisitor

abstract class StringFogTransform : AsmClassVisitorFactory<StringFogInstrumentationParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val params = parameters.get()
        return ClassVisitorFactory.create(
            StringFogWrapper(params.implementation.get()),
            params.fogPackages.get().toTypedArray(),
            params.keyGenerator.get(),
            params.className.get(),
            classContext.currentClassData.className,
            params.mode.get(),
            nextClassVisitor
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return true
    }
}
