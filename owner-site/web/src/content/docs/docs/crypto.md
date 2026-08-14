---
title: "Crypto support"
---

<div class="note warning">
  <h5>If you copied the example that used to be on this page, read the last section first.</h5>
  <p>
    Until 2.0.0 this page reproduced the source of <code>StandardEncryptor</code> for you to paste into
    your own project. That class is <b>AES/ECB with the passphrase used as the raw key</b>, and a file
    encrypted with it discloses which of its secrets are equal. It was never part of the library, so
    nothing was deprecated and nothing broke — but if it is in your code, it is worth an hour.
    <a href="#the-example-this-page-used-to-publish">What changed, and what to do about it</a>.
  </p>
</div>

Since 2.0.0 there are two ways of putting an encrypted value in a configuration, and they are not
equivalent. **Write the marker.** The annotation stays for the configurations that already use it.

|                        | `${$aes-gcm::…}` since 2.0.0 | `@EncryptedValue` since 1.0.10 |
|---|---|---|
| Where it is declared    | in the value                 | on the method                  |
| A cipher is shipped     | yes, AES-256/GCM             | no — you supply the class      |
| `fill()` gets the secret| yes                          | no                             |
| A value referring to it | gets the secret              | gets the cipher text           |
| `store()` writes back   | the marker                   | the cipher text                |


The marker
----------

A value can name what resolves it instead of holding its own text:

```properties
db.password = ${$aes-gcm::AAM0UBtPtHU9kZcgvqX673gZTlmMpp4RxRWoHOoDUGjJI2AYd1o9qYPK}
jdbc.url    = jdbc:h2:mem:test?password=${db.password}
```

Register the handler with the passphrase — from wherever your application already keeps it — and create
the configuration afterwards:

```java
ConfigFactory.registerValueHandler(new AesGcmHandler(passphrase));

MyConfig cfg = ConfigFactory.create(MyConfig.class);
cfg.password();   // the secret
cfg.jdbcUrl();    // …?password=<the secret>, because expansion recurses into the value
```

Nothing goes on the interface. `password()` is an ordinary `String` method.

<div class="note info">
  <h5>The passphrase never comes from the properties.</h5>
  <p>
    That would be circular — the secret protecting the file, kept in the file. You construct the handler
    and register it, the way a <a href="/owner/docs/file-formats/">loader</a> is registered, which is what
    lets the passphrase arrive from an environment variable, a mounted secret, a vault client or anywhere
    else your application already reads it from.
  </p>
</div>


Encrypting a value
------------------

The tool is in the core jar and needs nothing else on the classpath:

```
$ printf 's3cr3t\nhunter2\n' | OWNER_PASSPHRASE='…' \
    java -cp owner-2.0.0.jar org.aeonbits.owner.handlers.AesGcmTool

${$aes-gcm::AAM0UBtPtHU9kZcgvqX673gZTlmMpp4RxRWoHOoDUGjJI2AYd1o9qYPK}
${$aes-gcm::AAM0UBtPtHU9kZcgvqX673gZTlkT++B4i4OY/U+ozDWUAM4GLcG2l1wW}
```

Run it with no `OWNER_PASSPHRASE` and it asks on the terminal, twice, without echo. Markers go to standard
output and everything else to standard error, so `> markers.txt` collects markers and nothing else.

**Neither the passphrase nor the values may be command-line arguments**, and the tool refuses them there
rather than accepting them: a command line stays in the shell history and is visible in `ps` to every user
on the machine. `--name` and `--iterations` are arguments, because neither is secret.

Encrypt a whole file's worth of values **in one run**. Every value of one run shares a salt and gets its
own IV, so reading them back costs one key derivation between them instead of one per property.


What the construction is
------------------------

`base64( iterations(4) | salt(16) | iv(12) | ciphertext | tag(16) )`, where

- **AES-256/GCM** with a 128-bit tag, so an edited value fails loudly rather than decrypting to something
  else;
- **a random IV per value**, so two equal secrets do not produce equal cipher text;
- **PBKDF2-HMAC-SHA256** at 210,000 iterations — OWASP's current guidance — over a passphrase of *any*
  length;
- the whole header is passed to GCM as additional authenticated data, so it cannot be edited on its own.

The iteration count travels in the token so that raising it later leaves files already written readable. It
is not a knob: no syntax offers it to whoever edits the file, and a token asking to be read with fewer than
100,000 iterations is refused rather than honoured.


Rotating a key
--------------

A handler is registered under a name, and the name is what a value refers to. Register two:

