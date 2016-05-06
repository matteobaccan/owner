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
