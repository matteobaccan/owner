---
title: "Crypto support"
---

What is this feature?
---------------------

Crypto support is available since version 1.0.10 and is part of the stable API.

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


A value that refers to an encrypted one gets the cipher text
------------------------------------------------------------

*Since 2.0.0 this is reported.* Composing a value out of an encrypted property does **not** work, and it
used to fail in silence:

```properties
crypto.password = tzH7IKLCVc0AC72fh5DiZA==
jdbc.url        = jdbc:h2:mem:test?password=${crypto.password}
```

```java
cfg.password();   // the secret       — the method that declares @EncryptedValue decrypts
cfg.jdbcUrl();    // …?password=tzH7IKLCVc0AC72fh5DiZA==   — the cipher text
```

The same password reads two ways depending on how it is asked for. The connection then fails with a wrong
password, or the cipher text travels somewhere a secret was meant to go.

**It is where the annotation is written, not a defect in the substitution.** The properties hold the
cipher text — they have to, or [`store()`](/owner/docs/accessible-mutable/) would write the file back
decrypted — and decryption happens per method, chosen by the `@EncryptedValue` on it. A variable names a
*key*, so the substitution has nothing to read a decryptor from and inserts what it finds.

Since 2.0.0 the library says so when the configuration is created, naming both keys and neither value:

```
WARNING: the value of 'jdbc.url' refers to 'crypto.password', which is declared
         @EncryptedValue. […] Compose the value in Java from the method that
         decrypts it.
```

and [`owner.strict`](/owner/docs/loading-strategies/#refusing-everything-that-would-only-have-been-a-warning)
turns that into a refusal. **The remedy is to compose the value in Java**, from the method that decrypts,
rather than in the properties file.

<div class="note info">
  <h5>The same is true of a converter, and there it cannot be fixed.</h5>
  <p>
    A <code>@ConverterClass</code> is not applied either when a value is read through a variable, and that
    half has no cure at all: a converter answers with a typed object, and there is no room for one inside a
    string. Decryption is text to text, so only the missing decryptor is a question of where the
    declaration sits.
  </p>
</div>

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
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;


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
            String encryptedValue = Base64.getEncoder().encodeToString( encVal );
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
            byte[] decodedValue = Base64.getDecoder().decode( encryptedData );
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
