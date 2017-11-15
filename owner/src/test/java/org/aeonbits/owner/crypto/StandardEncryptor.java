package org.aeonbits.owner.crypto;

import org.aeonbits.owner.util.Base64;

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

    public String encrypt( String plainData ) {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance( this.algorithm );
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] encVal = c.doFinal( plainData.getBytes( this.encoding ) );
            String encryptedValue = Base64.encode( encVal );
            return encryptedValue;
        } catch ( Exception cause ) {
            throw new IllegalArgumentException( cause.getMessage(), cause );
        }
    }

    public String decrypt(String encryptedData) throws IllegalArgumentException {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance( this.algorithm );
            c.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedValue = Base64.decode(encryptedData);
            byte[] decValue = c.doFinal(decodedValue);
            String decryptedValue = new String(decValue, this.encoding );
            return decryptedValue;
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
