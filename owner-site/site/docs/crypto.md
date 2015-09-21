---
layout: docs
title: Crypto support
prev_section: singleton
next_section: features
permalink: /docs/crypto/
---
## What is this feature?

This is a experimental feature that adds crypto features support to OWNER.

With Crypto support it is possible to declare with a simple annotation that a property contains an encrypted value
( values which needed to be decrypted ). The `@DecryptorClass` can be specified for class or for every property.
The `@EncryptedValue(@DecryptorClass)` overrides the `@DecryptorClass` in class.

##Which crypto frameworks are supported?

Crypto support allows the use of any framework to decrypt the values. You must to supply a class ( the decryptor )
which implements the `Decryptor` interface, so it is possible to use every framework you want.

##How can I use it?

Suposse you will user the same `@DecryptorClass` to decrypt all values in your configuration:

```java
@DecryptorManagerClass( MyDecryptor1.class )
public interface Sample extends Config {

    @EncryptedValue  
    public String myEncryptedPassword1();

    @EncryptedValue
    public String myEncryptedPassword2();
}
```

And now suppose that you will use different `@DecryptorClass` for the previous properties:

```java
public interface Sample extends Config {

    @EncryptedValue( MyDecryptor1.class )
    public String myEncryptedPassword1();

    @EncryptedValue( MyDecryptor2.class )
    public String myEncryptedPassword2();
}
```

Or if you plan to use the same `@DecryptorClass` for all `@EncryptedValue` except `myEncryptedPassword1`:

```java
@DecryptorManagerClass( MyDecryptor2.class )
public interface Sample extends Config {

    @EncryptedValue( MyDecryptor1.class )
    public String myEncryptedPassword1();

    @EncryptedValue
    public String myEncryptedPassword2();

    @EncryptedValue
    public String myEncryptedPassword3();
}
```

##Can you show me an implementation example about Decryptor?

This is the java code of `IdentityDecryptor.java`, a decryptor which returns the same value that receives for decrypting:
It does nothing with the value to decrypt.

```java
package org.aeonbits.owner.crypto;

public final class IdentityDecryptor
extends AbstractDecryptor {
    @Override
    public String decrypt( String value ) {
        return value;
    }
}
```

It extends the `AbstractDecryptor`, an abstract class that implements the `Decrypt` interface and implements one of its
methods. We need implement the other method, the `decrypt( String value )` method to get our decryptor working.

Another example is the `StandardDecryptor.java`, which uses the javax.crypto features available in JDK.

```java
package org.aeonbits.owner.crypto;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import javax.xml.bind.DatatypeConverter;


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
            String encryptedValue = DatatypeConverter.printBase64Binary( encVal );
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
            byte[] decodedValue = DatatypeConverter.parseBase64Binary( encryptedData );
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

    public static final StandardEncryptor newInstance( String algorithm, String secretKey ) {
        return newInstance( algorithm, secretKey, "UTF-8", secretKey.length() );
    }

    public static final StandardEncryptor newInstance( String algorithm, String secretKey, String encoding, int secretKeySize ) {
        return new StandardEncryptor( algorithm, secretKey, encoding, secretKeySize );
    }
}
```
