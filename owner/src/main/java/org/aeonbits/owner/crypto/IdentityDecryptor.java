/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;

/**
 * IdentityDecryptor is a no-op decryptor: it accepts a value and returns the same value for both decrypting and encrypting.
 * It is used as default value for {@link org.aeonbits.owner.Config.EncryptedValue} and {@link org.aeonbits.owner.Config.DecryptorClass}.
 */
public final class IdentityDecryptor
extends AbstractDecryptor {

    /**
     * Built with no arguments, because it is named by an annotation and instantiated
     * reflectively - and because there is nothing to configure about doing nothing.
     */
    public IdentityDecryptor() {
    }

    @Override
    public String decrypt( String value ) {
        return value;
    }
}