```java
ConfigFactory.registerValueHandler(new AesGcmHandler("aes-gcm-2025", current));
ConfigFactory.registerValueHandler(new AesGcmHandler("aes-gcm-2024", previous));
```

```properties
db.password  = ${$aes-gcm-2025::…}   # moved
api.token    = ${$aes-gcm-2024::…}   # not yet
```

Both are readable while the rotation is under way, and it proceeds one value at a time.


Other things a marker can name
------------------------------

The library reads the envelope — the `$`, the name, the `::` — and hands everything after the first `::`
to the handler as text. **It owns the envelope and the handler owns the payload**, so nothing about the
mechanism is specific to cryptography:

```java
public class FileHandler implements ValueHandler {
    public String name() { return "file"; }

    public String resolve(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + path, e);
        }
    }
}
```

```properties
db.password = ${$file::/run/secrets/db_password}
api.token   = ${$vault::secret/data/app:v2}
```

Two rules a handler must respect:

- **it must throw when it cannot answer**, never return the empty string — for a password that is the
  worst available answer, and it is indistinguishable from success;
- **what it returns is not expanded again.** A secret that happens to contain `${` is a secret, not a
  template.

There is no discovery on the classpath. A handler exists only because you registered it: a file format
found on the classpath reads files that are already yours, while a handler found on the classpath would
answer for the values inside them.

A marker naming a handler nobody registered is an **error**, not an empty string — a misspelt name has to
fail loudly for exactly the same reason.


`@EncryptedValue`, and what it does not do
------------------------------------------

Available since 1.0.10 and unchanged. A `@DecryptorClass` can be given for a class or for a single
property, and `@EncryptedValue(SomeDecryptor.class)` overrides the class-level one:

```java
@DecryptorClass(MyDecryptor.class)
public interface Sample extends Config {

    @EncryptedValue
    String myEncryptedPassword1();

    @EncryptedValue(AnotherDecryptor.class)
    String myEncryptedPassword2();
}
```

You supply the `Decryptor`; the library ships none for this path. It composes with the other annotations:

```java
@Key("crypto.list")
@EncryptedValue
@Separator(",")
@DefaultValue("Pfzoiet5E5zN2/7tfgrGLQ==")
List<String> cryptoList();
```

**A method may not carry both**, and a `@EncryptedValue` whose value is a marker is refused when the
configuration is created. Expansion runs first, so the marker would decrypt the value and the decryptor
would then be handed the plain secret to decrypt a second time.


### A value that refers to an encrypted one gets the cipher text

*Reported since 2.0.0.* Composing a value out of an `@EncryptedValue` property does **not** work, and it
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
turns that into a refusal. **The marker is the cure**: written `crypto.password=${$aes-gcm::…}`, the
reference above resolves to the secret, because decryption became part of the expansion instead of being
attached to a method.

<div class="note info">
  <h5>The same is true of a converter, and there it cannot be fixed.</h5>
  <p>
    A <code>@ConverterClass</code> is not applied either when a value is read through a variable, and that
    half has no cure at all: a converter answers with a typed object, and there is no room for one inside a
    string. Decryption is text to text, so only the missing decryptor was a question of where the
    declaration sits.
  </p>
</div>


The example this page used to publish
-------------------------------------

This page carried the full source of `StandardEncryptor` and invited you to copy it. **If you did, you are
running AES/ECB with your passphrase as the raw key**, and that is worth changing.

It was never shipped. The class lives in the test suite, and only the `Decryptor`, `Encryptor` and the two
abstract classes were ever released API — so there is nothing deprecated here, and nothing that stopped
compiling. What there is, is a consequence that was measured rather than assumed:

- `Cipher.getInstance("AES")` is **AES/ECB**. The same plaintext gives the same cipher text every time, so
  a file discloses which of its secrets are equal — the staging password and the production one, the two
  services sharing a key.
- There is **no integrity check**. An edited value decrypts to something else instead of failing.
- The passphrase is used as the **raw key**, which is why it had to be exactly 16, 24 or 32 characters
  long. That is not a key derivation; it is a length requirement.

What to do: encrypt the same values again with the tool above and replace them with markers. The
passphrase can be the same one, and it no longer has to be padded to fit. The `@EncryptedValue`
annotations then come off, because a marker names what decrypts it.

`Decryptor` and `Encryptor` remain part of the API, for anybody who wrote a real implementation against
them. It is `StandardEncryptor` — an example, published for copying, that should not have been — that is
gone from this page.
