---
layout: docs
title: Crypto support
prev_section: singleton
next_section: features
permalink: /docs/crypto/
---

What is this feature?
---------------------

This is an experimental feature adding crypto support to OWNER.

With Crypto it is possible to declare, with a simple annotation, that a property contains an encrypted value
( a value which has to be decrypted ). A `@DecryptorClass` can be specified for a class or for each property.
`@EncryptedValue(@DecryptorClass)` overrides a `@DecryptorClass` specified at class level.


Which crypto frameworks are supported?
--------------------------------------

Crypto support allows the use of any framework to decrypt values. You must supply a class
implementing the `Decryptor` interface, where you can use any framework you want in order to decrypt values.


How can I use it?
-----------------

Suppose you will use the same `@DecryptorClass` to decrypt all values in your configuration:

```java
@DecryptorClass( MyDecryptor1.class )
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

Or if you plan to use the same `@DecryptorClass` for all `@EncryptedValue` properties except `myEncryptedPassword1`:

```java
@DecryptorClass( MyDecryptor2.class )
public interface Sample extends Config {

    @EncryptedValue( MyDecryptor1.class )
    public String myEncryptedPassword1();

    @EncryptedValue
    public String myEncryptedPassword2();

    @EncryptedValue
    public String myEncryptedPassword3();
}
```


It works with other annotations...
----------------------------------

... so you can write code like this:

```java
@Key("crypto.list")
@EncryptedValue
@Separator(",")
@DefaultValue("Pfzoiet5E5zN2/7tfgrGLQ==")
List<String> cryptoList();
```


Can you show me an example implementation of Decryptor?
-------------------------------------------------------

This is the source code of `IdentityDecryptor.java`, a no-op Decryptor returning the same value received for decrypting:

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

It extends `AbstractDecryptor`, an abstract class that already implements the `Decrypt` interface and one of its
methods. To get our Decryptor working, we just have to implement the other method, `decrypt( String value )` .

Another example is `StandardDecryptor.java`, which uses the `javax.crypto` features available in JDK.

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
