package org.aeonbits.owner.crypto;

/**
 * An encryptor is a class which is able to encrypt and decrypt a value.
 * <p>
 * This interface allows separation between API needed by OWNER at decription level and a implementation with a
 * decription library, as javax.crypto or others.
 */
public interface Encryptor extends Decryptor {
    /**
     * Encrypts a value.
     * @param value the value to encrypt.
     * @return the value encrypted
     * @throws IllegalArgumentException on any failure, with the message and the original exception.
     */
    String encrypt( String value ) ;
}
