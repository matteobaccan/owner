package org.aeonbits.owner.crypto;

import java.io.Serializable;

public interface Encrypter extends Serializable {
    String encrypt( String value );
}
