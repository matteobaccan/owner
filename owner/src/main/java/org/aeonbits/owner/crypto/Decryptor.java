package org.aeonbits.owner.crypto;

import java.io.Serializable;

/**
 * A decryptor is a class which is able to decrypt a value.
 * <p>
 * This interface allows separation between API needed by OWNER at decription level and a implementation with a
 * decription library, as javax.crypto or others.
 */
public interface Decryptor extends Serializable {
    /**
     * Decrypts a value.
     * @param value the value to decrypt.
     * @return the value decrypted
     * @throws IllegalArgumentException on any failure, with the message and the original exception.
     */
    String decrypt( String value );

    /**
     * Decrypts a value, and when the value can not be decrypted returns a default value.
     * @param value the value to decrypt.
     * @param defaultValue the value to return in case a failure occurst.
     * @return the value decrypted or, in case a failure occurs, the default value.
     */
    String decrypt( String value, String defaultValue );
}
