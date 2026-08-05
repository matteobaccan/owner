/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;

public class SampleDecryptor extends AbstractEncryptor {
    private final StandardEncryptor encrypter;

    public SampleDecryptor( String algorithm, String secretKey ) {
        this.encrypter = StandardEncryptor.newInstance( algorithm, secretKey );
    }

    @Override
    public String decrypt( String value ) {
        try {
            return this.encrypter.decrypt( value );
        } catch ( Exception cause ) {
            throw new IllegalArgumentException( cause.getMessage(), cause );
        }
    }

    @Override
    public String decrypt( String value, String defaultValue ) {
        try {
            return decrypt( value );
        } catch ( Exception cause ){
            return defaultValue;
        }
    }

    @Override
    public String encrypt( String value ) {
        try {
            return this.encrypter.encrypt( value );
        } catch ( Exception cause ) {
            throw new IllegalArgumentException( cause.getMessage(), cause );
        }
    }
}
