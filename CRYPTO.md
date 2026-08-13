CRYPTO
======

**Internal working document — not published on the project site.**

Started 2026-08-13. What `@EncryptedValue` actually gives somebody today, why that is weaker than it
looks, and what shipping a real one would have to be. The companion documents are `COMPARISON.md`,
which records what the other libraries do, `FORMATS.md`, which is the same kind of document for the
file formats, and `TODO.md`, which holds the ordered backlog.

Nothing here is built. The open questions are collected at the bottom.


Where we are, measured
----------------------

`org.aeonbits.owner.crypto` in the core contains `Decryptor`, `Encryptor`, `AbstractDecryptor`,
`AbstractEncryptor` and `IdentityDecryptor`, which returns its argument. **That is the whole of it:
the library ships no cipher.** The only concrete implementation, `StandardEncryptor`, lives in the
test suite, and `crypto.md` reproduces its source for the reader to copy — so in practice that class
*is* what people run.

It calls `Cipher.getInstance("AES")`, which every JDK provider resolves to **AES/ECB/PKCS5Padding**.
Probed on 2026-08-13:

```
enc("hunter2")  ==>  sNlb9RR7VSdiuwnD6TbJhQ==
enc("hunter2")  ==>  sNlb9RR7VSdiuwnD6TbJhQ==      identical, every time
```

The same plaintext always gives the same cipher text. Anyone who can read the configuration file
learns **which secrets are equal** — across properties, across environments, across releases — and one
guessable value identifies itself everywhere it appears. It leaks inside a single value too:

```
enc("AAAAAAAAAAAAAAAA" + "BBBBBBBBBBBBBBBB")  ==>  9NadLJ7eVvp0efi9/G59lNiIwc8…
enc("AAAAAAAAAAAAAAAA" + "CCCCCCCCCCCCCCCC")  ==>  9NadLJ7eVvp0efi9/G59lH1st7J…
                                                   └─ first block identical ─┘
```

Two more findings from the same probe:

- **The passphrase is the key.** No derivation and no salt: `secretKey.getBytes("UTF-8")`. It must
  therefore be exactly 16, 24 or 32 bytes — a 13-character one fails with *Invalid AES key length: 13
  bytes* — which pushes whoever chooses it to pad a short word until it fits.
- **No integrity.** Nothing detects a modified cipher text. Whoever can write the file can reorder
  blocks between values, and with ECB that is a meaningful attack rather than a theoretical one.

One thing is right and worth keeping: a wrong key **throws** — *Given final block not properly
padded* — and `PropertiesManager.decryptIfNecessary` calls the one-argument `decrypt`, which is the
throwing one, rather than the overload that falls back on a default. A wrong key therefore fails
loudly rather than quietly producing a default value.

**The weakness is not that the example is written badly. It is that nothing ships, so the example is
the implementation** — and cryptography is the last thing a library should leave its users to
improvise.


The constraint, and why it does not bite here
---------------------------------------------

**No external dependencies**, the rule that shapes `FORMATS.md` and everything else. It does not cost
anything in this case: `AES/GCM/NoPadding`, `PBKDF2WithHmacSHA256` and `SecureRandom` are all in
`javax.crypto` on the Java 8 baseline. A correct construction is available without adding a line to
the dependency list, which is why this belongs in the **core** rather than in `owner-extras`.


What the others do
------------------

Three of the four ship a cipher, and the three agree on more than they disagree.

| | construction | how a value is produced |
|---|---|---|
| **Jasypt** | `PBEWITHHMACSHA512ANDAES_256`: PBKDF2-HMAC-SHA512, 1000 iterations, 16-byte random salt, AES-256-CBC | a CLI, `encrypt.sh` / `JasyptPBEStringEncryptionCLI` |
| **Spring Cloud Config** | `{cipher}` values, symmetric via `encrypt.key` or asymmetric via a keystore | `/encrypt` and `/decrypt` endpoints on the config server |
| **SmallRye / Quarkus** | `${aes-gcm-nopadding::…}`, the key in a configuration property | a Quarkus CLI command |
| **Typesafe Config** | nothing — no encryption concept at all | — |

Two things to take from that table.

**Everybody who ships a cipher also ships a way to produce a value.** Without it the feature cannot
be adopted: you have the annotation and no way to get the text to put in the file. That is most of
why our users copy the example — it is the only thing that will encrypt anything.

**And a trap worth not copying.** Jasypt's own CLI does *not* use an IV generator unless
`ivGeneratorClassName=org.jasypt.iv.RandomIvGenerator` is passed by hand, so the default invocation
of the most-used tool in this space produces deterministic cipher text — the same weakness measured
above. The lesson is not to add the knob and document it properly: it is to have **no knob**, one
construction, and no way to reach a weaker one by leaving something out.


The design
----------

### The token

One base64 string, so that a value in a properties file stays a single unquoted token:

```
ENC:1:<base64 of  salt(16) ‖ iv(12) ‖ ciphertext ‖ tag(16)>
```

- **A scheme number**, `1`. It costs two characters now and is what lets a second scheme — an
  asymmetric one, a different KDF, a changed iteration count — be added later without ambiguity and
  without a flag day: a decryptor reads the number and dispatches, and an unknown number is refused
  by name rather than mis-parsed. This is the same instinct as refusing a keyed nested section:
  **the decision that can be taken back**.
