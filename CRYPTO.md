CRYPTO
======

**Internal working document — not published on the project site.**

Started 2026-08-13 as the argument for shipping a cipher, built on 2026-08-14, and **trimmed on
2026-08-18 to what only it holds.** What went: the survey of what the other libraries do, which
`COMPARISON.md` carries better and with a table of sources; the case for retiring the published example,
which the site page now makes to the reader who copied it; and the list of open questions, since there are
none.

What is left is the reasoning, and it is here because the two documents that could hold it cannot. The
[Crypto support](https://matteobaccan.github.io/owner/docs/crypto/) page says **what the thing is** — the
marker, the two ciphers, the token, the tool, and a section of its own for what it deliberately does not
do. `COMPARISON.md` says **what everybody else does**. Neither can say *why ours is built this way, and
where the design was wrong before it was built* — which is what gets re-litigated by whoever next opens
`AesGcmHandler` and asks why the iteration count travels in the token instead of in the handler's name.

Read from **What the construction settled**, near the bottom: that is where each decision meets what
building it actually found, including the two that came out differently from how they were posed.


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


The design
----------

### The form: a marker, with the token inside it

**Amended 2026-08-13, before anything was built.** This section first proposed a value prefix,
`ENC:1:<base64>`, and called it and the `${$handler::…}` marker of `TODO.md` two ways of saying the
same thing. They are not, and the difference is the whole design:

- **A prefix is the format of the cipher text.** It makes the cipher text self-describing and leaves
  decryption exactly where it is — chosen by `@EncryptedValue`, on a method. It therefore fixes
  neither #285 nor a value that refers to an encrypted one.
- **A marker is where decryption is decided**, moved out of the declaration and into the value.

So the question was never which of the two. It is whether the token belongs *inside* the marker, and
it does — minus one thing:

```
${$aes-gcm::<base64 of  salt(16) ‖ iv(12) ‖ ciphertext ‖ tag(16)>}
```

**The scheme number is gone, because the handler name is the scheme identifier.** `aes-gcm` is what
scheme `1` was going to be; an asymmetric one is `${$rsa::…}` — a different name, not a different
number. That removes a registry we would have had to own and hand out: a third party shipping a
cipher would have to ask us for a number, or take one and collide. A name has no such problem, which
is the same reason loaders are found by class and formats by extension rather than by an integer we
assign.

What the marker settles by construction, rather than by a second mechanism:

| | why |
|---|---|
| **#285**, `fill()` does not decrypt (closed 2026-08-14) | the marker *is* expansion, and `fill` expands since 2.0.0 |
| a value that refers to an encrypted one | `crypto.password=${$aes-gcm::…}` and `jdbc.url=…${crypto.password}`: expansion recurses into the value |
| the round trip of `store()` | the properties hold the marker text, so `store` writes the marker back |
| `@Sensitive` | a marker in a listing is unreadable already |

**Probed on 2026-08-13, against the parser as it stands**, since all of this rests on the expression
surviving intact:

```
${$aes-gcm::k7Hn+/x=}               reaches resolve() whole — base64's + / = are safe
${$vault::secret/data/app:v2}       colons in the payload survive
…password=${$aes-gcm::k7Hn+/x=}&z=1 substitutes inline
${$${env}::inner}  with env=aes-gcm resolves — the handler name may itself be a variable
${$aes-gcm::has}brace}              BREAKS — } is the one character a payload may not hold
```

The last line is the only constraint the format has to respect, and base64 never produces a `}`. The
fourth is a free property worth keeping in mind: a name that is itself a variable is one way a key
rotation could be driven from outside the file.

### Who owns the payload

Asked on 2026-08-13: could the marker carry options too, `${$aes-gcm::…#salt=xxx}`, the way a source
carries them in its fragment? **Syntactically yes** — probed, `#`, `&` and `=` all survive inside
`${…}`, and a `#` in the middle of a value is not a comment in a `.properties` file, where only a
line may start one.

**For cryptographic parameters, no**, for three reasons that are worth keeping written down:

- **The salt belongs inside the token.** GCM's tag authenticates the cipher text; a salt sitting
  outside it in a fragment is not covered and can be edited on its own. The damage is bounded — a
  wrong key gets derived and decryption fails — so it is denial of service rather than forgery, but
  the principle holds: everything decryption depends on goes inside the authenticated envelope, or is
  bound to it. Outside is strictly worse for nothing gained.
- **A knob is what we decided not to have.** The Jasypt finding above is exactly this: their CLI
  produces deterministic cipher text because the IV generator is an option one can forget. If
  `#iterations=1000` is expressible, somebody will write it or copy it from a blog post.
- **The salt is an output, not a choice.** It is produced by the tool and reproduced verbatim; making
  it look like a parameter invites somebody to pick one.

**And it is not needed, because the payload is opaque to the library.** OWNER dispatches on the name
and hands the rest to the handler as text — there is nothing here for us to interpret. A handler that
genuinely wants parameters defines them inside its own payload, in whatever syntax suits it. So:
**the library owns the envelope, the handler owns the payload.**

That is the mirror image of `SourceOptions`, where the fragment *is* ours — because there OWNER
interprets it, reading `required` itself and routing `dialect` to a loader. The one rule a payload
must respect is the one the earlier probe found: **no `}`**.

What legitimately varies — *which key* — is already covered by the handler **name**: register
`aes-gcm-2024` alongside `aes-gcm-2025` and a key rotation becomes an edit to the file, one value at a
time. Which is the argument that removed the scheme number, applied a second time.

### What it does not settle

`@EncryptedValue` and `@DecryptorClass` **stay**, for everybody who already has them, with the
limitations they have and the warning 2.0.0 now gives about the referring case. Two mechanisms
coexist, which is the cost of not breaking anybody, and it is the same cost SmallRye pays.

One conflict has to be closed with them: **a method carrying `@EncryptedValue` whose value is a
marker.** Expansion runs before `decryptIfNecessary`, so the marker would yield plain text and the
decryptor would then be handed plain text to decrypt. The two say the opposite of each other and
should be **refused together**, the way `@Mandatory` and `Optional` on one method are. It is
computable when the configuration is created: the keys carrying `@EncryptedValue` are already
collected in a set, and a value beginning `${$` is visible.

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

**Built on 2026-08-14, in the same release**, after the symmetric one and on the same envelope. This
section was written expecting to postpone it; what changed is that the envelope turned out to cost the
asymmetric case nothing — a second handler name and a second class, with no change to `aes-gcm` and none
to the substitution. `RsaHandler`, name `rsa-oaep`.

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

**How both were answered.** The hybrid construction is what shipped, exactly as anticipated: a fresh
AES-256 key per value, wrapped with RSA-OAEP, both in the token. On key material the prediction was wrong
in a useful way — **it is not a keystore.** The constructor takes `PublicKey` and `PrivateKey`, which is
the same rule as everywhere else here (the caller brings the material), and the two static readers
`publicKeyFrom` / `privateKeyFrom` take the PEM that `openssl` writes, which is what people actually
have. A keystore is then three lines of JDK on the caller's side rather than four settings on ours.
`publicKeyFrom` also accepts a `CERTIFICATE` block, which is what a keystore exports; PKCS#1
(`BEGIN RSA PRIVATE KEY`) is refused with the one `openssl` command that converts it, because the JDK has
no `KeySpec` for it and an ASN.1 reader does not belong in a configuration library.

Three things the building added that this section did not foresee:

- **A four-byte fingerprint of the modulus, at the head of the token.** Not security — a public key is
  public — but the diagnosis for the mistake this arrangement *invites*: encrypting against the wrong
  public key is silent to whoever does it, since they cannot read back what they wrote. Without it the
  deployment fails with "could not be decrypted"; with it, the message names both key pairs. Both halves
  of an RSA pair expose the modulus, which is why the fingerprint can be computed from either.
- **OAEP is given an explicit `OAEPParameterSpec`.** Naming the transformation alone leaves MGF1 on SHA-1
  in the JDK, which is self-consistent but is not what the name says.
- **A modulus below 2048 bits is refused**, and so is a public and a private key that are not two halves
  of one pair.

The name is `rsa-oaep` and not the `${$rsa::…}` written above: the marker name is what a token must be
read with, so it pins down the padding, while the class is just a Java identifier. Nothing about
`aes-gcm` changed for any of it, which was the point of dispatching on a name.


What the construction settled
-----------------------------

Written 2026-08-14, after building it. Seven of the eight questions below are answered; they are left
standing because the reasoning is worth more than the verdict, and because two of them were answered
*differently* from how they were posed.

**1, the iteration count — 210,000, and it travels in the token.** Measured before being chosen: one
derivation costs 38 ms on JDK 24 and 49 ms on JDK 17, and it happens once per salt. The departure is the
second half. This document said the count would be "fixed in a scheme number", meaning fixed in what
`aes-gcm` means; it is four bytes ahead of the salt instead. Nothing else could read a file written last
year once the guidance moves, and a handler name per iteration count is not a rotation, it is an accident.
**This is not the knob the "Who owns the payload" section argues against**: no syntax offers it to whoever
edits the file, it is an output of the tool like the salt, the whole header is passed to GCM as additional
authenticated data, and a token asking to be read with fewer than 100,000 iterations is refused rather
than honoured. The argument that put the salt inside the authenticated envelope is the same one that puts
the count there.

**2, the name — `AesGcmHandler`, one class.** Named after what it does, because a second construction
arrives as a second name and a second class rather than as a version of this one. Encryption and
decryption are in it together: they share a token format, and a format written down in two places
disagrees with itself.

**3, the tool — a `main()` in the core jar**, `java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool`,
with no `Main-Class` in the manifest, so the jar gains a class and not an identity as an executable. A
separate artifact would be one more thing to release and one more thing to fetch before you can put a
password in a file. The constraint held: neither the passphrase nor the values may be arguments, and a
bare argument is refused with that sentence rather than accepted.

**4, `StandardEncryptor` stays in the test suite**, as the fixture of the tests that were written against
it. What it stopped being is *published*: the site page no longer reproduces its source, and says what
changed to whoever copied it.

**6, the warning about a weak construction — not given.** An `@EncryptedValue` whose value is not a marker
is now the ordinary shape of every configuration written before today, and a line at `CONFIG` on all of
them would be noise, not diagnosis.

**7, registration — explicit only, no discovery.** A loader found on the classpath reads files that are
already yours; a handler found on the classpath answers for the values inside them, and a jar arriving as
a transitive dependency is not a decision anybody took. A name may not be empty and may not contain
whitespace or any of `$ : { }`, checked at registration, which is the one moment the author of the handler
is present to be told.

**8, an unknown handler — an `UnsupportedOperationException` from `HandlersManager.resolve`**, naming what
is registered so the message is actionable, and never repeating the payload, which is the one part of a
marker that may be a secret.

Two things were decided that no question asked about. **What a handler answers is not expanded again**: a
value read from a property is, because that is how `a=${b}` works, but text arriving from outside the
configuration is exactly the text that must not be read as a template. And **the passphrase and the
derived keys are `transient`**, because a Config object is serializable and a handler is reachable from
one, so writing them out would put a secret in a file nobody chose to protect.

One thing had to be found by running the tool rather than by testing it: `System.console()` meant "there is
a terminal" until JDK 21 and stopped meaning it in JDK 22, where a Console is returned even with the
streams redirected. Asked the old question, the tool called `readPassword` on a pipe and silently took the
first value being piped in as the passphrase. `Console.isTerminal()` is the new question and does not exist
on the Java 8 baseline, so it is asked by reflection where it exists.


Two things learnt afterwards, on 2026-08-18
-------------------------------------------

Neither changes the design; both are the kind of thing that gets re-litigated if it is not written down.

**The marker is also the answer to dependency injection**, and `@DecryptorClass` is not. A class named in an
annotation is built by this library out of a no-argument constructor, so a decryptor that needs a
collaborator — a key management client, an HSM session — cannot come from a container.
[#222](https://github.com/matteobaccan/owner/issues/222) had been asking for a hook to fix that since 2018
and the most recent comment on it, in March 2025, is exactly this case: *"is there a way to allow guice to
create the class that decrypts? right now I have to do `getInjector().injectMembers(this)` inside my
`decrypt` method"*. **The envelope built here already answers it**, because a `ValueHandler` is registered
as an **object**:

```java
ConfigFactory.registerValueHandler(injector.getInstance(MyHandler.class));
```

So the migration from `@EncryptedValue` on a method to `${$name::…}` in the value is worth recommending for
a reason beyond the cipher: it moves the decryptor from something we construct to something you construct.
That is now the answer given on the issue, and the hook it asked for stays unbuilt.

**What the tests deliberately do not cover, and why** — because the next person to look at the coverage of
`AesGcmHandler`, `RsaHandler` and `EncryptTool` will find around 130 uncovered instructions and should not
spend a day on them:

- **the JVM-capability guards** — *this JVM cannot do AES-256*, *SHA-256 is missing from this JDK* — are
  unreachable on any JDK this library supports, and are there for the one that is not;
- **a failure of the JCE mid-encryption** would need `Cipher` mocked;
- **the certificate date branches** (`CertificateNotYetValidException`, and the warning for an expired one)
  need a certificate built for the purpose. There is no public API to make one, so it would mean either a
  new test dependency for two log lines or **committing a private key to the repository** — which secret
  scanning would flag, correctly. Left uncovered on purpose;
- **`EncryptTool.main`** is one line calling `System.exit`, which under Surefire is not a branch but an
  ending. The terminal prompt has an entry of its own in `TODO.md`, with the reason a test seam was not
  added for it.
