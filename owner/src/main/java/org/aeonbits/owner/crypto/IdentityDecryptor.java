package org.aeonbits.owner.crypto;

/**
 * IdentityDecryptor is a no-op decryptor: it accepts a value and returns the same value for both decrypting and encrypting.
 * It is used as default value for {@link org.aeonbits.owner.Config.EncryptedValue} and {@link org.aeonbits.owner.Config.DecryptorClass}.
 */
public final class IdentityDecryptor
extends AbstractDecryptor {
    @Override
    public String decrypt( String value ) {
        return value;
    }
}
