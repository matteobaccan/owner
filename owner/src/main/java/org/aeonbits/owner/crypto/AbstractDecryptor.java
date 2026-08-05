/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;

/**
 * An abstract implementation of Decryptor.
 * It implements the decrypt( String, String ).
 */
public abstract class AbstractDecryptor
implements Decryptor {
    @Override
    public String decrypt( String value, String defaultValue ) {
        try {
            return this.decrypt( value );
        } catch ( IllegalArgumentException cause ) {
            return defaultValue;
        }
    }
}
