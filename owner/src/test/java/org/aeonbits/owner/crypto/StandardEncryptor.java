/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.Key;

public class StandardEncryptor extends AbstractEncryptor {
    private final String algorithm;
    private final String encoding;
    private final byte[] secretKey;
    private final int secretKeySize;

    public StandardEncryptor( String algorithm, String secretKey, String encoding, int secretKeySize ) {
        try {
            this.secretKeySize = secretKeySize;
            this.algorithm = algorithm;
            this.encoding = encoding;
            this.secretKey = secretKey.getBytes( encoding );
        } catch (UnsupportedEncodingException cause) {
            throw new IllegalArgumentException( cause.getMessage(), cause);
        }
    }

    public String getAlgorithm() {
        return this.algorithm;
    }

    @Override
    public String encrypt( String plainData ) {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance( this.algorithm );
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] encVal = c.doFinal( plainData.getBytes( this.encoding ) );
            return java.util.Base64.getEncoder().encodeToString( encVal );
        } catch ( Exception cause ) {
            throw new IllegalArgumentException( cause.getMessage(), cause );
        }
    }

    @Override
    public String decrypt(String encryptedData) throws IllegalArgumentException {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance( this.algorithm );
            c.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedValue = java.util.Base64.getDecoder().decode(encryptedData);
            byte[] decValue = c.doFinal(decodedValue);
            return new String(decValue, this.encoding );
        } catch ( Exception cause ){
            throw new IllegalArgumentException( cause.getMessage(), cause );
        }
    }

    private Key generateKey() throws Exception {
        return new SecretKeySpec( this.secretKey, this.getAlgorithm() );
    }

    public static StandardEncryptor newInstance(String algorithm, String secretKey ) {
        return newInstance( algorithm, secretKey, "UTF-8", secretKey.length() );
    }

    public static StandardEncryptor newInstance(String algorithm, String secretKey, String encoding, int secretKeySize ) {
        return new StandardEncryptor( algorithm, secretKey, encoding, secretKeySize );
    }

}
