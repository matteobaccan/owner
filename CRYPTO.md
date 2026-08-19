WHY THE CIPHER IS BUILT THIS WAY
================================

**Internal working document — not published on the project site.**

Argued 2026-08-13, built 2026-08-14, trimmed 2026-08-18 and again 2026-08-19.

**What the thing is lives on the site**, at
[Crypto support](https://matteobaccan.github.io/owner/docs/crypto/): the marker, the two ciphers, the token
layouts, the tool, key rotation, what a handler must respect, and a section of its own for what it
deliberately does not do. **What everybody else does is in `COMPARISON.md`.** Neither of them is repeated
here, and nothing here should ever be the second place a rule is written down — when the two disagree the
page is right and this file is stale.

What is left is the arguing: **why** each decision went the way it did, what was rejected on the way, and
the two that came out differently from how they were posed. It is for whoever next opens `AesGcmHandler`
and asks why the iteration count travels in the token instead of in the handler's name.


The evidence that started it
----------------------------

`StandardEncryptor` was never shipped — it lives in the test suite — but the page reproduced its source for
copying, so in practice it *was* the implementation. Probed on 2026-08-13, and the numbers are here because
the page states the conclusions and not the measurement:

```
enc("hunter2")  ==>  sNlb9RR7VSdiuwnD6TbJhQ==
enc("hunter2")  ==>  sNlb9RR7VSdiuwnD6TbJhQ==      identical, every time

enc("AAAAAAAAAAAAAAAA" + "BBBBBBBBBBBBBBBB")  ==>  9NadLJ7eVvp0efi9/G59lNiIwc8…
enc("AAAAAAAAAAAAAAAA" + "CCCCCCCCCCCCCCCC")  ==>  9NadLJ7eVvp0efi9/G59lH1st7J…
                                                   └─ first block identical ─┘
```

`Cipher.getInstance("AES")` is AES/ECB on every provider, so the leak is between values *and* inside one.

One thing was right and was kept: a wrong key **throws**, and `PropertiesManager.decryptIfNecessary` calls
the one-argument `decrypt` rather than the overload that falls back on a default — so a wrong key fails
loudly instead of quietly producing a default value.

**The weakness was never that the example was written badly. It was that nothing shipped, so the example
was the implementation** — and cryptography is the last thing a library should leave its users to
improvise.


The constraint, and why it did not bite
---------------------------------------

**No external dependencies**, the rule that shapes `FORMATS.md` and everything else. It cost nothing here:
`AES/GCM/NoPadding`, `PBKDF2WithHmacSHA256` and `SecureRandom` are all in `javax.crypto` on the Java 8
baseline. A correct construction was available without adding a line to the dependency list, which is why
this belongs in the **core** rather than in `owner-extras`.


A marker, and not a prefix
--------------------------

**Amended 2026-08-13, before anything was built.** The first draft proposed a value prefix, `ENC:1:<base64>`,
and called it and the `${$handler::…}` marker two ways of saying the same thing. They are not, and the
difference is the whole design:

- **A prefix is the format of the cipher text.** It makes the cipher text self-describing and leaves
  decryption exactly where it is — chosen by `@EncryptedValue`, on a method. It therefore fixes neither
  [#285](https://github.com/matteobaccan/owner/issues/285) nor a value that refers to an encrypted one.
- **A marker is where decryption is decided**, moved out of the declaration and into the value.

So the question was never which of the two.

**The scheme number is gone, because the handler name is the scheme identifier.** `aes-gcm` is what scheme
`1` was going to be; an asymmetric one is a different *name*, not a different number. That removes a
registry we would have had to own and hand out: a third party shipping a cipher would have to ask us for a
number, or take one and collide. A name has no such problem, which is the same reason loaders are found by
class and formats by extension rather than by an integer we assign.

**Probed on 2026-08-13 against the parser as it stands**, since all of it rests on the expression surviving
intact:

```
${$aes-gcm::k7Hn+/x=}               reaches resolve() whole — base64's + / = are safe
${$vault::secret/data/app:v2}       colons in the payload survive
…password=${$aes-gcm::k7Hn+/x=}&z=1 substitutes inline
${$${env}::inner}  with env=aes-gcm resolves — the handler name may itself be a variable
${$aes-gcm::has}brace}              BREAKS — } is the one character a payload may not hold
```

The last line became the documented rule. The fourth is a free property, **left undocumented on purpose**:
it works, nothing tests it, and promising it publicly would pin it down. It is one way a key rotation could
be driven from outside the file, if it is ever wanted.


Who owns the payload
--------------------

Asked on 2026-08-13: could the marker carry options too, `${$aes-gcm::…#salt=xxx}`, the way a source carries
them in its fragment? **Syntactically yes** — probed, `#`, `&` and `=` all survive inside `${…}`, and a `#`
in the middle of a value is not a comment in a `.properties` file, where only a line may start one.

**For cryptographic parameters, no**, for three reasons worth keeping written down:

- **The salt belongs inside the token.** GCM's tag authenticates the cipher text; a salt sitting outside it
  in a fragment is not covered and can be edited on its own. The damage is bounded — a wrong key gets
  derived and decryption fails — so it is denial of service rather than forgery, but the principle holds:
  everything decryption depends on goes inside the authenticated envelope, or is bound to it. Outside is
  strictly worse for nothing gained.
- **A knob is what we decided not to have.** Jasypt's CLI produces deterministic cipher text because the IV
  generator is an option one can forget. If `#iterations=1000` is expressible, somebody will write it or
  copy it from a blog post.
- **The salt is an output, not a choice.** It is produced by the tool and reproduced verbatim; making it
  look like a parameter invites somebody to pick one.

**And it is not needed, because the payload is opaque to the library.** A handler that genuinely wants
parameters defines them inside its own payload, in whatever syntax suits it.

That is the mirror image of `SourceOptions`, where the fragment *is* ours — because there OWNER interprets
it, reading `required` itself and routing `dialect` to a loader.

What legitimately varies — *which key* — is covered by the handler **name**, which is the argument that
removed the scheme number, applied a second time.


What it does not settle
-----------------------

`@EncryptedValue` and `@DecryptorClass` **stay**, for everybody who already has them. Two mechanisms
coexist, which is the cost of not breaking anybody, and it is the same cost SmallRye pays.


Deriving once, not per value
----------------------------

A per-value salt means a per-value key derivation, and at a serious iteration count ten encrypted
properties is ten derivations at configuration time. **The way out was not to weaken the salt**: the tool
uses one salt per invocation and the decryptor caches the derived key by salt. A salt shared by the values
of one file is still unique to that file and that deployment, which is what a salt is for; the IV stays per
value, which is what stops equal secrets looking equal.


Where the passphrase comes from
-------------------------------

Not from the properties, which would be circular. The decryptor is **constructed by the user and
registered**, as a loader is. That **dissolves the hardest question in the whole design instead of
answering it** — and it is the same move that later turned out to answer dependency injection.


The asymmetric case: what building it changed
---------------------------------------------

Written on 2026-08-13 expecting to postpone it; **built on 2026-08-14, in the same release**, because the
envelope turned out to cost it nothing — a second handler name and a second class, with no change to
`aes-gcm` and none to the substitution. Which was the point of dispatching on a name.

Two predictions, one right and one wrong:

- **"RSA cannot encrypt an arbitrary value, so the answer is hybrid."** Right, and that is what shipped.
- **"Key material stops being a passphrase and becomes a keystore, with its own path, type and password."**
  **Wrong, and usefully so.** The constructor takes `PublicKey` and `PrivateKey` — the same rule as
  everywhere else here, the caller brings the material — and the two static readers take the PEM that
  `openssl` writes, which is what people actually have. A keystore is then three lines of JDK on the
  caller's side rather than four settings on ours.

Three things the building added that nothing had foreseen: the four-byte fingerprint, the explicit
`OAEPParameterSpec`, and the refusal of a modulus below 2048 bits. All three are on the page, with what
each is for.

PKCS#1 (`BEGIN RSA PRIVATE KEY`) is refused with the one `openssl` command that converts it, because the
JDK has no `KeySpec` for it and an ASN.1 reader does not belong in a configuration library.

The name is `rsa-oaep` and not the `${$rsa::…}` first written down: the marker name is what a token must be
read *with*, so it pins the padding down, while the class name is just a Java identifier.


What the construction settled
-----------------------------

Written 2026-08-14, after building it. Two of these came out differently from how they were posed.

**The iteration count travels in the token, and that is the departure.** This document said the count would
be "fixed in a scheme number", meaning fixed in what `aes-gcm` means. It is four bytes ahead of the salt
instead, because nothing else could read a file written last year once the guidance moves, and a handler
name per iteration count is not a rotation, it is an accident. **This is not the knob the section above
argues against**: no syntax offers it to whoever edits the file, it is an output of the tool like the salt,
and the whole header is authenticated. The argument that put the salt inside the envelope is the same one
that puts the count there.

**`AesGcmHandler`, one class**, named after what it does, because a second construction arrives as a second
name and a second class rather than as a version of this one. Encryption and decryption are in it together:
they share a token format, and a format written down in two places disagrees with itself.

**The tool is a `main()` in the core jar, with no `Main-Class` in the manifest**, so the jar gains a class
and not an identity as an executable. A separate artifact would be one more thing to release and one more
thing to fetch before you can put a password in a file.

**`StandardEncryptor` stays in the test suite**, as the fixture of the tests written against it. What it
stopped being is *published*.

**No warning about a weak construction.** An `@EncryptedValue` whose value is not a marker is the ordinary
shape of every configuration written before 2.0.0, and a line at `CONFIG` on all of them would be noise,
not diagnosis.

**Registration is explicit, with no classpath discovery.** A loader found on the classpath reads files that
are already yours; a handler found on the classpath answers for the values *inside* them, and a jar
arriving as a transitive dependency is not a decision anybody took.

One thing had to be found by running the tool rather than by testing it: `System.console()` meant "there is
a terminal" until JDK 21 and stopped meaning it in JDK 22, where a Console is returned even with the
streams redirected. Asked the old question, the tool called `readPassword` on a pipe and silently took the
first value being piped in as the passphrase. `Console.isTerminal()` is the new question and does not exist
on the Java 8 baseline, so it is asked by reflection where it exists.


What the tests deliberately do not cover
----------------------------------------

Because the next person to look at the coverage of `AesGcmHandler`, `RsaHandler` and `EncryptTool` will
find around 130 uncovered instructions and should not spend a day on them:

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
