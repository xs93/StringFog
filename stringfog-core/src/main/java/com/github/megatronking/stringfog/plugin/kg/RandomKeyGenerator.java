package com.github.megatronking.stringfog.plugin.kg;

import com.github.megatronking.stringfog.IKeyGenerator;

import java.security.SecureRandom;

/**
 * Dynamic random security key for encryption.
 *
 * @author Megatron King
 * @since 2022/2/14 22:40
 */
public class RandomKeyGenerator implements IKeyGenerator {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_LENGTH = 8;

    private transient volatile SecureRandom mSecureRandom;
    private final int mLength;

    public RandomKeyGenerator() {
        this(DEFAULT_LENGTH);
    }

    public RandomKeyGenerator(int length) {
        mLength = length;
        mSecureRandom = new SecureRandom();
    }

    @Override
    public byte[] generate(String text) {
        byte[] key = new byte[mLength];
        getSecureRandom().nextBytes(key);
        return key;
    }

    private SecureRandom getSecureRandom() {
        SecureRandom secureRandom = mSecureRandom;
        if (secureRandom == null) {
            synchronized (this) {
                secureRandom = mSecureRandom;
                if (secureRandom == null) {
                    secureRandom = new SecureRandom();
                    mSecureRandom = secureRandom;
                }
            }
        }
        return secureRandom;
    }

}
