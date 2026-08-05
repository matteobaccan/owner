/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;


public final class CryptoUtils {
    public static StandardEncryptor newEncryptionSilently( String algorithm, String secretKey ) {
        try {
            return newEncryption( algorithm, secretKey );
        } catch ( Exception cause ) {
            cause.printStackTrace();
        }
        return null;
    }
    public static StandardEncryptor newEncryption( String algorithm, String secretKey )
    throws Exception {
        return StandardEncryptor.newInstance( algorithm, secretKey );
    }

    public static Decryptor newDecryptor( String algorithm, String secretKey )
    throws Exception {
        return StandardEncryptor.newInstance( algorithm, secretKey );
    }
}
