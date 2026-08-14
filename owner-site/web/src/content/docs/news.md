---
title: What's new
description: Every OWNER version and what changed in it, newest first.
---

Every version of OWNER and what changed in it, newest first. The one at the top,
2.0.0, has not been released yet.

The 1.0.x announcements were written by
[Luigi R. Viggiano](https://github.com/lviggiano), the original author of OWNER,
at the time of each release, and are kept as they were written — including the
first person, and the links that have since gone stale — because they are a
record of what happened rather than current documentation. The 2.0.0 one is by
[Matteo Baccan](https://github.com/matteobaccan), who maintains the project now.

Published artifacts are listed in the
[releases on GitHub](https://github.com/matteobaccan/owner/releases).

## 2.0.0

:::caution[In preparation — not released yet]
2.0.0 has not been published. What follows is the announcement as it stands while
the version is being prepared, and it may still change before it ships. The latest
released version is [1.0.12](#1012).
:::

Version 2.0.0 is the first release since the project maintenance moved from Luigi Viggiano to Matteo Baccan.
It brings a set of new features, a security hardening pass, a fully modernized build infrastructure, and a long
list of dependency updates accumulated since 1.0.12. Java 8 is now the minimum runtime, which allowed the
removal of some compatibility leftovers dating back to Java 6/7: see the "Removals" section below for the
(short) migration instructions.

#### Why 2.0.0
This release was prepared as 1.0.13 and renumbered before publication, because three of its changes alter the
result of a configuration that used to work, and a patch number would have been a quiet place to put them.
None is expected to affect a real configuration — the whole test suite of the project passes unchanged, and
each is described in full below — but the number should say so rather than the changelog alone:

 * **Braces are matched, not counted from the left.** Up to 1.0.12 a `${` was closed by the first `}` that
   followed it; now it is closed by the one that matches it, which is what makes nested variables possible.
   Only the `${` sequence opens a level, so a lone brace inside an expression is still ordinary text. Should
   an unforeseen combination of braces read differently, `-Downer.nested.variable.expansion=false` restores
   the previous behaviour for the whole JVM, by running the previous implementation unchanged.
 * **A circular variable reference is an error.** A property whose value leads back to itself used to exhaust
   the stack, or — for the shape `a=${a:default}` — to produce an empty string. It now throws an
   `IllegalArgumentException` naming the chain. No cycle ever produced a useful value, but a configuration
   that quietly resolved to the empty string will now fail loudly, which is the point.
 * **Repeated sibling elements in an XML source are numbered.** Two elements of the same name under the same
   parent used to write the same key, so the second overwrote the first and every value but the last was
   lost without a word. They now become `parent.tag[0]` and `parent.tag[1]`, which is what
   [a list is read from](/owner/docs/type-conversion/), and there is no longer a `parent.tag`. An
   element that occurs only once is untouched and keeps its plain key, so a document with no repetition
   reads exactly as it did. What changes is the reading of documents that were already losing data: if a
   configuration reads `parent.tag` from an XML that repeats that element, it was getting the last of
   several values, chosen by nothing better than document order.

Everything else is additive. In particular, a `Map` return type used to throw on every access, so the new
grouping behaviour described below cannot change the result of any configuration that worked.

Beyond the individual changes, this release has an explicit goal: to bring the test coverage as high as it can
practically go, and to bring the number of warnings reported by the static analysers down to zero. A library
that other projects depend on for their configuration has to be trustworthy first and featureful second, and
after several years without a release the most valuable thing to do was to verify — line by line — that the
existing behaviour is the intended one. The "Code quality and test coverage" section below explains what this
means in practice, and what it turned up.

### RELEASE NOTES

OWNER v2.0.0 contains following enhancements and bug fixes.

#### Removals
Java 8 is the minimum runtime required by this release, and the machinery that existed only to support
older JVMs is gone. If you are affected, migration is a one-liner in each case:

 * The `owner-java8` artifact is gone. Its only feature, the support for
   [`default` methods](http://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html) in config
   interfaces, is now built into the core: **replace the `owner-java8` dependency with `owner`** and
   everything keeps working.
 * The `owner-java8-extras` artifact is gone. The `DurationConverter`, `ByteSizeConverter` and the
   `ByteSize`/`ByteSizeUnit`/`ByteSizeStandard` classes it contained are now part of the core `owner`
   artifact, with unchanged package names: **replace the `owner-java8-extras` dependency with `owner`**,
   and no `import` changes. They were shipped apart only because the core had to run on Java 6 and could
   not so much as name `java.time.Duration`; with Java 8 as the minimum that reason is gone, and neither
   the converters nor the byte size classes bring a dependency of their own. `owner-extras` is left with
   what actually needs a third party library on the classpath — the ZooKeeper loader.
 * `ZooKeeperLoader` moved from `org.aeonbits.owner.loaders` to **`org.aeonbits.owner.extras.loaders`**:
   **change the import**, nothing else. It was the one class of `owner-extras` sitting under a package the
   core artifact also owns, and a package cannot live in two modules: as long as it did, the two jars could
   never both be put on the module path, whatever name they declare. The class itself is unchanged, it never
   used anything package-private, and the `Loader` interface it implements stays exactly where it is — a
   custom loader of your own is unaffected, since it lives in a package of yours and only imports
   `org.aeonbits.owner.loaders.Loader`.
 * The internal utility class `org.aeonbits.owner.util.Base64` is gone. It was a runtime-selection shim
   between `java.util.Base64` (Java 8+) and `javax.xml.bind.DatatypeConverter` (Java 6/7), never used by the
   library API itself. If you referenced it, **use [`java.util.Base64`](https://docs.oracle.com/javase/8/docs/api/java/util/Base64.html)
   directly**: `Base64.encode(bytes)` becomes `Base64.getEncoder().encodeToString(bytes)` and
   `Base64.decode(string)` becomes `Base64.getDecoder().decode(string)`.
 * The internal utility method `org.aeonbits.owner.util.Util.eq(a, b)` is gone: it predated
   `java.util.Objects` and did exactly what the standard
   [`Objects.equals(a, b)`](https://docs.oracle.com/javase/8/docs/api/java/util/Objects.html#equals-java.lang.Object-java.lang.Object-)
   does — **use that instead**.
 * The test-support methods `Util.save(File, Properties)`, `Util.saveJar(File, String, Properties)`
   and `Util.delete(File)` are gone from the public API: they were never used by the library itself
   and now live in the test suite. If you relied on them, the standard
   [`Properties.store`](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html#store-java.io.OutputStream-java.lang.String-),
   `java.util.jar.JarOutputStream` and [`java.nio.file.Files`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html)
   APIs cover the same ground.

#### Enhancements
 * **A property may be spelt the way the file spells it**, which closes
   [#116](https://github.com/matteobaccan/owner/issues/116), open since 2015. `String firstName()` now
   finds `firstName`, `first-name`, `first_name` or `FIRST_NAME`:

   ```properties
   first-name = Luigi
   ```

   **Four forms and no more.** Spring Boot 1 matched loosely — separators dropped, case ignored — and Boot
   2 deliberately narrowed it; this is the narrow side of that split, and `firstname`, `FirstName` and
   `first.name` are not spellings of this key. The forms are derived from the *key*, so `@Key("first-name")`
   is equally found under `FIRST_NAME`, and one form applies to the whole key at once — prefixes and
   [nesting](/owner/docs/nested-configuration/) included, so `my-db.user-name` is a spelling of
   `myDb.userName` and a whole section comes along with it.

   **A value that was written beats one that was only defaulted**, whichever spelling holds it; among
   written values the key the method resolves to comes first. Two spellings of one property in one
   configuration mean that one of them is inert, so it is reported as a `WARNING` naming both — and
   refused outright under
   [`owner.strict`](/owner/docs/loading-strategies/#refusing-everything-that-would-only-have-been-a-warning).

   Nothing is added to the properties and nothing is renamed: `store()`, `list()` and `propertyNames()`
   show the keys exactly as they were loaded, `getProperty("firstName")` still answers about `firstName`,
   and a [`Traceable`](/owner/docs/accessible-mutable/#the-traceable-interface) origin stays attached to
   the key that really exists. The prefix a `Map` or an indexed list scans is matched as written, since
   there the prefix decides which keys *are* the group. On by default, off per method or per interface
   with `@DisableFeature(RELAXED_BINDING)`. See
   [How the key may be written](/owner/docs/usage/#how-the-key-may-be-written).

 * **`@Min(12) int port()` is finally checked, and a constraint nobody checks now says so.**
   [Bean Validation](/owner/docs/validation/) has always worked against an OWNER configuration — provided
   the methods were named as JavaBean getters. `Validator.validate(config)` walks *properties*, so
   `@Min(12) int getPort()` was a property called `port` and was checked, while `@Min(12) int port()` — the
   spelling this documentation teaches — was neither a property nor a field and was **passed over without a
   word**, with Hibernate Validator and Apache BVal alike. That is
   [#201](https://github.com/matteobaccan/owner/issues/201), open since 2018, and the dangerous half of it
   was the silence rather than the missing check.

   Put `owner-extras` and a validation provider on the class path and every constrained property is checked
   when the configuration is created, next to the `@Mandatory` check and for the same reason:

   ```
   org.aeonbits.owner.validation.ConfigValidationException:
     ServerConfig: 2 properties do not satisfy the constraints declared on them:
     'port' (port()): must be greater than or equal to 12;
     'hostname' (hostname()): must not be null
   ```

   Every violation in one exception, each naming the **key** — the line to go and change — and never the
   value, which would put a rejected password in a log. Sections are walked into, so a violation inside one
   arrives as `server.port`; `Optional<@Min(12) Integer>` and `List<@Min(12) Integer>` are unwrapped by the
   provider as the specification says; and a getter-named method is reported **once**, not once as a
   property and again as a method.

   **The silence is the part that is gone.** Four method shapes cannot be checked at creation time — one
   taking arguments has no key until it is called, a `default` method is your own code, a nested-section
   accessor is a view that is never null, and a constraint written on an `Optional` applies to the container
   — and each of them is now named, with its reason, as a `WARNING`, or refused outright under
   `-Downer.strict=true`. So is a configuration carrying constraints that nothing on the class path can
   check. An interface whose annotations belong to another framework says so with
   `@DisableFeature(VALIDATION)`, which turns off the report as well as the check.

   Both spellings of the specification are supported — `javax.validation` for the Java 8 world this library
   still compiles for, `jakarta.validation` for everything current — and both are **optional** dependencies
   of `owner-extras`: nothing is shipped, nothing is transitive, and a configuration with no constraint on
   it pays nothing at all. `ConfigValidator` is a service like a loader, so another idea of what a
   constraint means can be plugged in from outside.
 * **A value can name what decrypts it, and a cipher is finally shipped.** Until now this library shipped
   none: `org.aeonbits.owner.crypto` was an SPI and a no-op, and the only concrete implementation lived in
   the test suite, where the documentation reproduced its source for you to copy. That class is AES/ECB
   with the passphrase used as the raw key, so the same plaintext always gave the same ciphertext and a file
   disclosed which of its secrets were equal. **If you copied it, the [Crypto support](/owner/docs/crypto/)
   page now says what to do about it.**

   In its place, a marker in the *value*:

   ```properties
   db.password = ${$aes-gcm::AAM0UBtPtHU9kZcgvqX673gZTlmMpp4RxRWoHOoDUGjJ...}
   jdbc.url    = jdbc:h2:mem:test?password=${db.password}
   ```

   ```java
   ConfigFactory.registerValueHandler(new AesGcmHandler(passphrase));
   ```

   Nothing goes on the interface, and the passphrase never comes from the properties — which would be the
   secret protecting the file, kept in the file. Two ciphers come with it:

   - **`${$aes-gcm::…}`** — AES-256/GCM with a random IV per value, PBKDF2-HMAC-SHA256 at 210,000
     iterations, salt and iteration count travelling in the token. One passphrase, which both writes and
     reads.
   - **`${$rsa-oaep::…}`** — a key pair, so that whoever adds a secret to a configuration **cannot read the
     ones already there**. RSA-OAEP wrapping a per-value AES-256/GCM key, since RSA cannot encrypt an
     arbitrary value.

   `EncryptTool`, in the same jar, turns values into markers:

   ```
   $ printf 's3cr3t
hunter2
' | OWNER_PASSPHRASE='…'        java -cp owner-2.0.0.jar org.aeonbits.owner.handlers.EncryptTool > markers.txt
   ```

   Neither the passphrase nor the values may be command-line arguments, and the tool refuses them there: a
   command line stays in the shell history and is visible in `ps`.

   **Being expansion is what makes it worth having.** `fill()` gets the secret, a value that refers to it
   gets the secret rather than the ciphertext, and `store()` writes the marker back because the properties
   hold its text and not its answer. Those last two are exactly what
   [#285](https://github.com/matteobaccan/owner/issues/285) and half of
   [#287](https://github.com/matteobaccan/owner/issues/287) reported, and they are settled by construction
   rather than by a second mechanism.

   `@EncryptedValue` and `@DecryptorClass` are **not deprecated** and still work as they always did — they
   are what every configuration written before 2.0.0 uses. The one thing refused is carrying both on one
   method, since expansion runs first and the decryptor would then be handed the plain secret.

 * **JNDI is readable as a source**, in the `owner-extras` artifact, which closes
   [#143](https://github.com/matteobaccan/owner/issues/143) — what a container binds, taking part in a
   `MERGE` like any other source:

   ```java
   @LoadPolicy(LoadType.MERGE)
   @Sources({
       "jndi:comp/env/myconfig",
       "file:~/myconfig.properties",
       "classpath:myconfig.properties" })
   public interface MyConfig extends Config { }
   ```

   A relative name is resolved against `java:comp/env/`, a `java:` name is used as written, and
   subcontexts are flattened with a dot like every other tree-shaped format. It needs no dependency, since
   JNDI is in the JDK. A binding that is not a scalar — a `DataSource`, a `UserTransaction` — is skipped
   and named at `CONFIG` rather than refused, because refusing a whole context over one of them would make
   the loader useless in the container it exists for. For a single entry rather than a context there is
   `${$jndi::comp/env/db/password}`.

   **Only `java:` names are accepted, and there is deliberately no setting to allow others.** A JNDI name
   carries its own scheme and `InitialContext` follows it over the network, so `jndi:ldap://somewhere/x`
   would be a configuration file turning into a request to somebody else's server — and a `@Sources` spec
   is expanded before it is read, so it need not even be a constant. To reach a provider elsewhere,
   construct `new JndiLoader(environment)` in Java, where the decision sits next to the credentials it
   needs. That is the same rule this release applies to an encryption passphrase.

 * **`ValueHandler`, the mechanism underneath it, is not about cryptography.** OWNER reads the envelope —
   the `$`, the name, the `::` — and hands everything after it to the handler as text. So a handler of your
   own is a two-method class:

   ```properties
   api.token = ${$vault::secret/data/app:v2}
   tls.key   = ${$file::/run/secrets/tls.key}
   ```

   Handlers are registered and **never discovered on the class path**: a file format found there reads files
   that are already yours, while a handler found there would answer for the values inside them. A marker
   naming a handler nobody registered is an error rather than the empty string, which for a password is the
   worst answer available. See [`ValueHandlerExample`](https://github.com/matteobaccan/owner/blob/master/owner/src/test/java/org/aeonbits/owner/examples/ValueHandlerExample.java).

 * **A class named in an annotation no longer has to be public.** A `Preprocessor`, a `Converter`, a
   `Tokenizer` and a decryptor may now be package-private, or a `private static` class nested inside the
   interface that names them, and their constructor may be private too:

   ```java
   public interface MyConfig extends Config {
       @DefaultValue("a")
       @PreprocessorClasses(ToUpperCase.class)
       String propA();
   }

   private static class ToUpperCase implements Preprocessor {
       @Override
       public String process(String input) { return input.toUpperCase(); }
   }
   ```

   Each of these is an implementation detail of the configuration that names it, and requiring them to be
   public meant that a library using OWNER had to widen its own published API to satisfy ours. The class was
   not being asked to be visible *to OWNER*, which would be fair: the instantiation lives in
   `org.aeonbits.owner.util`, so "the same package" was never true of anybody else's code and even a
   package-private class beside the interface was refused. What has not changed is that the class needs a
   constructor taking no arguments, which is still refused with its name in the message. Asked for in
   [#186](https://github.com/matteobaccan/owner/issues/186) in 2016. The converter also stops being built
   with `Class.newInstance()`, deprecated since Java 9, which swallowed the constructor's own exception.
 * **The hot reload interval can come from outside the interface.** `@HotReload(interval = "${ttl}")` takes
   the time between two checks as text, with a `${variable}` expanded from the properties of the
   `ConfigFactory`, the system properties and the environment — the same three, in the same order, that
   expand a `@Sources` spec. Five seconds in development and five minutes in production stop being two
   interfaces, or two builds:

   ```java
   @HotReload(interval = "${owner.reload.interval}", type = ASYNC)
   @Sources("file:/etc/myapp/myapp.properties")
   interface MyConfig extends Config, Reloadable { }
   ```

   The value carries its own unit — `500ms`, `30s`, `5m`, `PT1H30M` — so `unit` is not consulted, and a
   **bare number is refused**: the duration syntax reads one as milliseconds while `@HotReload(5)` next
   door means five *seconds*, and two neighbouring attributes cannot mean different things by the same
   digits. A value that is not a duration, a variable nobody set, and an interval that is not positive are
   all refused when the configuration object is created rather than at the first check. `value` and `unit`
   are untouched and still decide when `interval` is not written, so no existing configuration changes.
   Asked for in [#179](https://github.com/matteobaccan/owner/issues/179) in 2016, where the shape it took
   — a new attribute rather than a change to the type of `value` — was already the one Luigi Viggiano
   argued for. See [Hot reload](/owner/docs/reload/).
 * **TOML is read**, from a source whose path ends in `.toml`, and `MyConfig.toml` joins the names tried
   when a configuration declares no `@Sources`. Unlike YAML, TOML has a written specification and a
   conformance suite anyone can run, which is why it is parsed here rather than delegated the way HOCON is
   — and why the target is the whole of v1.0.0 rather than a subset we choose. `toml-test` runs in every build:
   **every one of its 499 documents that must be refused is refused**, and 204 of the 210 that must be read
   are read exactly as it expects. The six are an empty key and a dot inside a quoted key — the two places
   where TOML and this library's flattening convention disagree, which is a decision about the convention
   rather than a gap in the parser.

   TOML is the format this library's flattening convention was already shaped like: an `[[array of tables]]`
   *is* `servers[0].host`, a dotted key *is* the flattening, and a `[table]` is a prefix, so nothing had to
   be adapted on either side. A key written twice is refused, as TOML requires and as JSON already did.

   Values are kept as written, with one rule: where TOML offers **several spellings of one value** they are
   canonicalised, because otherwise they would convert to nothing. `1_000`, `0xDEADBEEF`, `0o755` and
   `0b1101` become plain decimals; `inf` and `nan` become `Infinity` and `NaN`; and the space TOML allows in
   place of a date-time's `T` becomes a `T`. Strings and ordinary decimals are untouched. The four date-time
   types need nothing registered. See [File formats](/owner/docs/file-formats/#toml).
 * **The `java.time` types are read out of the box** — `LocalDate`, `LocalTime`, `LocalDateTime`,
   `OffsetDateTime`, `Instant`, `ZoneId`, `Year` and the rest — with nothing to register. The conversion
   chain now understands two more ways of building a type from text: a public static `of(String)` and a
   public static `parse(CharSequence)`, alongside the `String` constructor and `valueOf(String)` it already
   knew. Those four are the implicit converters
   [MicroProfile Config](https://download.eclipse.org/microprofile/microprofile-config-3.0.1/apidocs/org/eclipse/microprofile/config/spi/Converter.html)
   defines, so the naming is the ecosystem's rather than ours.

   None of the `java.time` types worked before: they have no `String` constructor and no `valueOf`, so the
   chain ran out and refused them. Where MicroProfile tries the `String` constructor last we keep trying it
   first, as this library always has — changing that would silently move a type that has both from one to
   the other. When a factory exists and rejects the text, its own exception is kept as the cause, so a bad
   date says which character was unexpected instead of only *cannot convert*. See
   [Type conversion](/owner/docs/type-conversion/#types-built-by-a-static-factory).
 * **HOCON is read**, from a source whose path ends in `.conf`, and `MyConfig.conf` joins the names tried
   when a configuration declares no `@Sources`. The document becomes the same keys every other format
   flattens to, so nested interfaces, indexed lists and maps of sections read it unchanged — substitutions,
   object merging and `include` included, those being the reference implementation's to perform.

   **It is the one format this project does not parse itself, and the reason is that it already has a
   parser.** HOCON's specification *is* an implementation, and the value of the format is reading the
   `application.conf` files that already exist; a hand-written subset would refuse substitutions, merging
   and `include`, which is to say it would be JSON with comments. Worse, OWNER already reads `${...}` with
   different semantics, so an approximation would not fail on the files it could not handle — it would read
   them and quietly mean something else.

   It costs nothing to anyone who does not use it. `com.typesafe:config` is an **optional** dependency of
   `owner-extras`: it is not transitive, this project does not ship it, and you add it yourself. Nothing in
   the loader refers to it, so the loader is discovered and created like any other on a classpath without
   it, and only reading a `.conf` fails — naming the source and the artifact to add. See
   [File formats](/owner/docs/file-formats/#hocon). Closes
   [#240](https://github.com/matteobaccan/owner/issues/240).
 * **The ZooKeeper loader no longer has to be registered.** `ZooKeeperLoader` is now found on the classpath
   like every other loader, so `@Sources("zookeeper://…")` works with `ConfigFactory.create` and there is no
   `registerLoader` call and no factory of your own to write. Code that still registers it keeps working.
   [Apache Curator][curator-news] is still an optional dependency and is still yours to declare — what
   changed is only that declaring it is now the whole of the setup.

   Being discovered means the loader is created in every application carrying `owner-extras`, most of which
   will not have Curator, so nothing in it refers to Curator any more: everything that does moved to a
   separate class reached only when a `zookeeper:` source is read. A configuration reading any other source
   is unaffected, and the loader contributes no file name to the ones tried when a configuration declares no
   `@Sources`. Reading a `zookeeper:` source without Curator now names the source and the artifact to add,
   where it used to be a `NoClassDefFoundError`.

  [curator-news]: https://curator.apache.org

 * `default` methods in config interfaces work out of the box with the core `owner` artifact: no extra
   dependency is needed anymore (see Removals above).
 * New `Accessible.store(Writer, String)` overload, mirroring `Properties.store(Writer, String)`; the old
   javadoc note about it being unavailable dated back to the JDK 1.5 era.
 * The single-method SPI interfaces (`Converter`, `Preprocessor`, `ReloadListener`) are now marked
   `@FunctionalInterface`: converters, preprocessors and reload listeners can officially be written as lambdas.
 * A method can return an **`Optional`** of any supported type, which comes back empty when the property is
   defined nowhere and has no default, instead of returning `null`: `Optional<Integer> port()` reads the same
   value `Integer port()` does, and says in the signature that the caller has to deal with its absence. The
   wrapper only describes the absence, so everything else applies unchanged — `@Key`, `@Prefix`, the
   preprocessors, the variable expansion, the decryption, the tokenization of `Optional<List<String>>` — and a
   value that is *wrong* rather than missing keeps failing, so a typo does not silently become an empty
   `Optional`. An empty value stays a value, as it does everywhere else. `@Mandatory` and `Optional` written on
   the same method contradict each other and are reported when the Config object is created, while a
   `@Mandatory` written on the interface leaves an `Optional` method alone, being the exception it declares.
   See the [documentation](/owner/docs/type-conversion/#optional-values).
 * New `@Sensitive` annotation: the value of the annotated property (or of every property of the annotated
   interface) is printed as `********` by `Accessible.list()` and by `toString()`. A password written in clear
   in a properties file is a value like any other to this library, and a `cfg.list(System.out)` added while
   debugging and then forgotten is how one ends up in a log. Only the output meant to be read by a human is
   masked: the method itself, `getProperty`, `fill`, `store`, `storeToXML` and the JMX attributes keep
   returning the real value, since those are how a configuration is read and written back and masking them
   would replace the password with the mask in the file at the next save. Masking is not encryption — see
   `@EncryptedValue` for that, whose values are already printed as ciphertext — it only keeps a value from
   being printed by accident. The keys to mask are resolved when the Config object is created, so a
   parametrized property, whose key depends on the arguments, is left alone. See the
   [documentation](/owner/docs/debugging/#keeping-a-property-out-of-the-output).
 * New `@Mandatory` annotation: mark a property (or a whole interface) as required, and get a
   `MissingMandatoryPropertyException` listing all the unresolvable keys when the Config is created, as well as
   on access if a mandatory property disappears later (e.g. after a hot reload). See the
   [documentation](/owner/docs/usage/#mandatory-properties). Originally proposed by Alexander Poulikakos in
   [#216](https://github.com/matteobaccan/owner/pull/216).
 * New `@Prefix` annotation: declare the common prefix of a group of keys once, on the interface, instead of
   repeating it in the `@Key` of every method. `@Prefix("server.")` makes `String hostname()` resolve to
   `server.hostname`, and it is prepended to the `@Key` value as well. The prefix belongs to the interface that
   *declares* the method, so it never leaks onto the methods a sub-interface inherits, at any depth of the
   hierarchy; it is expanded like the rest of the key, so `@Prefix("servers.${env}.")` selects a section at
   runtime; and it can be switched off per method or per interface with
   `@DisableFeature(PREFIX)`, a new value of `DisableableFeature`. Nothing changes for existing configurations:
   an interface without `@Prefix` resolves its keys exactly as before. See the
   [documentation](/owner/docs/key-prefix/). Originally proposed by Gmugra in
   [#273](https://github.com/matteobaccan/owner/pull/273).
 * A prefix can also be configured **on the factory**, for the interfaces that do not declare a `@Prefix` of
   their own: `owner.key.prefix` prepends a literal to every key, and `owner.key.prefix.from.package` derives
   it from the package of the interface declaring the method, so `com.example.ServerConfig.port()` reads
   `com.example.port`. Being derived rather than typed, the second form follows the class when it is moved to
   another package — and it extends to the keys the convention OWNER already applies to the name of the
   default properties file. It is set through the factory properties, so no method is added to the `Factory`
   interface, and it belongs to the factory rather than to the JVM: two factories do not interfere, and a
   library can create its own and be unaffected by what the application does. `@Prefix` wins over it,
   `@DisableFeature(PREFIX)` switches off both, and the prefix is read when the Config object is created and
   kept for its whole life — so reconfiguring the factory cannot rename the keys of what already exists, a
   reload resolves the same keys, and the mapping survives serialization. See the
   [documentation](/owner/docs/key-prefix/). Answers the request in
   [#259](https://github.com/matteobaccan/owner/issues/259).
 * New `@DefaultValue(useOnEmpty = true)` flag: a property that is present but **empty** is normally a value
   like any other — `port=` is not a missing property, and on a numeric type it fails the conversion — which is
   the distinction MicroProfile Config, Quarkus and Spring Boot all draw, and what keeps a typo like
   `port=8O80`, written with the letter O, from silently becoming the default. The flag covers the one case
   where the distinction gets in the way: a value left empty by a template, as in `port=${PORT}` with `PORT`
   unset. With it, an empty value — whitespace included, and after the variables are expanded — falls back on
   the default as if the property were missing, while a value that is *wrong* rather than empty keeps failing.
   It is opt-in and per method, so nothing changes for existing configurations. See the
   [documentation](/owner/docs/usage/#a-property-that-is-set-but-empty) and the
   [table of what an empty value does on each type](/owner/docs/type-conversion/). Partially answers
   [#191](https://github.com/matteobaccan/owner/issues/191).
 * `ByteSize` implements `Comparable`, ordering sizes by the amount of data they represent whatever unit
   they are written in, so `1 MB` sorts before `1 MiB`. The ordering is consistent with equality, which is
   what makes a `TreeSet` of byte sizes agree with a `HashSet` on which of them are duplicates.
 * New `ByteSize.in(ByteSizeStandard)`: the same size written in the unit of that family that suits it — the
   largest one in which the value does not fall below one — so 2048576 bytes read as `2.048576 MB` in SI and
   as `1.95367431640625 MiB` in IEC. Where `convertTo` has to be told the unit, this needs only the family to
   choose from, which is what one usually has when a configured size is to be logged or shown. The answer is
   canonical, depending on the size and never on the unit it happened to be written in, and exact, every
   factor being a power of 1000 or of 1024.
 * `ByteSize` is `Serializable`, which a `Config` object already was. The unit is preserved along with the
   value, so a size written as `1 MB` comes back reading as `1 MB` and not as `1000000 B`, and the stream is
   validated on the way in: deserialization runs no constructor, so a stream that does not describe a byte
   size is refused with an `InvalidObjectException` instead of producing an object that fails later.
 * [#320](https://github.com/matteobaccan/owner/pull/320): `EnumSet` and `Set<Enum>` are now supported by type
   conversion (thanks to @dexman545).
 * **`java.time.Duration` is converted out of the box**, like a `File` or a `URL`, instead of asking for
   `@ConverterClass(DurationConverter.class)` on every method that returns one: a timeout is the commonest
   typed setting after a number and a string, and the JDK has a type for it. `10 s`, `500 ms`, `1 d` and the
   ISO-8601 form `PT15M` are all read, in collections, arrays and `Optional` like any other type.
   **The time unit is required on this path**: `timeout=30` is refused with a message saying what to write,
   because a bare number would be read as milliseconds and whoever writes 30 means seconds far more often
   than that. The converter named explicitly keeps its previous behaviour, bare number included, so no
   configuration written before this release changes meaning; a converter registered for `Duration`, or named
   with `@ConverterClass`, still takes precedence over the automatic conversion. See the
   [documentation](/owner/docs/type-conversion/#duration).
 * [#187](https://github.com/matteobaccan/owner/issues/187): `java.nio.file.Path` is converted, with a leading
   `~` expanded to the user home exactly as `java.io.File` already was — the two ways of naming a path no
   longer disagree. Arrays and collections of `Path` follow. The reporter asked in 2016 whether this belonged
   in a separate module for Java 7; the question no longer arises, since Java 8 is the minimum runtime.
 * Variables can now carry a default value: `${db.host:localhost}` resolves to `localhost` when `db.host` is
   defined nowhere, instead of to the empty string. Everything after the first colon is the default, colons
   included, so URLs, Windows paths and `host:port` pairs survive intact. Existing configurations are unaffected:
   the text inside `${...}` is looked up as a property key in its entirety first, and only if there is no such
   property is the colon read as a separator — so a key like `jdbc:url` keeps resolving as before. The one
   behaviour that changes is a variable that used to resolve to nothing: `${a:b}`, with neither `a:b` nor `a`
   defined, yielded the empty string up to 1.0.12 and yields `b` from now on. See the
   [documentation](/owner/docs/variables-expansion/). Proposed by Ilya Koshaleu in
   [#256](https://github.com/matteobaccan/owner/pull/256).
 * Variables can now be nested: the expression inside `${...}` is expanded first, and the result is then looked
   up as a key, so `${servers.${env}.url}` reads the key named by the value of `env`. It works at any depth, in
   the `@Key`, in a property value and in the `@Sources` specification, and it combines with the default values
   above — `${servers.${env}.url:http://localhost}`. This is what makes a key depend on a key that itself
   depends on another one, the case that produced silently wrong lookups before. See the
   [documentation](/owner/docs/variables-expansion/#nested-variables). Proposed by Tomek in
   [#326](https://github.com/matteobaccan/owner/pull/326).

   **Compatibility.** This is the one change in this release that touches an existing parsing rule: up to 1.0.12
   a `${` was closed by the first `}` that followed it, now it is closed by the one that matches it. Only the
   `${` sequence opens a nesting level, so a lone brace inside an expression remains ordinary text and a key such
   as `a{b` keeps resolving; `${}` and an unbalanced `${` are left alone as before. A configuration that uses
   plain variables is therefore unaffected. Should some unforeseen combination of braces read differently, the
   whole behaviour can be switched off for the JVM with `-Downer.nested.variable.expansion=false`, which runs the
   substitution of the previous releases unchanged.
 * A `Map` return type now reads the **group of properties below the key of the method**, closing a request open
   since 2013 ([#41](https://github.com/matteobaccan/owner/issues/41)):

   ```properties
   something.foo=1
   something.bar=2
   ```

   ```java
   Map<String, Integer> something();     // {foo=1, bar=2}
   ```

   Both sides of the entry go through the regular type conversion, so `Map<Integer, String>` and
   `Map<Colour, String>` work as well; the group is named like any other key, so `@Key`, `@Prefix` and variable
   expansion all apply — `@Key("servers.${env}")` picks the section at runtime. A name with further dots keeps
   them, so `something.a.b` becomes the entry `a.b`; no match gives an empty map rather than `null`; the
   declared map type is honoured — a class is instantiated as itself, and an interface is given an
   implementation that satisfies it: `LinkedHashMap` for `Map`, `TreeMap` for `SortedMap` and
   `NavigableMap`, `ConcurrentHashMap` for `ConcurrentMap`, `ConcurrentSkipListMap` for
   `ConcurrentNavigableMap`, and an `EnumMap` over the enum it declares, which is built from the key type
   rather than refused for want of a no-argument constructor. A type nothing can satisfy is named in the
   message instead of failing as a `ClassCastException` on the way out; and `@DefaultValue` is refused on
   such a method, since a default belongs to the individual properties. A `@ConverterClass` still takes
   precedence, which is how the other shape of the request — one property whose value holds the pairs, as asked
   in [#286](https://github.com/matteobaccan/owner/issues/286) — keeps working. Nothing can break: a `Map`
   return type used to throw on every access, so no working configuration relied on it. See the
   [documentation](/owner/docs/type-conversion/).
 * A list can be written **one element per key**, closing a request open since 2013
   ([#48](https://github.com/matteobaccan/owner/issues/48)):

   ```properties
   servers[0]=alpha
   servers[1]=beta
   ```

   ```java
   List<String> servers();               // [alpha, beta]
   ```

   for every array and collection type, `Optional` included. The point of it is that an element written this
   way is one element whatever it contains, so `servers[0]=a,b` is a single value with a comma in it, which a
   comma-separated list cannot express at all — and it is how a list out of a JSON or a YAML source will
   survive being flattened into properties. The separator does not apply to an indexed element, there being
   nothing to split. Square brackets rather than `servers.0` because the dot already belongs to the `Map`
   grouping above, which would otherwise make one layout of keys mean two things. An indexed key wins over a
   single value and over a `@DefaultValue`, and the elements must be numbered from zero without gaps: a gap
   is refused rather than closed up, since a list quietly shorter than the file describes, with everything
   after the gap moved, is not something the caller can notice. Nothing can break, `servers[0]` having been a
   property nothing read. See the [documentation](/owner/docs/type-conversion/).
 * **YAML is read**, by a parser of ours, in the same `owner-formats` artifact — and it is **a subset**,
   which is said here rather than discovered later:

   ```yaml
   server:
     host: localhost
   servers:
     - host: alpha
     - host: beta
   ports: [80, 443]
   ```

   Read: block mappings and sequences nested by indentation, a mapping opened on the same line as its dash,
   plain and quoted scalars, the block scalars `|` and `>` with their chomping indicators, flow collections
   — so a JSON document is read too, being valid YAML — comments, and a leading `---`.

   Refused by name, with the line they are on: anchors, aliases and merge keys; tags; complex keys; a value
   continued on the next line without `|` or `>`; a second document in the same file; and a tab used as
   indentation. None of them is guessed at or quietly ignored, because a parser that half-understood one
   would change the meaning of a file rather than decline to read it.

   **Types are not guessed**, and that is what makes the parser possible at all. A scalar is kept as
   written and the method that reads it decides what it means, so `enabled: yes` is the text `yes` and
   `country: no` is the string `no` — the "Norway problem" simply does not arise. Implicit type resolution
   is most of what a complete YAML implementation does, and none of it is needed when the interface is
   where the types are declared. Write `true` when a boolean is meant. Issues
   [#14](https://github.com/matteobaccan/owner/issues/14) and
   [#65](https://github.com/matteobaccan/owner/issues/65).
 * **JSON is read**, by a parser of ours, in a new artifact:

   ```xml
   <dependency>
       <groupId>org.aeonbits.owner</groupId>
       <artifactId>owner-formats</artifactId>
   </dependency>
   ```

   Adding it is all there is to do — the loader declares itself, so a `.json` source is read as soon as the
   artifact is on the class path. The document's shape becomes the keys, `server.host` and
   `servers[0].host`, which is the flattening every loader here already uses: a JSON document is therefore
   read by the same nested interfaces, indexed lists and grouped maps as anything else, and nothing about
   the mapping is specific to JSON.

   **RFC 8259 and no more**: no comments, no trailing commas, no unquoted names, no single quotes, no
   leading zeros. Those are JSON5 and JavaScript, and a file we accepted and other tools refused would be
   the worst of both. Every complaint names the line and the column, and a value is kept exactly as
   written — `1e3` stays `1e3`, and a long past 2^53 keeps its last digits.

   Three things the specification leaves open, decided and written down: a `null` writes no key at all,
   `Properties` being unable to hold one; an empty array writes an empty value, which is already read as an
   empty collection; and a repeated name is refused, because JSON has a real way to write a list and a
   repetition is therefore a mistake rather than the shorthand it is in INI and XML.

   **Why a separate artifact**: the core ships the formats the JDK can already parse — `Properties` for
   properties, SAX for XML, and `.env` and INI are line-by-line variations on the first. A parser we write
   is code that chews untrusted input, and a defect in one would be a security release for everybody,
   including the majority who never load that format. It brings no dependency of its own. Issue
   [#240](https://github.com/matteobaccan/owner/issues/240).
 * **Nested configuration interfaces.** A method returning another interface that extends `Config` reads the
   section of the configuration below its own key:

   ```properties
   server.host=localhost
   server.port=8080
   ```

   ```java
   ServerConfig server();                // server.host, server.port
   ```

   The accessor names the section rather than the type it returns, so `@Key` renames it and two methods can
   return the same interface without colliding. The nested object **loads nothing of its own**: it is a view
   over the properties its parent resolved, sharing one set of `@Sources`, one reload, one set of listeners
   and one mutable state for the whole tree. The objects are built when the configuration is created, so a
   `@Mandatory` property one level down is checked then like any other and a cycle in the types is refused
   there rather than at the first call. A `@Prefix` on a nested interface **composes** with the path it hangs
   from — unlike the prefix configured on a factory, which `@Prefix` overrides — because the path says where
   the object was hung and the annotation says how it names its own keys.

   Sections can be counted or named. `List<ServerConfig>` reads `servers[0].host`, `servers[1].host`, by the
   rules of any indexed list, which is exactly the shape a tree-structured source flattens to: an XML
   document with a repeated element is now read by an interface holding a list. A type holding a `List` of
   itself is a tree and is allowed, where a type holding itself is refused — the keys say how deep it goes.
   `Map<String, ServerConfig>` reads `servers.alpha.host` and `servers.beta.host`, the name of each section
   becoming the key of the entry, and `@Key("servers.%s") ServerConfig server(String name)` asks for one by
   name: together they answer the long-standing question of objects whose names are only known at run time.

   An `Optional` section is present when anything at all was written below its path. A `@DefaultValue`
   declared inside the nested interface is one such thing, so it makes the section permanently present: the
   two say the opposite of each other and the default wins. For the same reason `@Mandatory` written on the
   accessor of a section is refused when the configuration is created, since the check could never fail;
   `@Mandatory` on the properties inside is the one that means something. Nothing can break: a method
   returning an interface extending `Config` had no meaning before. See the
   [documentation](/owner/docs/nested-configuration/).
 * **The library says which key each method reads.** A wrong prefix makes every property vanish at once
   with nothing to show for it — no error, every method answering `null` or its default, and a file full of
   values that look right. At `FINE` every method now reports the key it resolves to, nested sections
   walked with it, and a key that is not yet final says which kind it is: one whose prefix is disabled, one
   whose arguments are formatted in at each call, one still holding variables. At `CONFIG`, one line names
   the prefix configured on the factory, which is singled out because it is the only prefix written in no
   source file at all. See the [documentation](/owner/docs/debugging/#which-key-is-my-method-reading).
 * **A source that hot reload cannot watch says so.** Watching means asking something whether it has
   changed, and only a file and `system:properties` can answer: a resource inside a jar served over the
   network, an `http:` source, `system:env` cannot. Those were dropped from the watch list in silence, which
   is where "I changed the file and nothing happened" comes from. It is now a `WARNING` naming them, once,
   when the configuration is created — different from an absent source, which stays silent, because here
   somebody wrote `@HotReload` and for that source it will never fire. What *is* being watched, of which
   kind and how often, is written at `CONFIG` beside it.
 * **A source that was named and did not arrive is no longer passed over in silence.** Both load policies
   ended in `catch (IOException) { ignore() }`, with a comment admitting it covered two different things: a
   file legitimately absent, which is how a fallback chain works, and a file that is there and cannot be
   read. The second produced a configuration full of defaults and said nothing about it. Now an absent
   source is still silent — with `FIRST` every miss but the last is the feature working, and a
   configuration with no `@Sources` probes four names per interface — while a source that is there and
   refuses is a `WARNING`, and **declared sources of which not one could be read** are a `WARNING` of their
   own, that being what a mistyped path looks like. Each is said once, not at every reload.

   Which of the two a failure was is deliberately not read off the exception: `FileInputStream` throws
   `FileNotFoundException` for a file that is missing, for a directory named where a file was meant, and
   for one it may not open — the three cases the rule exists to separate. For a file the filesystem is
   asked; only a source that is not a file falls back on the exception.

   And a source can now say that it has to be there: `@Sources("file:/etc/app.properties#required=true")`
   refuses the configuration when that one is missing or unreadable, including a `classpath:` resource that
   resolves to nothing — the case that never reaches a loader and would have been the one place the promise
   was dropped. It is Spring's `optional:` the other way up, which is what keeps every fallback written so
   far working unchanged. Unlike a dialect, `required` is read by the library rather than by a loader, so
   no loader has to declare it. Issue [#170](https://github.com/matteobaccan/owner/issues/170) asked for
   the visible half of this.
 * **A configuration can say where each property came from.** A new interface of the `Accessible` family,
   `Traceable`, answers the question the merged properties cannot:

   ```java
   @LoadPolicy(LoadType.MERGE)
   @Sources({"system:env", "file:config/app.properties"})
   interface MyConfig extends Config, Traceable { ... }

   cfg.originOf("port");            // file:config/app.properties
   cfg.originOf("port").kind();     // SOURCE, IMPORT, DEFAULT_VALUE or RUNTIME
   ```

   Merging is exactly what destroys this: after it, a value read from a file is the same property as one
   that came from the environment or from a `@DefaultValue`, and nothing in the map says which. So the
   origin is recorded while each source is read, and under `MERGE` the one recorded is the source whose
   value survived — the first declared. Under `FIRST` the sources after the one that answered are never
   read, and nothing is attributed to them. The origins follow the properties afterwards: a reload works
   them out again, `setProperty` makes a property one that was written at run time, and removing a property
   removes its origin with it.

   It was asked for by somebody whose `store()` wrote the whole environment back into the configuration
   file, and whose workaround — removing the environment variables by name before saving — failed for a
   property that was in both. Filtering by origin is the answer, and the recipe is in the
   [documentation](/owner/docs/accessible-mutable/). **A source never carries its credentials** into an
   origin: `https://user:secret@config/app.properties` appears as `https://***@config/app.properties`,
   the same masking the log lines and the exception messages already use. Issue
   [#277](https://github.com/matteobaccan/owner/issues/277).
 * **`.env` files are read**, which is how container tooling carries configuration into a process —
   `docker run --env-file`, `env_file` in Compose, `envFrom` in Kubernetes, the secrets of a CI pipeline. Any
   source whose path ends in `.env` is read this way, values go through the usual type conversion, and the
   parser is ours: the core still has no dependencies.

   There is no `.env` standard, and the tools that read one disagree on the point that bites hardest, which
   is quoting: given `NAME="Matteo"`, `docker run --env-file` gives you the quotes and the `dotenv` family
   does not — and Docker Compose does not agree with `docker run`. So OWNER does not implement "the .env
   format", it implements a **dialect**, and there are three presets — `docker`, `dotenv`, `compose` — plus
   seven rules that can each be set on their own, for the tools that match none of them. **`docker` is the
   default**, because it does nothing at all to a value and a value that arrives with its quotes still
   attached is noticed at once, where quotes silently removed are not. A file that looks quoted under a
   dialect that keeps quotes draws one `WARNING`. A `.env` is never looked for on its own: it is not named
   after the configuration interface, so it is always named explicitly, and configurations that do not use
   one pay no extra lookup. See the [documentation](/owner/docs/file-formats/#env).
 * **INI files are read** — sections in square brackets and `key = value` below them, which is the shape of
   `~/.aws/credentials`, `~/.gitconfig`, a systemd unit and a good deal of what is already on a machine. A
   section becomes the prefix of the keys under it, needing no convention of its own since the dot is
   already how OWNER nests, and `.ini` and `.cfg` are both recognised and both looked for beside the
   configuration class.

   **A repeated key is a list** — `servers.host[0]`, `servers.host[1]` — exactly as repeated XML elements
   are numbered, a key occurring once keeping its plain key. This is the point the tools disagree on most:
   Python's `configparser` refuses the file, git and systemd and Commons Configuration read a list, and the
   AWS SDK for Java keeps the last. A list is the answer because it is the one this release already gives
   to a repeated XML element, and reading the same shape two ways would be the surprise; the other three
   are available as options.

   There is no INI standard, so as with `.env` the rules are a **dialect**: `ini` by default — the
   conservative common denominator every surveyed tool agrees with — plus `git`, which reads a subsection,
   so `[remote "origin"]` holding a `url` becomes `remote.origin.url`, the very key `git config` prints;
   and `python`, which folds keys, accepts `:`, refuses a duplicate, continues a value by indentation and
   lets every section inherit `[DEFAULT]`. Eleven rules can be set one at a time over any of them.

   One thing the `python` dialect **refuses** rather than half-honours: `ConfigParser` interpolates
   `%(name)s` by default and OWNER never will, expanding `${…}` itself after loading and across every
   source. A value holding `%(…)s` read under that dialect is an error naming the key and pointing at
   `${…}`, because handing back the literal would make the same file mean one thing to Python and another
   here, quietly. See the [documentation](/owner/docs/file-formats/#ini).
 * **A source can carry options, written in its fragment.** `@Sources("file:.env#dialect=dotenv")` sets the
   dialect for that file alone, several options separated by `&`. The rule is the same for every loader and
   every scheme: **the query belongs to the protocol and the fragment belongs to OWNER**. A query is never
   touched, so `https://config/app.env?token=abc#dialect=dotenv` sends the token to the server and keeps the
   dialect; and the fragment is the only place the options can be written at all for a resource inside a
   jar, whose URI has no query to speak of. An option a loader does not recognise is **refused, not
   ignored**, and the message names the option, the source and what would have been accepted — a misspelt
   option that passes in silence is a configuration that is wrong and says nothing. This works on a
   `classpath:` source as well as on a file. See the [documentation](/owner/docs/loading-strategies/#options-on-a-source).
 * **A loader can be found on the classpath** instead of being registered by hand: declare it in
   `META-INF/services/org.aeonbits.owner.loaders.Loader` and it is picked up when a factory is created,
   which is what a jar shipping a format is for. Being found enables it — it answers for its formats at
   once, and its default file names join the ones looked for when an interface declares no `@Sources`, as
   Spring Boot, MicroProfile and Gestalt all do with theirs.

   Where it lands is deliberately not the same in both directions. A found loader comes **before** the
   built-in ones when a source is matched, or `PropertiesLoader` — which accepts every URL it can resolve —
   would take its files; and **last** among the default file names, so that adding a jar to a build cannot
   make a stray `MyConfig.yaml` start beating the `MyConfig.properties` an application already reads.
   Registering a loader by hand keeps the front in both, that being something the application said on
   purpose.

   The searching is done by the thread's context class loader, falling back on the one that loaded OWNER.
   That is right in an ordinary application and in an application server, and it is not right on a pooled
   thread carrying somebody else's context, nor under OSGi; in those, `registerLoader` still works and
   depends on nothing. Since a loader that is not found does not fail — its file falls through to the
   properties loader and is read as properties, quietly — OWNER now reports what it found at the `CONFIG`
   logging level, including when it found nothing. `org.aeonbits.owner.level = CONFIG` is the switch, and it
   is silent unless you turn it on. See the [documentation](/owner/docs/loading-strategies/#letting-it-be-found-instead).
 * **A format may go by more than one name.** `Loader.defaultSpecsFor(String)` returns every default file
   name a loader offers, for the formats spelled two ways — `.yaml` and `.yml`, `.ini` and `.cfg`. It is a
   `default` method, and so is `defaultSpecFor`, which now returns `null` by default: declining to be looked
   for is a choice a loader is allowed to make, and `SystemLoader` and `DotEnvLoader` both make it. Nothing
   that implements `Loader` today has to change, or even to be recompiled.
 * A circular variable reference is now reported instead of being followed. A property whose value leads back to
   the property itself cannot be resolved, and an `IllegalArgumentException` names the chain that closes the
   loop — `Circular variable reference: ${a} -> ${b} -> ${a}` — where up to 1.0.12 the same configuration
   exhausted the stack with a `StackOverflowError`. A default value does not rescue it: `db.host=${db.host:localhost}`
   is the shell idiom for "keep it if set, otherwise use this", but it relies on the substitution happening once
   at assignment, while OWNER expands variables when a property is read, and inside values. That line therefore
   describes a loop rather than a fallback, and it is reported as one — what was meant is `db.host=localhost`.
   See the [documentation](/owner/docs/variables-expansion/#circular-references).
 * New `@CollectionConverterClass` annotation: hands the raw property value to a single converter instead of
   splitting it first and converting one element at a time, as `@ConverterClass` does. It is the way to opt out of
   the built-in tokenization — for a property holding a single JSON document, say — or to return a collection type
   OWNER cannot instantiate itself, such as an immutable one or an implementation without a no-argument
   constructor. Using it on a method that does not return a `Collection` reports which method is at fault instead
   of failing later with a `ClassCastException`. See the
   [documentation](/owner/docs/type-conversion/). Contributed by Adam Huječek in
   [#248](https://github.com/matteobaccan/owner/pull/248), closing
   [#206](https://github.com/matteobaccan/owner/issues/206).
 * Security hardening of the `XMLLoader` against XXE attacks: external DTDs and entities are neutralized, secure
   processing limits entity expansion; the standard Java properties XML format keeps working as before.
 * [#325](https://github.com/matteobaccan/owner/pull/325): temporary files are now created with owner-only
   permissions via `Files.createTempFile` (thanks to @JLLeitschuh); when storing a Config to an existing file,
   the file permissions are preserved.
 * The jars declare an `Automatic-Module-Name` — `org.aeonbits.owner` and `org.aeonbits.owner.extras` — so a
   `requires` written against them keeps resolving across releases, instead of depending on a module name
   derived from the file name and therefore from the version. The two artifacts no longer share a package
   either (see the `ZooKeeperLoader` move under Removals), so they can both sit on the module path.
 * Bytecode is still compatible with Java 8 at runtime, while the project is built with modern JDKs
   (`compiler-release=8`); a JDK 11 or superior is required to build from sources.
 * Javadoc completed and improved across the codebase.
 * Dependencies updated across the board, including security-driven pins: JUnit 4.13.2, Mockito 5.x, SLF4J 2.x,
   commons-codec 1.22, Curator with ZooKeeper forced to 3.9.5 and Netty aligned via BOM to address published
   vulnerabilities.

#### Code quality and test coverage
A large part of the work that went into 2.0.0 is not visible in the API. The objective was to raise the test
coverage as far as it reasonably goes and to leave no warning unexamined, so that future changes start from a
codebase that says what it does.

 * **Test coverage extended**, with the crypto, loaders, util and ConfigCache packages at or near 100%. The
   tests were written to pin down actual behaviour, not to move a percentage: several of them document
   decisions that were previously only implicit in the code.
 * **Every warning triaged, one by one.** The project is analysed on each push by
   [CodeQL](https://github.com/matteobaccan/owner/security/code-scanning) with the `security-and-quality`
   query pack, by [SonarCloud](https://sonarcloud.io/project/overview?id=matteobaccan_owner), and by the
   inspections built into the IDEs used for development. Every finding was either fixed, or dismissed with a
   written technical justification explaining why the construct is correct as it stands — an analyser being
   wrong is a legitimate outcome, an unread warning is not.
 * **The exercise was not cosmetic.** Chasing warnings that looked like style issues surfaced genuine defects
   that had gone unnoticed for years. The array and collection conversion bug listed under "Bugs fixes" was
   found exactly this way: a CodeQL note about an inner class that could be made `static` turned out to be
   hiding a test that passed for the wrong reason, and behind it a real failure affecting any user converting
   a list of custom objects.
 * **Tests that passed for the wrong reason were corrected.** A green test suite is only meaningful if each
   test fails when the behaviour it describes breaks. Where a test was found to be satisfied by an accident of
   its fixtures rather than by the behaviour under test, the fixture was fixed and the assertion re-verified.

The intention is to keep this state: warnings are not allowed to accumulate between releases, and a finding is
closed only when it has been understood.

#### Site Enhancements
 * New documentation for the [Preprocessors](/owner/docs/preprocessors/) feature (available since 1.0.9,
   never documented).
 * New documentation for the [JMX support](/owner/docs/jmx/) (available since 1.0.10, never documented).
 * New chapter on the [Key prefix](/owner/docs/key-prefix/) feature, and the list of the
   [disableable features](/owner/docs/disabling-features/) is now spelled out, with the version each
   one appeared in.
 * New sections on [nested variables](/owner/docs/variables-expansion/#nested-variables) and on
   [how to switch them off](/owner/docs/variables-expansion/#disabling-nested-variables) in Variables expansion.
 * New section on [Sources and interface inheritance](/owner/docs/loading-strategies/#sources-and-interface-inheritance), writing down
   what was until now only implicit in the code: `@Sources` accumulates across the interfaces a mapping interface
   extends — by design, since it describes a set and not a single setting — while `@LoadPolicy` and `@HotReload`
   take the first annotation found. The section also documents the limitation the three of them share, that only
   the direct super-interfaces are read, so an annotation two levels up is silently ignored. The behaviour is
   unchanged in 2.0.0 and is now covered by tests, so that changing it will be a deliberate step.
 * New section on [Mandatory properties](/owner/docs/usage/#mandatory-properties) in Basic usage.
 * [Type conversion](/owner/docs/type-conversion/) no longer stops at "`Map` is not supported", a
   sentence that read as "cannot be done" and had been sending people away since at least
   [#41](https://github.com/matteobaccan/owner/issues/41). The chapter now describes the grouping above, and
   keeps the `@ConverterClass` recipe for the case where a single property value holds the pairs — arrays of
   maps included.
 * New section on [overriding a property in a sub-interface](/owner/docs/usage/#overriding-a-property-in-a-sub-interface), answering
   [#421](https://github.com/matteobaccan/owner/issues/421): an override redirects a property instead of adding
   one, since there is one method and therefore one key. Both of the things usually wanted there — keeping the
   base key readable, and making the concrete setting fall back to the base one — are shown written down
   explicitly, the second one as a three-level chain built with a variable.
 * [Crypto support](/owner/docs/crypto/) is no longer labelled as experimental: the `@EncryptedValue` and
   `@DecryptorClass` annotations have shipped unchanged since 1.0.10 and are part of the stable API.
 * Documentation refreshed to the current state of the project: installation instructions, build requirements,
   FAQ (encrypted properties are supported since 1.0.10, not 1.0.12 as previously stated;
   [#229](https://github.com/matteobaccan/owner/issues/229)), links and navigation updated to the maintained
   repository and to the current CI services.

#### Infrastructure
 * Continuous integration migrated from Travis CI to
   [GitHub Actions](https://github.com/matteobaccan/owner/actions): every push and pull request is built on all
   the supported LTS JDKs (11, 17, 21 and 25) with Maven caching.
 * Code quality and coverage tracked on [SonarCloud](https://sonarcloud.io/project/overview?id=matteobaccan_owner);
   security scanning via CodeQL and Dependabot; dependencies kept current by Renovate.
 * Maven wrapper added; Maven Enforcer requires Maven 3.6.3+; Travis, Coveralls and WhiteSource/Mend leftovers
   removed.
 * The BSD license header is now enforced on every Java source file by the
   [license-maven-plugin](https://oss.carbou.me/license-maven-plugin/): `mvn license:format` adds or fixes it,
   and `mvn license:check` — bound to the `verify` phase, so it also runs in CI — fails the build when a file is
   missing it. Nineteen files had drifted without one over the years, and the copyright line existed in three
   different variants; both are now uniform. The Maven wrapper sources are excluded, as they ship under Apache 2.0.

#### Bugs fixes
 * **A source with no scheme made the ZooKeeper loader throw.** `ZooKeeperLoader.accept` compared the URI's
   scheme against its own without allowing for a URI that has none, so it raised a `NullPointerException`
   rather than answering. Every registered loader is asked about every source, so this bit anyone following
   the ZooKeeper documentation who also had a source written without a scheme —
   `@Sources("myconfig.properties")` — or a blank `file:`, which the library turns into an empty URI on
   purpose, that being what a source path built from an unset environment variable comes to. What such a
   source does is unchanged: no loader accepts it, so the library still says it cannot resolve one.
 * **An XML document that broke its own DTD was read past it.** `XMLLoader` validates — it must, the Java
   XML properties format being defined by a DTD — and a validity error was refused for that format and
   swallowed for every other. So a document of your own carrying a `<!DOCTYPE>` and then contradicting it
   came back complete, the forbidden part included, with nothing said. It was never a truncated document:
   a validity error is recoverable, the parse runs to the end, and what the caller got was *more* than the
   grammar allows rather than less.

   The swallowing was not gratuitous, which is why it survived so long: a validating parser reports a
   validity error for **every** document that declares no grammar at all — *no grammar found* — and
   ignoring that one is what makes reading ordinary XML possible. The test now is whether the document
   declares a grammar, not whose grammar it is. One that declares none is read as it is, and so is one
   naming an **external** DTD, which the XXE hardening neutralizes: the grammar never arrives, and a
   document cannot be held to a rule that was refused a reading.

   Where this refuses a file that 1.0.12 read, `#validate=false` on the source reads it again — for a
   grammar of your own and for the Java properties one alike. See the
   [documentation](/owner/docs/file-formats/#a-document-is-held-to-the-grammar-it-declares).
 * An XML source carrying a query string was not recognised as XML. `XMLLoader` decided from
   `URL.getFile()`, which by contract is the path **plus the query**, so
   `@Sources("https://config/app.xml?v=2")` failed its own test, fell through to `PropertiesLoader` — which
   accepts everything the others turn down — and was read as a properties file. There was no error and no
   warning: the configuration came back holding nothing but its defaults. The format is now decided from the
   path alone, so a query changes nothing about how a source is read, and a query on a `file:` or `jar:`
   source, where it can mean nothing and would send the handler looking for a file whose name ends in
   `?v=2`, is refused with a message saying that options go in the fragment.
 * [#195](https://github.com/matteobaccan/owner/issues/195): imported `Map` entries whose key or value is not a
   `String` are now rejected with an `IllegalArgumentException` naming the offending key, instead of being accepted
   and then silently misbehaving. Originally reported and fixed by Stefán Freyr Stefánsson in
   [#197](https://github.com/matteobaccan/owner/pull/197), extended here to cover keys as well as values.

   Imports are merged into a `java.util.Properties`, whose contract only admits `String` keys and values, but
   which extends `Hashtable<Object, Object>` and therefore accepts anything through `putAll`. The entry then
   became invisible to `getProperty`, in two different ways:

   ```java
   public interface MyConfig extends Config {
       @Key("some.key")
       @DefaultValue("1")
       Integer someValue();
   }
   ```

   | Import | Up to 1.0.12 | Since 2.0.0 |
   |---|---|---|
   | `imports.put("some.key", 42)` | `someValue()` returns `null`, *shadowing* `@DefaultValue("1")` | `IllegalArgumentException` at `create()` |
   | `imports.put(42, "42")` | the entry is dropped, `someValue()` returns the default `1` | `IllegalArgumentException` at `create()` |
   | `imports.put("some.key", new StringBuilder("42"))` | `someValue()` returns `null` | `IllegalArgumentException` at `create()` |
   | `imports.put("some.key", "42")` | `someValue()` returns `42` | unchanged, returns `42` |

   **Compatibility.** Code that imported only `String` keys and values is unaffected. Code that imported anything
   else was already getting a wrong value, or none, so no working behaviour is lost — but a heterogeneous `Map`
   that happened to be read only through its `String` entries will now fail fast at `create()` time rather than
   half-working. Note that a `CharSequence` is not sufficient, as `Properties` compares against `String`
   specifically: call `toString()` on `StringBuilder`/`StringBuffer` values before importing them. See the
   [documentation](/owner/docs/importing-properties/).

   The validation lives in the factory, so it applies uniformly to `ConfigFactory.create()`, to a `Factory`
   obtained from `ConfigFactory.newInstance()`, and to `ConfigCache.getOrCreate()`; previously the equivalent
   check on null keys and values only covered the first of the three.
 * Fixed the conversion of arrays and collections when a single element cannot be converted. The converter is
   chosen once from the first element, so the remaining ones could still fail: their internal "skip" marker
   ended up being stored into the resulting array, surfacing as an
   `IllegalArgumentException: array element type mismatch` instead of the documented
   [`UnsupportedOperationException`](https://docs.oracle.com/javase/8/docs/api/java/lang/UnsupportedOperationException.html).
   A property like `@DefaultValue("1, 2, foo, 4")` mapped to a custom type now reports
   `Cannot convert 'foo' to MyType`, consistently with what already happened for a non-array property. For the
   same reason, a `@ConverterClass` returning `null` for an element now yields a `null` element instead of
   failing the whole conversion.
 * Conversion errors now name the property they come from: `Cannot convert 'abc' to int` became
   `Cannot convert 'abc' to int for property 'server.port'`. The message used to say what could not be converted
   but not where to go and fix it, which in a file with fifty properties left the search to be done by hand. The
   key named is the one the property is read with, `@Key` and `@Prefix` included
   ([#191](https://github.com/matteobaccan/owner/issues/191)). When the value comes from a group of
   properties read as a `Map`, the key named is the individual entry — `group.second`, not `group`.
 * `ByteSizeUnit.parse` no longer depends on the default locale of the JVM. It lowercased the text without
   saying in which language, and in Turkish a capital `I` lowercases to the dotless `ı`: `512 KIB` was
   therefore rejected as an invalid unit on a Turkish JVM and accepted everywhere else. Every IEC unit
   written in capitals was affected, since all of them carry an `i`.
 * `ByteSize` honours the `equals`/`hashCode` contract. `equals` compares the number of bytes, so `1 MB` and
   `1000000 B` are equal, while the hash code was derived from the value and the unit as they were written:
   the two were equal with different hash codes, which made the type unusable as a key of a `HashMap` or as
   an element of a `HashSet` — a set could hold the same size twice, and a lookup could miss. The class is
   now `final`, and both parts are rejected at construction when null, instead of failing later at the first
   arithmetic with no indication of where the missing part was written.
 * Fixed a `NullPointerException` masking the real error in the hot reload example when the configuration URI is
   invalid.
 * Test suite stability fixes (thread handling in multi-threading tests, wait times).

Downloadable artifacts are published on
[GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-2.0.0) and on
[Maven Central Repository](https://central.sonatype.com/artifact/org.aeonbits.owner/owner/2.0.0).

## 1.0.12

*Released 7 June 2020*

I just released version 1.0.12, it contains all the bug fixes included in 1.0.11 plus a fix to a 
multi threading issue that appeared in 1.0.11.

--Luigi.
     
  
### RELEASE NOTES

OWNER v1.0.12 contains following enhancements and bug fixes.

#### Enhancements
 * None
 
#### Site Enhancements
 * None
 
#### Bugs fixes
 * Fixed [#268](https://github.com/matteobaccan/owner/issues/268): Calling a value is not thread safe (return another value)
 * Fixed [#266](https://github.com/matteobaccan/owner/issues/266): PropertyEditor - Concurrency Issues

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-1.0.12) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.12/).

:::note[The 1.0.11 was not announced, in fact it introduced a bug in multi threading.]
If you are using 1.0.11, please update to 1.0.12 asap, so that you should have the bug fixed.
:::

OWNER v1.0.11 contains following enhancements and bug fixes. 

#### Enhancements
 * [#234](https://github.com/matteobaccan/owner/pull/234): Allowing to format Key value by method arguments as 
   with DefaultValue.
 * [#255](https://github.com/matteobaccan/owner/pull/255): Solves the thread contention problem reported on Issue #254; 
   Note this has partially been rolled back in 1.0.12 due to bugs [#268](https://github.com/matteobaccan/owner/issues/268) 
   and [#266](https://github.com/matteobaccan/owner/issues/266).
 * [64a7c07](https://github.com/matteobaccan/owner/commit/64a7c07bd79287b1d9debacfe60ad6e4e597cc39): 
   Updated dependencies to work with Java 11 LTS.
 
#### Site Enhancements
 * [#247](https://github.com/matteobaccan/owner/pull/247): Documentation for system:properties and system:env.
 * Fixed [Sonar](https://sonarcloud.io/project/overview?id=matteobaccan_owner) and Travis.
 * [#274](https://github.com/matteobaccan/owner/pull/247): 
   Documentation for system:properties and system:env, Update importing-properties.md. 
 * [#246](https://github.com/matteobaccan/owner/pull/246): Fixed doc typos & errors and improved readability.
 * [#242](https://github.com/matteobaccan/owner/issues/242): FAQ broken link.
 * [#224](https://github.com/matteobaccan/owner/pull/224): Adding some documentation for bug 
   [#184](https://github.com/matteobaccan/owner/issues/184) (Maps with null values cause an unclear exception). 
 * Fixed Javadocs.
 * Updated documentation.
 
#### Bugs fixes
 * [2479d47](https://github.com/matteobaccan/owner/commit/2479d4718c5996e432f6cc0dedcbb4f250b29c43): decryption not working when used in combination with variable substitution
 * [0b2d209](https://github.com/matteobaccan/owner/commit/0b2d209b0fe661a1596aa55921fff16e2ba5bc92): removed [double check locking] anti pattern.
 * [#227](https://github.com/matteobaccan/owner/pull/227): Fixes properties issue in loading file URLs.
 * [#239](https://github.com/matteobaccan/owner/pull/239): Allow property values to contain a '%' character without being a format string.
 * [#203](https://github.com/matteobaccan/owner/issues/203), [#241](https://github.com/matteobaccan/owner/pull/241): 
   ConcurrentModificationException on creating Config.
 * [#226](https://github.com/matteobaccan/owner/issues/226), [#227](https://github.com/matteobaccan/owner/pull/227):
   Empty system variables for file paths in @Sources cause URISyntaxException failures.
   Fixes properties issue in loading file URLs.

## 1.0.10

*Released 1 March 2018*

After long time (more than 2 years now), and many people asking for a new release, here we are.
And here my apologies for the delay.

As you may know, I had serious health problems that kept me away from coding. Now my health is getting better, but I
feel much slower in coding and using awesome tools like IntelliJ IDEA; in the meantime, my open source license has
expired, so I hope the guys from JetBrains will be so nice to renew it :).

Also, I always found the maven release process being cumbersome so that also kept me away from the effort.
Now I took some time to simply it a little bit, and I kept some note for the future.

In this release, a huge amount of work has been conducted by contributors, and I mostly did housekeeping with
refactoring, code review, enforcing quality standards, asking for documentation and tests, and integrating the great
ideas coming from the users' community.

I took back the project recently to upgrade it to have Java 9 support, and simplify release deployment, and only now
that I am writing this release note, I realize how many things have been added and was waiting to be released.

Documentation is very important; I hadn't had the chance to keep all in sync, so many things here need to be
documented. If you think you can help, feel free to help: this website is a sub-project
[`owner-site`](https://github.com/matteobaccan/owner/tree/master/owner-site), and uses Markdown language, which is very
handy and quick to learn; the structure is quite easy to follow.
[Jekyll](https://jekyllrb.com/) is used as site generator, which is written in Ruby and can be tricky for a Java dev
like me, but it works awesomely with github. So feel free to help there too.
There is also an [ant script](https://github.com/matteobaccan/owner/blob/master/owner-site/build.xml) which allows
you to launch Jekyll and live-preview the end result of your edits.

I don't feel very comfortable in making promises, but I'd really like to give back life to this project and, for the
future, avoid such a long wait for a release.

Please notice that at the moment I am not professionally working, I closed my consultancy company years back, and
in this moment I am writing from a nice [Coworking Space "ImpactHub" here in Torino](https://torino.impacthub.net/).
So, let me quickly say that [donations are very welcome](https://github.com/matteobaccan/owner/#donations).
Or if you want, you can hire me for some custom development on OWNER, training, or to help implementing your
projects.
This would definitely help keeping OWNER alive.

Credits to ALL the contributors of OWNER, and to the end-users of this neat library.
To you all it goes my gratitude for this release.

**Thank you!**

--Luigi.

***

### RELEASE NOTES

OWNER v1.0.10 contains following enhancements and bug fixes.

#### Enhancements
 * Added Java 9 support, dropped Java 6 support. All code and tests are running and built with Java 9, so you can use
   OWNER with the latest Java version. It was not trivial. If you want to use some specific feature like default
   methods in interfaces introduced in Java8, you still need to add `owner-java8` dependency. I know... I didn't want to
   create a new sub-module for Java 9 and every newer versions, if it's not necessary.
   Also, I updated all the dependencies (testing, and optional) and Maven plugins, in order to have it working
   with Java 9.
   A huge thank you to my friend [@sbordet](https://github.com/sbordet).
 * Added `list()` method to `ConfigCache`. ConfigCache is a great way to centralise configuration for various
   parts of an application. This commit adds a list() method to the ConfigCache class, which lists the keys for all
   configurations present in the cache. This allows the entire application configuration to be inspected (e.g. for
   debugging) without the need for storing cache keys elsewhere. Thanks
    [@kevin-canadian](https://github.com/kevin-canadian), who also was so nice to update the documentation on the
    website.
 * Added `@EncryptedValue` and `@DecryptorClass` annotations to allow hiding passwords stored in configuration
   properties. See [#49](https://github.com/matteobaccan/owner/issues/49), thanks [@rrialq](https://github.com/rrialq)
   for the implementation and the awesome documentation.
 * Added a Java 8 duration converter class: `DurationConverter.class` in `owner-java8-extras.jar` .
   Thanks [@StFS](https://github.com/StFS).
 * Added system properties and enviroment variable as sources: example `@Sources({"system:properties", "system:env"})`.
   See [#110](https://github.com/matteobaccan/owner/issues/110). Thanks [@gintau](https://github.com/gintau) for the
   implementation and [@kevin-canadian](https://github.com/kevin-canadian) for the idea.
 * Added `ByteSizeConverter` and `DurationConverter` classes in `owner-java8-extras` jar, see
   [#155](https://github.com/matteobaccan/owner/issues/155). Thanks [@StFS](https://github.com/StFS), also for providing
   the [necessary documentation](https://matteobaccan.github.io/owner/docs/type-conversion/#byte-size) and unit tests.
 * Added the ability to register default converters for types and classes defined by users.
   See [#184](https://github.com/matteobaccan/owner/issues/184).
   Thanks [@StFS](https://github.com/StFS).
 * Added inheritance support for `@Sources`, `@LoadPolicy` and `@HotReload`.
   Sources defined for all extended interfaces will be merged.
   LoadPolicy and HotReload can be inherited and override by the extended interface.
   Thanks [@chengmingwang](https://github.com/chengmingwang).

#### Bugs fixes
 * Replaced `fixBackslashForRegex` with better implementation. Thanks [@kiefinger](https://github.com/kiefinger).
 * Have `ConfigFactory` throw an exception on imported Maps having either null keys or null values.
   See [#185](https://github.com/matteobaccan/owner/pull/185), [#184](https://github.com/matteobaccan/owner/pull/184).
   Thanks [@StFS](https://github.com/StFS).
 * Accept file URI containing spaces. Updated the uri processing to allow loading files that contain spaces in
   their paths.
   See [#134](https://github.com/matteobaccan/owner/issues/134). Thanks [@icirellik](https://github.com/icirellik).
 * Maps with null values cause an unclear exception. See [#184](https://github.com/matteobaccan/owner/issues/184).
   Thanks [@StFS](https://github.com/StFS).
 * Set tar long file mode to posix in maven assembly plugin to avoid build errors.
   Thanks [@gdenning](https://github.com/gdenning).

#### Site Enhancements
 * Added [Crypto support](https://matteobaccan.github.io/owner/docs/crypto/) documentation page.
 * Added [ByteSize Converter](https://matteobaccan.github.io/owner/docs/type-conversion/#byte-size) converter and
   [Duration Converter](https://matteobaccan.github.io/owner/docs/type-conversion/#duration) documentation section.
   Thanks [@StFS](https://github.com/StFS).
 * Chinese documentation has been contributed by [@cyfonly](https://github.com/cyfonly) and is available
   [here](https://github.com/cyfonly/owner-doc). Sorry, I cannot check that everything is correct or update that! :-)
   See [#172](https://github.com/matteobaccan/owner/issues/172).
 * Added security/stability badges by [Meterian](https://www.meterian.com/).
   Thanks [@fdiotalevi](https://github.com/fdiotalevi), [@bbossola](https://github.com/bbossola)

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-1.0.10) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.10/).

## 1.0.9

*Released 22 July 2015*

v1.0.9 contains following enhancements and bug fixes.

#### Enhancements
 * Added `fill(java.util.Map)` method to the `Accessible` interface.
 * Added pre-processing feature. See [#120](https://github.com/matteobaccan/owner/issues/120), thanks
   [@a1730](https://github.com/a1730) for the feedback.

#### Site Enhancements
 * None.

#### Bugs fixes
 * Config.Sources with ~ doesn't create a valid URI on Windows.
   See [#123](https://github.com/matteobaccan/owner/issues/123), thanks [@outofrange](https://github.com/outofrange) for
   spotting this bug.

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-parent-1.0.9) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.9/).

## 1.0.8

*Released 1 April 2015*

:::note[Release 1.0.7 failed deployment in Maven Central Repository]
Some required pom was skipped, and if you try to use it as dependency in your project, it
may raise some maven error or other issues. So here the hotfix: 1.0.8 is out!
:::

v1.0.8 contains following enhancements and bug fixes.

#### Enhancements
 * Fixed the javadocs included in the tarballs/zips released.

#### Site Enhancements
 * None.

#### Bugs fixes
 * No `owner-parent` pom in Maven Central Repository. See [#121](https://github.com/matteobaccan/owner/issues/121),
 thanks [@rajatvig](https://github.com/rajatvig) for quickly spotting the issue.

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-parent-1.0.8) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.8/).

## 1.0.7

*Released 30 March 2015*

:::caution[Release 1.0.7 failed deployment in Maven Central Repository]
Some required pom was skipped, and if you try to use it as dependency in your project, it
may raise some maven error or other issues. So, avoid using 1.0.7 and jump to 1.0.8!
:::

v1.0.7 contains following enhancements and bug fixes.

#### Enhancements
 * Added JMX Support. See [#107](https://github.com/matteobaccan/owner/pull/107) and
   [#19](https://github.com/matteobaccan/owner/issues/19).
   Thanks [@robinmeiss](https://github.com/robinmeiss).
   I still need to write the documentation on how to use it (sorry).
 * Added examples module, containing some example Maven Java projects to show some of the API features.
   This gets packaged in the [released archive artifacts (zip and tarballs)](https://github.com/matteobaccan/owner/releases/tag/owner-parent-1.0.7).

#### Site Enhancements
 * None.

#### Bugs fixes
 * Fixed packaging: the `owner-extras.jar` was missing required classes.
   See [#114](https://github.com/matteobaccan/owner/issues/114). Thanks [@ksaritek](https://github.com/ksaritek) for the patience.

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-parent-1.0.7) and
on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.7/).

## 1.0.6

*Released 18 November 2014*

v1.0.6 contains following enhancements and bug fixes.

#### Enhancements
 * Added basic support for ZooKeeper [#81](https://github.com/matteobaccan/owner/issues/81). Thanks [@ksaritek](https://github.com/ksaritek).
 * Added Java 8 Support (default and static methods on interfaces). See [#94](https://github.com/matteobaccan/owner/issues/94).
 * Added OSGi support. See [#101](https://github.com/matteobaccan/owner/issues/101).

#### Site Enhancements
 * Fixed documentation errors. See [#88](https://github.com/matteobaccan/owner/issues/88), [#89](https://github.com/matteobaccan/owner/issues/89), [#92](https://github.com/matteobaccan/owner/issues/92). Thanks [@hemus2121](https://github.com/hemus2121).
 * Minor changes in build.xml (ant publishing script to gh-pages)

#### Bugs fixes
 * Use of default value for for properties using the Key Expansion mechanism [#84]( https://github.com/matteobaccan/owner/pull/84).

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-1.0.6) and on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner-assembly/1.0.6/).

## 1.0.5.1

*Released 28 May 2014*

v1.0.5.1 contains following enhancements and bug fixes.

#### Enhancements
 * Java8 fixes, so now it is officially supported.
 * Added UTF-8 Support for properties files. (See [#77](https://github.com/matteobaccan/owner/issues/77) and
   [#78](https://github.com/matteobaccan/owner/issues/78), thanks [@SvetaNesterenko](https://github.com/SvetaNesterenko) )
 * Added [ConfigCache (Singleton)](/owner/docs/singleton/) feature. (See [#64](https://github.com/matteobaccan/owner/issues/64))
 * Improved support for Android. Somebody wants to verify/help with this? (See [#75](https://github.com/matteobaccan/owner/issues/75))
 * Implemented variable expansion for `@Key` annotation. (See [#63](https://github.com/matteobaccan/owner/issues/63))
 * Restructured maven project to allow sub-modules.

#### Site Enhancements
 * Documentation website minor style/layout, updates and improvements.
 * Added [SlideShare presentation](https://www.slideshare.net/LuigiViggiano/owner-31716769) in home page.

#### Bugs fixes
 * Code cleanup, removed warnings.
 * Fixed compatibility issue on exception raised by Java7 and Java6. (See [#71](https://github.com/matteobaccan/owner/issues/71))

Downloadable artifacts are published on [GitHub](https://github.com/matteobaccan/owner/releases/tag/owner-1.0.5.1) and on [Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner/1.0.5.1/).

## 1.0.5

*Released 9 October 2013*

v1.0.5 contains following enhancements and bug fixes.

#### Enhancements

 * [Support for XML](/owner/docs/xml-support/).
   OWNER is now able to load not only from properties files, but also from XML files. The XML
   can follow the [Java XML Properties format](http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html),
   or can be freely defined by the user.<br/>
   (See more in the documentation: [XML support](/owner/docs/xml-support/) and see
   [#5](https://github.com/matteobaccan/owner/issues/5)).
 * Added method `registerLoader()` to `ConfigFactory`, so the user can define new loaders for more file formats.<br/>
   (See [#55](https://github.com/matteobaccan/owner/issues/55)).
 * Support for `classpath:` URLs in HotReload. Also it works with the default files associated to the mapping
   interface, when `@Sources` is not specified.
 * Added method `Set<String> propertyNames()` in the `Accessible` interface.<br/>
   (See [#46](https://github.com/matteobaccan/owner/issues/46)).
 * Added [Event support](/owner/docs/event-support/) for property changes and reload.
   Both the events can now be transactional: the listener can be notified by an event before and after a property change
   or a reload takes place. The listener can check what is changed and eventually rollback the reload or property change
   operation.<br/>
   (See more in the documentation: [Event support](/owner/docs/event-support/) and see
   [#47](https://github.com/matteobaccan/owner/issues/47)).
 * Added non-static `ConfigFactory`, so one can create independent instances of OWNER `Factory` objects.<br/>
   (See [#43](https://github.com/matteobaccan/owner/issues/43)).
 * Added implementation on `hashCode()` and `equals()`.
 * Added serialization capability to OWNER `Config` objects, so now they can be transferred through the network or
   transformed to byte streams. <br/>
   (See [#54](https://github.com/matteobaccan/owner/issues/54)).
 * Allow `@ConverterClass` annotation to override default converters (i.e. primitive types, etc).
 * The interfaces `Reloadable`, `Mutable` and `Accessible` now extend from `Config`, so you don't need anymore to extend
   directly from Config. For instance, your interface can now extend just from Mutable to generate an object which is
   also a valid `Config` object that can be instantiated by the `ConfigFactory`:<br/><br/>
   ![config-hierarchy](/owner/img/config-hierarchy.png)

#### Site Enhancements
 * Website sources reorganized: moved from `gh-pages` branch to `master`, with publish ant scripts `build.xml`.
 * Added news section, with release announcements and blog posts.

#### Bugs fixes

 * Fixed bugs on tests that were making the build failing on Windows systems.
 * Fixed bug [#51](https://github.com/matteobaccan/owner/pull/51), variables expansion, and path expansion not working
   properly with string containing the backslash characters `'\'`. <br/>
   Thanks [NiXXeD](https://github.com/NiXXeD).
 * Fixed bug [#42](https://github.com/matteobaccan/owner/issues/42), regarding the incompatibility of the OWNER
   library with the Google App Engine security restrictions.

Downloadable artifacts are published on
[Maven Central Repository](http://repo1.maven.org/maven2/org/aeonbits/owner/owner/1.0.5/).

## 1.0.4.1

*Released 19 September 2013*

v1.0.4.1 is a bug fix release for v1.0.4 branch.

#### Bugs fixes

 * Fixed some multi-threaded tests that were failing sometimes randomly during continuous integration.
 * Fixed bug [#50](https://github.com/matteobaccan/owner/issues/50), regarding hot reload not working
   when file name needs to be expanded.

## 1.0.4

*Released 11 July 2013*

v1.0.4 contains some key enhancements and bug fixes.

#### Enhancements

 * New `@ConverterClass` annotation.
   See [The @ConverterClass annotation](/owner/docs/type-conversion/#the-converterclass-annotation), [#38][issue-38].
 * Hot reload for file based sources.
   See [Automatic "hot reload"](/owner/docs/reload/#automatic-hot-reload), [#15][issue-15].
 * toString() method can be invoked on the Config object to get some useful text for debugging.
   See [The toString() method](/owner/docs/debugging/#the-tostring-method), [#33][issue-33].
 * Added [`Mutable`][mutable-intf] interface for the methods giving *write* access to the underlying properties structure:
   setProperty, removeProperty, clear.
   See [The Mutable interface](/owner/docs/accessible-mutable/#the-mutable-interface), [#31][issue-31].
 * Added [`Accessible`][accessible-intf] interface for the `list()` methods used to aid debugging, and other methods
   giving read access to the underlying properties structure.
   See [The Accessible interface](/owner/docs/accessible-mutable/#the-accessible-interface).
 * Added the `reload()` method that can be exposed implementing the interface [`Reloadable`][reloadable-intf].
   See [Programmatic reload](/owner/docs/reload/#programmatic-reload).
 * Fist class Java Arrays and Collections support in type conversion. Thanks [ffbit][].
   See [Arrays and Collections](/owner/docs/type-conversion/#arrays-and-collections), [#21][issue-21], [#22][issue-22] and [#24][issue-24].
 * Implemented `@DisableFeature` annotation to provide the possibility to disable variable expansion and parametrized
   formatting.
   See [Disabling Features](/owner/docs/disabling-features/), [#20][issue-20].

#### Site Enhancements

 * New website for documentation.
 * Added Sonar to keep high attention on code quality.
 * Added [Travis CI][travis-ci] to the project to track changes and run tests on different JDK versions.
 * Website code snippets now have syntax highlighting. Thanks [ming13][].

#### Bugs fixes

 * Fixed bug [#40][issue-40] about tilde expansion.
 * Fixed bug [#17][issue-17] Substitution and format not working as expected when used together.

## 1.0.3.1

*Released 26 June 2013*

v1.0.3.1 contains some key enhancements and bug fixes:

 * Fixed bug [#35](https://github.com/matteobaccan/owner/issues/35)

## 1.0.3

*Released 3 February 2013*

v1.0.3 contains some key enhancements and bug fixes:

 * Fixed incompatibility with JRE 6 (project was compiled using JDK 7 and in some places I was catching
   ReflectiveOperationException that has been introduced in JDK 7).
 * Minor code cleanup/optimization.

See [what's new][intr] and [what's new part 2][intr-2] articles for more information on this release.

## 1.0.2

*Released 27 January 2013*

v1.0.2 contains some key enhancements and bug fixes:

 * Changed package name from `owner` to `org.aeonbits.owner`.
   Sorry to break backward compatibility, but this has been necessary in order to publish the artifact on Maven Central
   Repository.
 * Custom & special return types.
 * Properties variables expansion.
 * Added possibility to specify [Properties][properties] to import with the method `ConfigFactory.create()`.
 * Added list() methods to aide debugging. User can specify these methods in his properties mapping interfaces.
 * Improved the documentation (this big file that you are reading), and Javadocs.

See [what's new][intr] and [what's new part 2][intr-2] articles (most of them applies to 1.0.3 and 1.0.2 as well) for
more information on this release.

## 1.0.1

*Released 27 December 2012*

v1.0.1 contains some key enhancements and bug fixes:

 * Removed [commons-lang][] transitive dependency. Minor bug fixes.

## 1.0.0

*Released 24 December 2012*

v1.0.0 contains following key features:

  - Mapping between Java interfaces and properties files.
  - `@DefaultValue` and `@Key` annotations.
  - `@Sources` annotation for loading properties from specified urls.

See article [Introducing OWNER, a tiny framework for Java Properties files.][introducing]

## Other announcements

### Telegram Chat for Users and Devs

*2 March 2018*

:::caution[This chat no longer exists]
The Telegram group announced below has since been closed. The post is kept as it was
written, but the link goes nowhere. Questions are now asked in
[Discussions](https://github.com/matteobaccan/owner/discussions), and bugs and feature
requests in [Issues](https://github.com/matteobaccan/owner/issues).
:::

Hi All!

Just a quick post to announce that a Telegram Chat is available for quick Q&A.

If you don't already know [Telegram](https://www.telegram.org), you should check it out right now!

It's possibly the fastest way you can get in touch with devs and other users and have your questions answered lightning fast.

So, join [OWNER API Users and Devs](https://t.me/ownerapi) Telegram Chat and let's keep in touch!

--Luigi

[issue-21]: https://github.com/matteobaccan/owner/issues/21
[issue-22]: https://github.com/matteobaccan/owner/issues/22
[issue-24]: https://github.com/matteobaccan/owner/issues/24
[issue-40]: https://github.com/matteobaccan/owner/issues/40
[issue-38]: https://github.com/matteobaccan/owner/issues/38
[issue-33]: https://github.com/matteobaccan/owner/issues/33
[issue-17]: https://github.com/matteobaccan/owner/issues/17
[issue-20]: https://github.com/matteobaccan/owner/issues/20
[issue-31]: https://github.com/matteobaccan/owner/issues/31
[issue-15]: https://github.com/matteobaccan/owner/issues/15
[ffbit]: https://github.com/ffbit
[ming13]: https://github.com/ming13
[accessible-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Accessible.html
[reloadable-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Reloadable.html
[mutable-intf]: https://matteobaccan.github.io/owner/apidocs/latest/org/aeonbits/owner/Mutable.html
[travis-ci]: https://sonarcloud.io/project/overview?id=matteobaccan_owner
[intr]: http://en.newinstance.it/2013/02/04/owner-1-0-3-whats-new-part-1-variable-expansion/
[intr-2]: http://en.newinstance.it/2013/05/29/owner-1-0-3-whats-new-part-2/
[properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html
[commons-lang]: http://commons.apache.org/lang/
[introducing]: http://en.newinstance.it/2012/12/27/introducing-owner-a-tiny-framework-for-java-properties-files/
