CRYPTO
======

**Internal working document — not published on the project site.**

Started 2026-08-13. What `@EncryptedValue` actually gives somebody today, why that is weaker than it
looks, and what shipping a real one would have to be. The companion documents are `COMPARISON.md`,
which records what the other libraries do, `FORMATS.md`, which is the same kind of document for the
file formats, and `TODO.md`, which holds the ordered backlog.

**Built on 2026-08-14.** `ValueHandler`, `HandlersManager` and the hook in `StrSubstitutor` are the
envelope; `AesGcmHandler` and `RsaHandler` are the two ciphers; `EncryptTool` is the tool; the site page
was rewritten. What the
building decided, and where it departed from what is written below, is in **What the construction
settled** at the bottom, ahead of the open questions — which are now one.


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
| **#285**, `fill()` does not decrypt | the marker *is* expansion, and `fill` expands since 2.0.0 |
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


Open questions
--------------

1. ~~**The iteration count.**~~ Answered above — 210,000, and carried in the token.
   The reasoning as it was posed: OWASP's current guidance for PBKDF2-HMAC-SHA256 is 210,000, which on the
   Java 8 baseline is a noticeable pause. Deriving once per salt makes it once per configuration
   rather than once per value, which probably settles it — but it wants measuring on the oldest JDK
   we support before it is fixed, because once a token is written the count is not a knob the reader
   can change: it is part of what `aes-gcm` means.
2. ~~**The name.**~~ Answered above — `AesGcmHandler`, one class. `AesGcmDecryptor` says the construction, which is honest and matches the handler
   name; something like `StandardDecryptor` says less and ages differently. Since the handler name is
   the identifier, a second construction arrives as a second name and a second class rather than as a
   version of this one — which argues for naming the class after what it does. The class implements
   both `Decryptor` and `Encryptor`, or two classes?
3. ~~**The tool's shape.**~~ Answered above — a `main()` in the core jar. A `main()` in the core jar, reachable with `java -cp owner.jar …`, or a
   separate artifact? The core has no `main` today, and adding one is a small change to what the jar
   is.
4. ~~**Does `StandardEncryptor` stay in the test suite**~~ Answered above — it stays, as a fixture; what it
   stopped being is *published*. As it was posed: does it stay as a demonstration of the SPI, clearly
   labelled as not a cipher to use, or does it go?
5. ~~**The relationship with the value-level marker.**~~ Answered — see below.
6. ~~**Whether the warning about a weak construction is ours to give.**~~ Answered above — not given. A decryptor somebody wrote
   themselves may be anything, and we cannot inspect it. But an `@EncryptedValue` property whose
   value is not a marker is at least *not* using what we ship, which is something we could say once
   at `CONFIG`. It may also be noise.
7. ~~**Where a handler is registered, and what a name may be.**~~ Answered above — registration only, and a name may not contain whitespace or `$ : { }`. The loaders are the precedent —
   `registerLoader(new DotEnvLoader(dialect))` on the factory, plus `ServiceLoader` discovery — and
   registering a *configured instance* is also what dissolves the question of where the passphrase
   comes from: the caller brings it. Left to decide: whether discovery applies to handlers at all
   (a cipher found on the classpath and silently enabled is a different proposition from a file
   format), and what shape a name may take, since it has to be told apart from an ordinary key.
8. ~~**What an unknown handler does.**~~ Answered above — an `UnsupportedOperationException` naming what is registered. Settled in principle on 2026-08-13 and recorded in `TODO.md`:
   an expression beginning `$` and containing `::` **is** a handler reference, and one naming a
   handler that is not registered is an error rather than a fallback — otherwise a misspelt name
   resolves to the empty string, which for a password is the worst answer available. What is left is
   where that refusal is raised and what it says.

Question 5 was **answered on 2026-08-13** and is folded into the design above: the marker is the
form, the token is what goes inside it, and the scheme number is gone. The other seven were answered by
building it, on 2026-08-14, and the verdicts are in the section before this one.

**And the question that was not in this list — the asymmetric case — was answered the same day**, by
building it. See the section above. There is nothing open in this document any more.
