package org.aeonbits.owner.crypto;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import javax.xml.bind.DatatypeConverter;


public class AESEncryption {
    private final String algorithm;
    private final String encoding;
    private final byte[] keyValue;
    private final int secretKeySize;

    public String getAlgorithm() {
        return this.algorithm;
    }

    public AESEncryption( String algorithm, String keyValue )
    throws UnsupportedEncodingException, NoSuchAlgorithmException {
        this ( algorithm, keyValue, "UTF-8", keyValue.length() );
    }

    public AESEncryption( String algorithm, String keyValue, int secretKeySize )
    throws UnsupportedEncodingException, NoSuchAlgorithmException {
        this ( algorithm, keyValue, "UTF-8", secretKeySize );
    }


    public AESEncryption( String algorithm, String keyValue, String encoding, int secretKeySize )
    throws UnsupportedEncodingException, NoSuchAlgorithmException {
        this.secretKeySize = secretKeySize;
        this.algorithm = algorithm;
        this.encoding = encoding;
        this.keyValue = keyValue.getBytes( encoding );
    }

    public String encrypt(String plainData) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance( this.algorithm );
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal( plainData.getBytes( this.encoding ) );
        String encryptedValue = DatatypeConverter.printBase64Binary( encVal );
        return encryptedValue;
    }

    public String decrypt(String encryptedData) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance( this.algorithm );
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decordedValue = DatatypeConverter.parseBase64Binary( encryptedData );
        byte[] decValue = c.doFinal(decordedValue);
        String decryptedValue = new String(decValue, this.encoding );
        return decryptedValue;
    }

    private Key generateKey() throws Exception {
        return new SecretKeySpec( this.keyValue, this.getAlgorithm() );
    }
}
