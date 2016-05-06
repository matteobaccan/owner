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
