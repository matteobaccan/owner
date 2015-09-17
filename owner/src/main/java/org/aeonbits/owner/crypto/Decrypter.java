package org.aeonbits.owner.crypto;

import java.io.Serializable;

public interface Decrypter extends Serializable {
    String decrypt( String value );
}
