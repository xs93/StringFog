package com.github.megatronking.stringfog;

import java.io.Serializable;

/**
 * A generator uses to generate security keys.
 *
 * <p>Implementations are Gradle instrumentation inputs, so all implementation state must be
 * serializable.</p>
 *
 * @author Megatron King
 * @since 2022/1/14 22:15
 */
public interface IKeyGenerator extends Serializable {

    /**
     * Generate a security key.
     *
     * @param text The content text will be encrypted.
     * @return A security key for the encryption.
     */
    byte[] generate(String text);

}
