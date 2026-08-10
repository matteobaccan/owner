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
