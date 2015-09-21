---
layout: docs
title: Crypto support
prev_section: building
next_section: contributing
permalink: /docs/crypto/
---

This is a experimental feature that adds crypto features support to OWNER.

With Crypto support it is possible declare that a property contains an encrypted value ( values which needed to be
decrypted ) with a simple annotation. The decryptor can be specified for class or for every property.

Crypto support allows the use of any framework to decrypt the value. You must to supply a class ( the decryptor )
which implements the `Decryptor` interface.

How can I use it?
-----------------

Imagine you will be used the same `Decryptor` for all encrypted values in your configuration:

```java
@DecryptorManagerClass( MyDecryptor1.class )
public interface Sample extends Config {

    @EncryptedValue  
    public String myEncryptedPassword1();

    @EncryptedValue
    public String myEncryptedPassword2();
}
```

And now suppose that you will use different `Decryptor` for the previous properties:

```java
public interface Sample extends Config {

    @EncryptedValue( MyDecryptor1.class )
    public String myEncryptedPassword1();

    @EncryptedValue( MyDecryptor2.class )
    public String myEncryptedPassword2();
}
```

Or if you plans to use the same decryptor for all encryptedValue except `myEncryptedPassword1`:

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