- **Salt and IV travel with the value.** Nothing else to configure, nothing to keep in step, and no
  second property to lose.
- The prefix also makes an encrypted value **recognisable on sight**, which matters for the warning
  described in `TODO.md` and for anybody reading the file.

### The construction

- **AES-256/GCM/NoPadding**, 12-byte IV from `SecureRandom`, 128-bit tag. GCM gives the integrity
  that is missing today, and a random IV per value is what stops two equal secrets looking equal.
- **PBKDF2WithHmacSHA256** over the passphrase with the 16-byte salt. Iteration count to be decided —
  see the open questions.
- A passphrase of **any length**, which is the point of a KDF and removes the padding-to-fit that the
  current example forces.

### Deriving once, not per value

A per-value salt means a per-value key derivation, and at a serious iteration count ten encrypted
properties is ten derivations at configuration time. The way out is not to weaken the salt:

- the **tool** uses one random salt per invocation, so the values it writes in one run share it;
- the **decryptor caches the derived key by salt**, so a file written in one run derives once.

A salt shared by the values of one file is still unique to that file and that deployment, which is
what a salt is for; the IV stays per value, which is what stops equal secrets looking equal.

### Where the passphrase comes from

Not from the properties, which would be circular. The decryptor is **constructed by the user and
registered**, as a loader is — `registerLoader(new DotEnvLoader(dialect))` is the precedent — so the
passphrase arrives from wherever the application already keeps it. That dissolves the hardest
question in the whole design instead of answering it.

### The tool

The half without which the feature cannot be adopted. Shape to be decided, but one constraint is not
negotiable: **the passphrase must not be a command-line argument**, where it lands in the shell
history and in `ps` for every user on the machine. Read it from standard input, or from a named
environment variable.


Asymmetric keys
---------------

**Not in the first version, and the format is what keeps the door open.**

The use case is real and is not the same as the symmetric one: with a key pair, whoever *writes* the
configuration needs only the **public** key, and only the application holds the private one. A
developer or a CI job can add a secret to a file without being able to read the others. Spring Cloud
Config offers exactly this through a keystore, and it is what `sops` and `age` are built around.

Two things make it more than a variation:

- **RSA cannot encrypt an arbitrary value.** RSA-2048 with OAEP takes about 190 bytes, which covers
  most configuration values and then falls off a cliff. The standard answer is **hybrid** — a random
  AES key per value, encrypted with RSA, both packed into the token — which is a second construction
  rather than a parameter.
- **Key material stops being a passphrase** and becomes a keystore, with its own path, type and
  password. That is a second thing to configure and a second thing to get wrong.

Scheme `2` is where it goes when it is wanted. Nothing about scheme `1` has to change for it.


Retiring the example in `crypto.md`
-----------------------------------

**In API terms it costs nothing.** `StandardEncryptor` was never shipped: it lives in
`owner/src/test/java`, and `Decryptor`, `Encryptor` and the two abstract classes — which *are*
published API — do not change. There is nothing to deprecate, because there was never anything
released to deprecate.

Two things it does cost:

- **The test suite has to use something.** It should use what is shipped, which is better than what
  it does now: the tests would exercise the implementation users run instead of a fixture.
- **An obligation to the people who copied it.** They are running AES-ECB in production because the
  documentation told them to. Swapping the page in silence leaves them there. The page has to say
  what changed, why it matters and what to do about it — which also means the retirement is not a
  deletion but a rewrite, and that whatever replaces it should be shorter, because it will say
  *"register this"* rather than *"paste this"*.

Whether the class stays in the test suite as a demonstration of the SPI, or goes entirely, is an open
question below.


Open questions
--------------

1. **The iteration count.** OWASP's current guidance for PBKDF2-HMAC-SHA256 is 210,000, which on the
   Java 8 baseline is a noticeable pause. Deriving once per salt makes it once per configuration
   rather than once per value, which probably settles it — but it wants measuring on the oldest JDK
   we support before it is fixed in a scheme number.
2. **The name.** `AesGcmDecryptor` says the construction, which is honest but ages badly if scheme 2
   arrives; something like `StandardDecryptor` ages better and says less. The class implements both
   `Decryptor` and `Encryptor`, or two classes?
3. **The tool's shape.** A `main()` in the core jar, reachable with `java -cp owner.jar …`, or a
   separate artifact? The core has no `main` today, and adding one is a small change to what the jar
   is.
4. **Does `StandardEncryptor` stay in the test suite** as a demonstration of the SPI, clearly labelled
   as not a cipher to use, or does it go?
5. **The relationship with the value-level marker.** `TODO.md` records the idea of `${$handler::…}`,
   which would make decryption part of the expansion and fix the referring-value defect. The token
   designed here and that marker are two ways of saying the same thing, and they should not both
   exist in different shapes. Decide which is the form before either is built.
6. **Whether the warning about a weak construction is ours to give.** A decryptor somebody wrote
   themselves may be anything, and we cannot inspect it. But an `@EncryptedValue` property whose
   value does not carry a scheme prefix is at least *not* using what we ship, which is something we
   could say once at `CONFIG`. It may also be noise.
