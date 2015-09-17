package org.aeonbits.owner.crypto;


public final class CryptoUtils {
    public static AESEncryption newEncryptionSilently( String algorithm, String secretKey ) {
        try {
            return newEncryption( algorithm, secretKey );
        } catch ( Exception cause ) {
            cause.printStackTrace();
        }
        return null;
    }
    public static AESEncryption newEncryption( String algorithm, String secretKey )
    throws Exception {
        return new AESEncryption( algorithm, secretKey );
    }

    public static Decrypter newDecrypter( AESEncryption encription ) {
        return new Decrypter() {
            AESEncryption encription = null;

            @Override
            public String decrypt(String value) {
                try {
                    return encription.decrypt( value );
                } catch ( Exception cause ) {
                    cause.printStackTrace();
                    return value;
                }
            }

            private Decrypter init(AESEncryption encription) {
                this.encription = encription;
                return this;
            }
        }.init(encription);
    }
}
